package org.tinymediamanager.scraper.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.thirdparty.FFmpeg;
import org.tinymediamanager.thirdparty.FFprobe;

/**
 * Standalone Java port of GOLANG videophash. (there are subtle differences in generating the scaled down image for DCT calculation.
 *
 * It replicates the Go behavior and parameters: - 25 screenshots (5x5 grid) - offset at 5% of duration, step over 90% duration - montage sprite ->
 * 64-bit perceptual hash from top-left 8x8 DCT block using median threshold
 */
public final class VideoPHash {
  private static final Logger LOGGER  = LoggerFactory.getLogger(VideoPHash.class);
  private static final int    COLUMNS = 5;
  private static final int    ROWS    = 5;

  private VideoPHash() {
  }

  public static String generate(Path videoFile) throws IOException, InterruptedException {
    LOGGER.trace("Calculating PHASH for {}", videoFile);
    if (!FFprobe.isAvailable()) {
      LOGGER.warn("Would have executed perceptual hash generation - unfortunately, FFprobe could not be found.");
      MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, "task.phash", "message.ard.ffmpegmissing"));
      return "";
    }
    double durationSeconds = FFprobe.detectDurationSeconds(videoFile);
    LOGGER.trace("Got duration [{}] for {}", durationSeconds, videoFile);
    if (durationSeconds <= 0) {
      LOGGER.warn("Duration from FFProbe invalid.");
      return "";
    }
    if (durationSeconds <= 25) {
      LOGGER.warn("Duration is too low to produce valid results"); // 5x5 images, every sec... meh.
      return "";
    }
    // System.out.println(durationSeconds);
    BufferedImage sprite = generateSprite(videoFile, durationSeconds);
    // debugDumpImage("java_sprite.bmp", sprite);
    Long hash = perceptionHash64(sprite);
    String phash = Long.toHexString(hash);
    LOGGER.trace("Got PHASH [{}] for {}", phash, videoFile);
    return phash;
  }

  private static BufferedImage generateSprite(Path videoPath, double durationSeconds) throws IOException, InterruptedException {
    int chunkCount = COLUMNS * ROWS;
    double offset = 0.05d * durationSeconds;
    double stepSize = (0.9d * durationSeconds) / chunkCount;

    List<BufferedImage> images = new ArrayList<>(chunkCount);
    for (int i = 0; i < chunkCount; i++) {
      double t = offset + (i * stepSize);
      BufferedImage img = FFmpeg.generateScreenshotForPHash(videoPath, t);
      // debugDumpImage(String.format("java_frame_%02d.bmp", i), img);
      images.add(img);
    }

    if (images.isEmpty()) {
      throw new IOException("images slice is empty, failed to generate phash sprite for " + videoPath);
    }

    return combineImages(images);
  }

  /**
   * build a 5x5 combined image
   * 
   * @param images
   * @return
   */
  private static BufferedImage combineImages(List<BufferedImage> images) {
    int width = images.get(0).getWidth();
    int height = images.get(0).getHeight();
    int canvasWidth = width * COLUMNS;
    int canvasHeight = height * ROWS;

    BufferedImage montage = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = montage.createGraphics();
    try {
      for (int index = 0; index < images.size(); index++) {
        int x = width * (index % COLUMNS);
        // Matches the Go implementation exactly (division by ROWS).
        int y = height * (index / ROWS);
        g.drawImage(images.get(index), x, y, null);
      }
    }
    finally {
      g.dispose();
    }
    return montage;
  }

  /**
   * calculate the pHash
   * 
   * @param image
   * @return
   */
  private static long perceptionHash64(BufferedImage image) {
    BufferedImage resized = resizeBilinear(image, 64, 64);
    // debugDumpImage("java_resized_64.bmp", resized);
    double[][] gray = toGray(resized);
    double[][] dct = dct2D(gray);

    double[] lowFreq = new double[64];
    int idx = 0;
    for (int y = 0; y < 8; y++) {
      for (int x = 0; x < 8; x++) {
        lowFreq[idx++] = dct[y][x];
      }
    }

    double median = median64(lowFreq);
    long hash = 0L;
    for (int i = 0; i < lowFreq.length; i++) {
      if (lowFreq[i] > median) {
        hash |= (1L << (63 - i));
      }
    }
    return hash;
  }

  /**
   * Exact port of github.com/nfnt/resize Bilinear filter for *image.NRGBA input.
   *
   * Matches the separable algorithm used by goimagehash.PerceptionHash internally:<br>
   * - Pass 1: resizeNRGBA (horizontal, with alpha premultiplication, transposed temp)<br>
   * - Pass 2: resizeRGBA (vertical, no premultiplication, un-transpose)<br>
   * - Coefficients: int16(linearKernel(x) * 256), integer division for final output
   */
  private static BufferedImage resizeBilinear(BufferedImage src, int dstWidth, int dstHeight) {
    int srcWidth = src.getWidth();
    int srcHeight = src.getHeight();
    double scaleX = (double) srcWidth / dstWidth;
    double scaleY = (double) srcHeight / dstHeight;

    // --- createWeights8 for horizontal (scaleX) ---
    int hLen = 2 * (int) Math.max(Math.ceil(scaleX), 1);
    double hFactor = Math.min(1.0 / scaleX, 1.0);
    short[] hCoeffs = new short[dstWidth * hLen];
    int[] hStart = new int[dstWidth];
    for (int y = 0; y < dstWidth; y++) {
      double ix = scaleX * (y + 0.5) - 0.5;
      hStart[y] = (int) ix - hLen / 2 + 1;
      ix -= hStart[y];
      for (int i = 0; i < hLen; i++) {
        double v = Math.abs((ix - i) * hFactor);
        hCoeffs[y * hLen + i] = (short) ((v < 1.0) ? (int) ((1.0 - v) * 256) : 0);
      }
    }

    // --- createWeights8 for vertical (scaleY) ---
    int vLen = 2 * (int) Math.max(Math.ceil(scaleY), 1);
    double vFactor = Math.min(1.0 / scaleY, 1.0);
    short[] vCoeffs = new short[dstHeight * vLen];
    int[] vStart = new int[dstHeight];
    for (int y = 0; y < dstHeight; y++) {
      double ix = scaleY * (y + 0.5) - 0.5;
      vStart[y] = (int) ix - vLen / 2 + 1;
      ix -= vStart[y];
      for (int i = 0; i < vLen; i++) {
        double v = Math.abs((ix - i) * vFactor);
        vCoeffs[y * vLen + i] = (short) ((v < 1.0) ? (int) ((1.0 - v) * 256) : 0);
      }
    }

    // --- Pass 1: resizeNRGBA (horizontal, alpha-premultiplied, TRANSPOSED output) ---
    // temp layout: [dstWidth rows × srcHeight cols] × 4 channels
    // temp[row=y][col=x] = horizontally-filtered value for src-row=x, dst-col=y
    int tempStride = srcHeight * 4; // bytes per row of temp
    byte[] temp = new byte[dstWidth * tempStride];
    int maxX = srcWidth - 1;

    for (int x = 0; x < srcHeight; x++) { // iterate over source rows
      for (int y = 0; y < dstWidth; y++) { // iterate over destination columns
        int start = hStart[y];
        int ci = y * hLen;
        int sumR = 0, sumG = 0, sumB = 0, sumA = 0, sum = 0;
        for (int i = 0; i < hLen; i++) {
          short coeff = hCoeffs[ci + i];
          if (coeff != 0) {
            int xi = start + i;
            if (xi < 0)
              xi = 0;
            else if (xi >= maxX)
              xi = maxX;
            // alpha-premultiply (NRGBA -> RGBA conversion), matching resizeNRGBA
            int argb = src.getRGB(xi, x);
            int a = (argb >>> 24) & 0xFF;
            int r = ((argb >>> 16) & 0xFF) * a / 0xFF;
            int g = ((argb >>> 8) & 0xFF) * a / 0xFF;
            int b = (argb & 0xFF) * a / 0xFF;
            int c = coeff & 0xFFFF; // treat as unsigned (0..256)
            sumR += c * r;
            sumG += c * g;
            sumB += c * b;
            sumA += c * a;
            sum += c;
          }
        }
        int xo = y * tempStride + x * 4;
        temp[xo + 0] = nfntClamp8(sumR / sum);
        temp[xo + 1] = nfntClamp8(sumG / sum);
        temp[xo + 2] = nfntClamp8(sumB / sum);
        temp[xo + 3] = nfntClamp8(sumA / sum);
      }
    }

    // --- Pass 2: resizeRGBA (vertical, no premultiplication) ---
    // Reads from temp rows (each row = one destination-x column of the final image).
    // maxX for this pass = srcHeight-1 (temp column count = srcHeight)
    int maxY = srcHeight - 1;
    BufferedImage result = new BufferedImage(dstWidth, dstHeight, BufferedImage.TYPE_INT_ARGB);

    for (int x = 0; x < dstWidth; x++) { // iterate over destination columns
      int rowBase = x * tempStride; // base of row x in temp
      for (int y = 0; y < dstHeight; y++) { // iterate over destination rows
        int start = vStart[y];
        int ci = y * vLen;
        int sumR = 0, sumG = 0, sumB = 0, sumA = 0, sum = 0;
        for (int i = 0; i < vLen; i++) {
          short coeff = vCoeffs[ci + i];
          if (coeff != 0) {
            int xi = start + i;
            if (xi < 0)
              xi = 0;
            else if (xi >= maxY)
              xi = maxY;
            int base = rowBase + xi * 4;
            int r = temp[base + 0] & 0xFF;
            int g = temp[base + 1] & 0xFF;
            int b = temp[base + 2] & 0xFF;
            int a = temp[base + 3] & 0xFF;
            int c = coeff & 0xFFFF;
            sumR += c * r;
            sumG += c * g;
            sumB += c * b;
            sumA += c * a;
            sum += c;
          }
        }
        int r = nfntClamp8(sumR / sum) & 0xFF;
        int g = nfntClamp8(sumG / sum) & 0xFF;
        int b = nfntClamp8(sumB / sum) & 0xFF;
        int a = nfntClamp8(sumA / sum) & 0xFF;
        result.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
      }
    }
    return result;
  }

  /** Matches nfnt/resize clampUint8: clamps int32 to [0,255]. */
  private static byte nfntClamp8(int v) {
    if (v < 0)
      return 0;
    if (v > 255)
      return (byte) 255;
    return (byte) v;
  }

  private static double[][] toGray(BufferedImage img) {
    int w = img.getWidth();
    int h = img.getHeight();
    double[][] gray = new double[h][w];

    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        gray[y][x] = 0.299d * r + 0.587d * g + 0.114d * b;
      }
    }
    return gray;
  }

  private static double[][] dct2D(double[][] input) {
    int n = input.length;
    double[][] temp = new double[n][n];
    double[][] out = new double[n][n];

    for (int y = 0; y < n; y++) {
      temp[y] = dct1D(input[y]);
    }

    double[] column = new double[n];
    for (int x = 0; x < n; x++) {
      for (int y = 0; y < n; y++) {
        column[y] = temp[y][x];
      }
      double[] transformed = dct1D(column);
      for (int y = 0; y < n; y++) {
        out[y][x] = transformed[y];
      }
    }

    return out;
  }

  // Unnormalized DCT-II.
  private static double[] dct1D(double[] in) {
    int n = in.length;
    double[] out = new double[n];
    for (int k = 0; k < n; k++) {
      double sum = 0.0d;
      for (int i = 0; i < n; i++) {
        sum += in[i] * Math.cos((Math.PI / n) * (i + 0.5d) * k);
      }
      out[k] = sum;
    }
    return out;
  }

  private static double median64(double[] values) {
    double[] copy = Arrays.copyOf(values, values.length);
    Arrays.sort(copy);
    return (copy[31] / 2.0d) + (copy[32] / 2.0d);
  }

  /**
   * DEBUG: dumps BufferedImages to disk for visual inspection
   * 
   * @param fileName
   * @param img
   */
  @SuppressWarnings("unused")
  private static void debugDumpImage(String fileName, BufferedImage img) {
    try {
      Path outDir = Paths.get(Globals.CACHE_FOLDER);
      Files.createDirectories(outDir);
      Path outPath = outDir.resolve(fileName);
      BufferedImage toWrite = img;
      if ("bmp".equalsIgnoreCase(extensionOf(fileName))) {
        // Java BMP writers often do not support ARGB input directly.
        toWrite = toBmpCompatible(img);
      }
      boolean written = ImageIO.write(toWrite, extensionOf(fileName), outPath.toFile());
      if (!written) {
        LOGGER.warn("[videophash-java] no writer for format " + extensionOf(fileName) + " while writing " + fileName);
      }
    }
    catch (IOException e) {
      LOGGER.warn("[videophash-java] failed to dump debug image " + fileName + ": " + e.getMessage());
    }
  }

  private static String extensionOf(String fileName) {
    int idx = fileName.lastIndexOf('.');
    if (idx == -1 || idx == fileName.length() - 1) {
      return "bmp";
    }
    return fileName.substring(idx + 1);
  }

  private static BufferedImage toBmpCompatible(BufferedImage src) {
    BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
    Graphics2D g = rgb.createGraphics();
    try {
      g.drawImage(src, 0, 0, null);
    }
    finally {
      g.dispose();
    }
    return rgb;
  }

  /**
   * compute the distance between 2 hashes.<br>
   * 0 = 99-100% perfect match<br>
   * 1-2 = 95% confident to be the same<br>
   * 5+ = probably not the same vid
   * 
   * @param a
   *          pHash1
   * @param b
   *          pHash 2
   * @return calculated distance to be same
   */
  public static int hammingDistance(long a, long b) {
    return Long.bitCount(a ^ b);
  }
}

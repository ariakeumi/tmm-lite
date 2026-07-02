/*
 * Copyright 2012 - 2026 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.thirdparty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.addon.FFprobeAddon;
import org.tinymediamanager.core.Settings;

/**
 * the class {@link FFprobe} is used to access FFprobe
 * 
 * @author Myron Boyle
 */
public class FFprobe {
  private static final Logger LOGGER = LoggerFactory.getLogger(FFprobe.class);

  private FFprobe() {
    throw new IllegalAccessError();
  }

  public static boolean isAvailable() {
    FFprobeAddon FFprobeAddon = new FFprobeAddon();
    return (!Settings.getInstance().isUseInternalMediaFramework() && StringUtils.isNotBlank(Settings.getInstance().getMediaFramework())
        && Files.isExecutable(Paths.get(Settings.getInstance().getMediaFramework())) || FFprobeAddon.isAvailable());
  }

  public static double detectDurationSeconds(Path videoFile) throws IOException, InterruptedException {
    List<String> cmdList = new ArrayList<>();
    cmdList.add(getFfprobeExecutable());
    cmdList.add("-v");
    cmdList.add("error");
    cmdList.add("-show_entries");
    cmdList.add("format=duration");
    cmdList.add("-of");
    cmdList.add("default=noprint_wrappers=1:nokey=1");
    cmdList.add(videoFile.toAbsolutePath().toString());

    String out = executeCommand(cmdList);
    double duration;
    try {
      duration = Double.parseDouble(out);
    }
    catch (NumberFormatException e) {
      throw new IOException("unable to parse ffprobe duration: '" + out + "'", e);
    }
    // round to 2 decimals
    return Math.round(duration * 100.0d) / 100.0d;
  }

  private static String executeCommand(List<String> cmdline) throws IOException, InterruptedException {
    LOGGER.debug("Running command: {}", String.join(" ", cmdline));

    ProcessBuilder pb = new ProcessBuilder(cmdline.toArray(new String[0])).redirectErrorStream(true);
    final Process process = pb.start();

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      new Thread(() -> {
        try {
          IOUtils.copy(process.getInputStream(), outputStream);
        }
        catch (IOException e) {
          LOGGER.debug("could not get output from the process", e);
        }
      }).start();

      int processValue = process.waitFor();
      String response = outputStream.toString(StandardCharsets.UTF_8);
      if (processValue != 0) {
        LOGGER.warn("Error calling FFprobe - '{}'", response);
        throw new IOException("error running FFprobe - code '" + processValue + "' / message '" + response + "'");
      }
      return response;
    }
    finally {
      process.destroy();
      // Process must be destroyed before closing streams, can't use try-with-resources,
      // as resources are closing when leaving try block, before finally
      IOUtils.close(process.getErrorStream());
    }
  }

  public static String getFfprobeExecutable() throws IOException {
    FFprobeAddon FFprobeAddon = new FFprobeAddon();

    if (!Settings.getInstance().isUseInternalMediaFramework() && StringUtils.isNotBlank(Settings.getInstance().getMediaFramework())
        && Files.isExecutable(Paths.get(Settings.getInstance().getMediaFramework()))) {
      // external FFmpeg binary chosen and filled, replace with ffprobe
      // use regex for matching at end (so not replacing a potential path)
      // double replace due to win&nix
      return Settings.getInstance().getMediaFramework().replaceAll("ffmpeg$", "ffprobe").replaceAll("ffmpeg\\.exe$", "ffprobe.exe");
    }
    else if (FFprobeAddon.isAvailable()) {
      // either internal chosen or fallback from empty external
      return FFprobeAddon.getExecutablePath();
    }
    else {
      throw new IOException("FFprobe is not available");
    }
  }
}

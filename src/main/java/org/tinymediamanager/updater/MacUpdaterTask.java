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
package org.tinymediamanager.updater;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.ReleaseInfo;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.util.UrlUtil;
import org.tinymediamanager.ui.MainWindow;

/**
 * The class {@link MacUpdaterTask} performs the macOS-only "whole {@code .app} bundle" self-update.<br>
 * <br>
 * Because macOS bundles are code-signed, the getdown file-patching used on Windows/Linux would break the signature (see {@link MacAppUpdater} for the
 * rationale). Instead this task downloads the very same notarized DMG the users get from the download page, extracts the fresh signed {@code .app}
 * next to the running one, verifies it, and hands the final swap over to a tiny detached shell helper that runs once this process has exited.<br>
 * <br>
 * All steps are best-effort and non-destructive until the very last {@code mv}: any failure before the hand-off simply aborts and tells the user to
 * download manually.
 *
 * @author Manuel Laggner
 */
public class MacUpdaterTask extends TmmTask {
  private static final Logger  LOGGER          = LoggerFactory.getLogger(MacUpdaterTask.class);

  /**
   * matches the {@code TeamIdentifier=XXXX} line emitted by {@codesign -dvvv}
   */
  private static final Pattern TEAM_ID_PATTERN = Pattern.compile("TeamIdentifier=(\\S+)");

  /**
   * how long to wait (seconds) for a helper/hdiutil/codesign command to finish
   */
  private static final long    COMMAND_TIMEOUT = 300;

  private volatile boolean     downloadSuccessful;

  public MacUpdaterTask() {
    super(TmmResourceBundle.getString("task.updater.prepare"), 100, TaskType.BACKGROUND_TASK);
  }

  /**
   * @return true when the new bundle was staged and verified successfully
   */
  public boolean isDownloadSuccessful() {
    return downloadSuccessful;
  }

  @Override
  public void doInBackground() {
    downloadSuccessful = false;

    if (!MacAppUpdater.isSupported()) {
      abortWithManualDownload();
      return;
    }

    Path bundle = MacAppUpdater.getRunningAppBundle();
    Path staging = null;
    Path dmgFile = null;

    try {
      // -------------------------------------------------------------------
      // 1) figure out the target version + DMG URL (per channel / mirror)
      // -------------------------------------------------------------------
      setTaskName(TmmResourceBundle.getString("task.updater.prepare"));
      UpdateInfo info = resolveUpdate();
      if (info == null) {
        LOGGER.warn("Could not determine a macOS DMG for the pending update");
        abortWithManualDownload();
        return;
      }

      LOGGER.info("macOS bundle update: target version {} -> {}", ReleaseInfo.getHumanVersion(), info.targetHumanVersion);

      // -------------------------------------------------------------------
      // 2) download the DMG (+ its sha256 sidecar) to the update work dir
      // (NOT Utils.getTempFolder() - that is wiped during shutdown before the helper would run)
      // -------------------------------------------------------------------
      setTaskName(TmmResourceBundle.getString("task.update"));
      Path downloadDir = MacAppUpdater.getUpdateWorkDir();
      Utils.createDirectoryIfAbsent(downloadDir);
      dmgFile = downloadDir.resolve(info.dmgUrl.substring(info.dmgUrl.lastIndexOf('/') + 1));

      // we need roughly 3x the DMG size free (download + mounted copy + freshly-staged app)
      if (info.dmgSize > 0 && bundle.getParent().toFile().getUsableSpace() < info.dmgSize * 3) {
        LOGGER.error("Not enough free space next to '{}' for the staged bundle", bundle);
        abortWithManualDownload();
        return;
      }

      String expectedSha = fetchExpectedSha256(info.dmgUrl + ".sha256");
      downloadWithProgress(info.dmgUrl, dmgFile);

      String actualSha = sha256(dmgFile);
      if (expectedSha != null && !expectedSha.equalsIgnoreCase(actualSha)) {
        LOGGER.error("DMG checksum mismatch – expected '{}', got '{}'", expectedSha, actualSha);
        abortWithManualDownload();
        return;
      }
      LOGGER.debug("DMG checksum ok ({})", actualSha);

      // -------------------------------------------------------------------
      // 3) mount the DMG (outside the staging dir, so a stuck mount can never be rm -rf'd by the swap
      // helper) and ditto the fresh .app next to the running bundle
      // -------------------------------------------------------------------
      staging = MacAppUpdater.prepareStagingDirectory();
      Path mountPoint = MacAppUpdater.getUpdateWorkDir().resolve("mnt");
      Files.createDirectories(mountPoint);
      Path stagedApp = extractAppFromDmg(dmgFile, mountPoint, staging);

      // -------------------------------------------------------------------
      // 4) verify the staged bundle really is a valid, same-team signed app
      // -------------------------------------------------------------------
      if (!verifyStagedBundle(stagedApp, bundle)) {
        LOGGER.error("staged bundle failed signature verification – refusing to swap");
        abortWithManualDownload();
        return;
      }

      // -------------------------------------------------------------------
      // 5) write the detached swap helper and hand over to the restart flow
      // -------------------------------------------------------------------
      Path logFile = MacAppUpdater.getHelperLogFile();
      Utils.deleteFileSafely(logFile);

      Path scriptFile = MacAppUpdater.prepareHelperScriptFile();
      long pid = ProcessHandle.current().pid();
      String script = MacAppUpdater.buildHelperScript(pid, bundle, stagedApp, staging, logFile);

      Utils.writeStringToFile(scriptFile, script);
      if (!scriptFile.toFile().setExecutable(true)) {
        throw new IOException("could not make the swap helper executable");
      }

      ProcessBuilder pb = new ProcessBuilder("/usr/bin/nohup", "/bin/sh", scriptFile.toAbsolutePath().toString());
      pb.redirectOutput(new File("/dev/null")).redirectErrorStream(true);

      downloadSuccessful = true;

      // the DMG is no longer needed once the app has been staged next to the running bundle
      Utils.deleteFileSafely(dmgFile);

      // everything is prepared – now ask the user to restart (only in a UI environment, like the getdown updater)
      final Path stagedDir = staging;
      if (!GraphicsEnvironment.isHeadless()) {
        SwingUtilities.invokeLater(() -> {
          int decision = JOptionPane.showConfirmDialog(MainWindow.getInstance(), TmmResourceBundle.getString("tmm.updater.restart.desc"),
              TmmResourceBundle.getString("tmm.updater.restart"), JOptionPane.YES_NO_OPTION);

          if (decision == JOptionPane.YES_OPTION) {
            MainWindow.getInstance().closeTmmAndStart(pb);
          }
          else {
            // user deferred – drop the staged copy so we do not leave litter next to the bundle
            cleanupStaging(stagedDir);
          }
        });
      }
      else {
        LOGGER.info("macOS bundle update staged – restart tinyMediaManager manually to apply the update");
      }
    }
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these exceptions
      Thread.currentThread().interrupt();
      downloadSuccessful = false;
      cleanupStaging(staging);
    }
    catch (Exception e) {
      LOGGER.error("macOS bundle update failed - '{}'", e.getMessage(), e);
      downloadSuccessful = false;
      cleanupStaging(staging);
      abortWithManualDownload();
    }
    finally {
      // best-effort: never leave the (potentially large) DMG behind if something failed before staging succeeded
      if (dmgFile != null) {
        Utils.deleteFileSafely(dmgFile);
      }
    }
  }

  /**
   * Walk all known update URLs, read their remote {@code version} file (which carries the target human version) and pick the first one for which a
   * macOS DMG actually exists on the server.
   *
   * @return the resolved {@link UpdateInfo} or {@code null} when no mirror had a matching DMG
   * @throws InterruptedException
   *           when the thread was interrupted during a network round-trip
   */
  private UpdateInfo resolveUpdate() throws InterruptedException {
    List<String> urls = UpdateCheck.parseUpdateUrls();
    for (String uu : urls) {
      if (!uu.endsWith("/")) {
        uu += '/';
      }
      try {
        String versionFile = UrlUtil.getStringFromUrl(uu + "version");

        if (versionFile == null || versionFile.isBlank()) {
          continue;
        }

        Properties p = new Properties();
        p.load(new java.io.StringReader(versionFile));
        String targetHumanVersion = p.getProperty("human.version");

        if (targetHumanVersion == null || targetHumanVersion.isBlank()) {
          continue;
        }

        String dmgUrl = MacAppUpdater.deriveDmgUrl(uu, targetHumanVersion);
        Long dmgSize = probeDmgSize(dmgUrl);

        if (dmgSize != null) {
          return new UpdateInfo(targetHumanVersion, dmgUrl, dmgSize);
        }

        LOGGER.debug("no DMG found at {} – trying next mirror", dmgUrl);
      }
      catch (InterruptedException e) {
        throw e;
      }
      catch (Exception e) {
        LOGGER.debug("update URL '{}' not usable - '{}'", uu, e.getMessage());
      }
    }
    return null;
  }

  /**
   * One cheap HEAD request per candidate DMG: returns its content length (possibly -1 / 0 when unknown), or {@code null} when the resource does not
   * exist / is not reachable. A non-2xx status makes {@link Url#getInputStream(boolean)} throw, so reaching the return below means the resource is
   * there – we do not depend on the server sending a Content-Length header for HEAD.
   */
  private Long probeDmgSize(String urlAsString) {
    try {
      Url url = new Url(urlAsString);
      try (InputStream is = url.getInputStream(true)) {
        return is != null ? url.getContentLength() : null;
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.debug("HEAD '{}' failed - '{}'", urlAsString, e.getMessage());
    }

    return null;
  }

  /**
   * Fetch the raw hex digest from the {@code .dmg.sha256} sidecar (first whitespace-delimited token).
   *
   * @return the expected digest, or {@code null} if the sidecar was not reachable (we then skip verification rather than blocking the update – HTTPS
   *         already guarantees origin authenticity)
   */
  private String fetchExpectedSha256(String shaUrl) {
    try {
      String content = UrlUtil.getStringFromUrl(shaUrl);

      if (content == null || content.isBlank()) {
        return null;
      }

      return content.strip().split("\\s+")[0];
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.warn("Could not fetch '{}', skipping checksum verification - '{}'", shaUrl, e.getMessage());
    }

    return null;
  }

  /**
   * Download a file via the standard {@link Url} client and report progress back to the task UI.
   */
  private void downloadWithProgress(String urlAsString, Path target) throws IOException, InterruptedException {
    Url url = new Url(urlAsString);
    try (InputStream in = url.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
      if (in == null) {
        throw new IOException("could not open stream to " + urlAsString);
      }
      // content length is only known once the response headers arrived
      final long total = url.getContentLength();
      if (total == 0) {
        setWorkUnits(0);
      }

      byte[] buffer = new byte[64 * 1024];
      long done = 0;
      int read;

      while ((read = in.read(buffer)) != -1) {
        if (cancel) {
          throw new InterruptedIOException("download aborted");
        }
        out.write(buffer, 0, read);
        done += read;
        if (total > 0) {
          publishState((int) Math.min(100, done * 100 / total));
        }
      }
      out.flush();
    }

    publishState(100);
  }

  /**
   * Mount the DMG read-only, find the enclosed {@code *.app}, {@code ditto} it into the staging folder and unmount again.
   *
   * @return the staged {@code .app} path
   */
  private Path extractAppFromDmg(Path dmgFile, Path mountPoint, Path staging) throws IOException, InterruptedException {
    List<String> attach = List.of("/usr/bin/hdiutil", "attach", "-nobrowse", "-readonly", "-mountpoint", mountPoint.toString(), dmgFile.toString());
    if (run(attach, null) != 0) {
      throw new IOException("hdiutil attach failed for " + dmgFile.getFileName());
    }
    try {
      Path sourceApp = findAppBundle(mountPoint);

      if (sourceApp == null) {
        throw new IOException("no .app bundle found inside the DMG");
      }

      Path target = staging.resolve(sourceApp.getFileName().toString());
      // ditto preserves symlinks + metadata, which is essential for the bundled JRE and the code signature
      List<String> copy = List.of("/usr/bin/ditto", sourceApp.toString(), target.toString());

      if (run(copy, null) != 0) {
        throw new IOException("ditto of " + sourceApp.getFileName() + " failed");
      }

      return target;
    }
    finally {
      // detach is best-effort; if it sticks, the mount lives in the work dir (never rm -rf'd by the helper)
      // and macOS tears it down on logout - we only log
      if (run(List.of("/usr/bin/hdiutil", "detach", "-quiet", mountPoint.toString()), null) != 0) {
        LOGGER.warn("could not detach the mounted DMG at '{}'", mountPoint);
      }
    }
  }

  /**
   * Locate the first {@code *.app} at the root of the mounted DMG volume.
   */
  private Path findAppBundle(Path mountPoint) throws IOException {
    try (Stream<Path> entries = Files.list(mountPoint)) {
      return entries.filter(p -> p.getFileName().toString().endsWith(".app") && Files.isDirectory(p)).findFirst().orElse(null);
    }
  }

  /**
   * Verify the staged bundle's code signature and confirm it is signed by the same Developer ID team as the currently running bundle – otherwise the
   * swap would not be TCC-permitted and we must not do it.
   */
  private boolean verifyStagedBundle(Path stagedApp, Path runningBundle) throws InterruptedException {
    if (run(List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", stagedApp.toString()), null) != 0) {
      LOGGER.error("codesign --verify failed on the staged bundle");
      return false;
    }

    String runningTeam = readTeamIdentifier(runningBundle);
    String stagedTeam = readTeamIdentifier(stagedApp);
    if (runningTeam == null || stagedTeam == null) {
      LOGGER.error("could not read the TeamIdentifier of running/staged bundle (running='{}', staged='{}')", runningTeam, stagedTeam);
      return false;
    }

    if (!runningTeam.equalsIgnoreCase(stagedTeam)) {
      LOGGER.error("TeamIdentifier mismatch (running='{}', staged='{}') – refusing to swap", runningTeam, stagedTeam);
      return false;
    }

    return true;
  }

  /**
   * Read the {@code TeamIdentifier} of a signed bundle via {@code codesign -dvvv} (the info goes to stderr).
   */
  private String readTeamIdentifier(Path app) {
    List<String> lines = new ArrayList<>();
    try {
      run(List.of("/usr/bin/codesign", "-dvvv", app.toString()), lines);
    }
    catch (Exception e) {
      LOGGER.debug("codesign -dvvv '{}' failed - '{}'", app, e.getMessage());
      return null;
    }

    for (String line : lines) {
      Matcher m = TEAM_ID_PATTERN.matcher(line);
      if (m.find()) {
        return m.group(1);
      }
    }

    return null;
  }

  /**
   * Run an external command, optionally capture its combined output, and return its exit code (or -1 on timeout).
   *
   * @param command
   *          argv
   * @param outputSink
   *          when non-null, each stdout/stderr line is appended here
   * @return the process exit code, or {@code -1} on timeout/interrupt/IO error
   */
  private int run(List<String> command, List<String> outputSink) throws InterruptedException {
    LOGGER.trace("running: {}", command);
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (outputSink != null) {
            outputSink.add(line);
          }
          LOGGER.trace("  > {}", line);
        }
      }

      if (!process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        LOGGER.warn("command timed out after {}s: {}", COMMAND_TIMEOUT, command);
        return -1;
      }

      return process.exitValue();
    }
    catch (IOException e) {
      LOGGER.warn("command '{}' failed - '{}'", command, e.getMessage());
      return -1;
    }
  }

  /**
   * Compute the lowercase hex SHA-256 digest of a file.
   */
  private String sha256(Path file) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (InputStream is = Files.newInputStream(file)) {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
          md.update(buffer, 0, read);
        }
      }

      StringBuilder sb = new StringBuilder();
      for (byte b : md.digest()) {
        sb.append(String.format("%02x", b));
      }

      return sb.toString();
    }
    catch (Exception e) {
      throw new IOException("could not compute checksum of " + file, e);
    }
  }

  /**
   * Fallback when the in-place update is not possible at all: push a message so the user knows to grab the new version from the download page
   * (mirrors the "else browse" branch used elsewhere).
   */
  private void abortWithManualDownload() {
    downloadSuccessful = false;
    MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, "Updater", "tmm.updater.failed"));
  }

  private void cleanupStaging(Path staging) {
    if (staging != null && Files.exists(staging)) {
      Utils.deleteDirectorySafely(staging);
    }
  }

  /**
   * immutable holder for the resolved update target
   */
  private record UpdateInfo(String targetHumanVersion, String dmgUrl, long dmgSize) {
  }
}

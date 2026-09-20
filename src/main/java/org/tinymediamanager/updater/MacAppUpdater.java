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

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.TinyMediaManager;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Utils;

/**
 * The class {@link MacAppUpdater} bundles all macOS-specific checks and helpers needed for the in-place {@code .app} bundle update flow.<br>
 * <br>
 * macOS apps are distributed as signed (and notarized) {@code .app} bundles. Any modification of a file <em>inside</em> the bundle invalidates the
 * Developer ID code signature and therefore the running app cannot be patched file-by-file like our getdown-based updater on Windows/Linux does.<br>
 * Instead we replace the <b>whole</b> bundle atomically: download a fresh, signed DMG, mount it, {@code ditto} the contained {@code .app} next to the
 * current bundle, and then hand off to a small detached shell script that performs the swap once our process has terminated.<br>
 * <br>
 * Why this works on macOS:
 * <ul>
 * <li>a running process keeps executing from the old inodes after its bundle directory is renamed/moved (POSIX semantics), so the swap does not
 * require the process to release anything,</li>
 * <li>HTTP downloads made from within the app do <em>not</em> set the {@code com.apple.quarantine} xattr, so Gatekeeper does not assess the
 * freshly-staged app (the Developer ID signature is still validated by the kernel on first exec),</li>
 * <li>TCC "App Management" (Ventura+) silently allows an app to modify its own bundle when the new bundle is signed by the same Developer ID team –
 * which is what we check before handing off to the helper.</li>
 * </ul>
 *
 * @author Manuel Laggner
 */
public final class MacAppUpdater {
  private static final Logger LOGGER                   = LoggerFactory.getLogger(MacAppUpdater.class);

  /**
   * marker inside the bundle path when the app is running from a Gatekeeper-translocated location (read-only, randomized mountpoint) – self-update is
   * impossible there
   */
  private static final String APP_TRANSLOCATION_MARKER = "/AppTranslocation/";

  private MacAppUpdater() {
    throw new IllegalAccessError();
  }

  /**
   * Is the current macOS installation eligible for a whole-bundle self-update?<br>
   * <ul>
   * <li>we must be on macOS</li>
   * <li>we must run from a real {@code .app} bundle (not from a dev/IDE classpath)</li>
   * <li>we must <em>not</em> be running via Gatekeeper App Translocation (path is randomized and read-only)</li>
   * <li>the parent directory of the bundle must be writable (POSIX level – TCC is validated later by attempting the swap)</li>
   * </ul>
   *
   * @return true if {@link MacUpdaterTask} can perform an in-place update
   */
  public static boolean isSupported() {
    if (!SystemUtils.IS_OS_MAC) {
      return false;
    }

    Path bundle = getRunningAppBundle();
    if (bundle == null) {
      LOGGER.debug("Not running from a macOS .app bundle – cannot do a bundle update");
      return false;
    }

    if (bundle.toString().contains(APP_TRANSLOCATION_MARKER)) {
      LOGGER.debug("Running from a Gatekeeper-translocated path – refusing to swap the bundle: {}", bundle);
      return false;
    }

    Path parent = bundle.getParent();
    if (parent == null || !Files.isWritable(parent)) {
      LOGGER.debug("Bundle parent '{}' is not writable – cannot swap the bundle", parent);
      return false;
    }

    // the swap must happen on the same volume (atomic rename) – staging dir is directly next to the bundle,
    // so this is guaranteed. Still, verify the bundle is on a real local volume and not on a read-only mount.
    try {
      if (!Files.getFileStore(bundle).supportsFileAttributeView("unix")) {
        LOGGER.debug("Bundle volume does not look like a local Unix filesystem – cannot swap the bundle");
        return false;
      }
    }
    catch (IOException e) {
      LOGGER.debug("Could not query file store of bundle – assume unsupported: '{}'", e.getMessage());
      return false;
    }

    return true;
  }

  /**
   * Walk up from the running {@code tmm.jar} code-source location to the enclosing {@code *.app} directory.
   *
   * @return the absolute path of the running {@code tinyMediaManager.app}, or {@code null} if we are not running from a macOS bundle
   */
  public static Path getRunningAppBundle() {
    if (!SystemUtils.IS_OS_MAC) {
      return null;
    }

    try {
      String rawPath = TinyMediaManager.class.getProtectionDomain().getCodeSource().getLocation().getPath();
      String decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);

      Path current = Paths.get(decoded).toAbsolutePath().normalize();
      while (current != null) {
        if (current.getFileName() != null && current.getFileName().toString().endsWith(".app") && Files.isDirectory(current)) {
          return current;
        }
        current = current.getParent();
      }
    }
    catch (Exception e) {
      LOGGER.debug("Could not determine the running .app bundle – '{}'", e.getMessage());
    }
    return null;
  }

  /**
   * Map the JVM-reported {@code os.arch} to the arch token used in our released macOS DMG filenames (see {@code AppBundler/create_mac_image.sh}).
   *
   * @return {@code aarch64} on Apple-Silicon, {@code x86_64} on Intel
   */
  public static String getMacArchToken() {
    String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (osArch.contains("arm") || osArch.contains("aarch")) {
      return "aarch64";
    }
    return "x86_64";
  }

  /**
   * Derive the DMG download URL from the (channel-specific) getdown appbase URL. The stable/nightly/prerelease servers all expose their artifacts
   * under {@code /v5/dist/} while the getdown files live in {@code /v5/build/}.
   *
   * @param appbase
   *          the successful getdown URL (ending in {@code /})
   * @param humanVersion
   *          the human version incl. channel suffix (e.g. {@code 5.3.3-NIGHTLY}) – this is what the release script embeds into the DMG filename
   * @return the absolute URL to the matching DMG
   */
  public static String deriveDmgUrl(String appbase, String humanVersion) {
    if (appbase == null || !appbase.endsWith("/")) {
      throw new IllegalArgumentException("appbase must be a directory URL ending in '/'");
    }

    String distBase = appbase.endsWith("build/") ? appbase.substring(0, appbase.length() - "build/".length()) + "dist/" : appbase;
    return distBase + "tinyMediaManager-" + humanVersion + "-macos-" + getMacArchToken() + ".dmg";
  }

  /**
   * Create (or refresh) the temporary staging directory directly next to the running bundle so that the swap is guaranteed to be same-volume (POSIX
   * rename is atomic within one filesystem).
   *
   * @return the staging path – the caller must not put anything inside the current {@code .app}, this is a sibling directory named
   *         {@code .tmm-update-staging}
   * @throws IOException
   *           when the directory cannot be created
   */
  public static Path prepareStagingDirectory() throws IOException {
    Path bundle = getRunningAppBundle();
    if (bundle == null) {
      throw new IOException("not running from a macOS .app bundle");
    }
    Path parent = bundle.getParent();
    Path staging = parent.resolve(".tmm-update-staging");
    if (Files.exists(staging)) {
      LOGGER.debug("Staging directory '{}' already exists – clearing it", staging);
      Utils.deleteDirectoryRecursive(staging);
    }
    Files.createDirectories(staging);
    return staging;
  }

  /**
   * The working directory for the macOS bundle update (downloaded DMG, swap-helper script and its log/status files).<br>
   * <br>
   * This deliberately lives directly under {@code java.io.tmpdir} and <b>not</b> inside {@link org.tinymediamanager.Globals#TEMP_FOLDER}:
   * {@code Utils.clearTempFolder()} wipes {@code java.io.tmpdir/tmm} during {@code TinyMediaManager.shutdown()}, which happens <em>before</em>
   * {@code MainWindow.closeTmmAndStart()} spawns the helper - so a script placed there would already be gone when the detached helper is exec'd. The
   * sibling {@code .../tmm-mac-update} survives that purge.
   *
   * @return the (not yet necessarily created) working directory path
   */
  public static Path getUpdateWorkDir() {
    return Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "tmm-mac-update");
  }

  /**
   * Ensure {@link #getUpdateWorkDir()} exists and return it.
   *
   * @return the created/existing working directory
   * @throws IOException
   *           when the directory cannot be created
   */
  private static Path ensureUpdateWorkDir() throws IOException {
    Path dir = getUpdateWorkDir();
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
    }
    return dir;
  }

  /**
   * Where the detached helper script is written to. Placed in {@link #getUpdateWorkDir()} (outside both the current and the staged bundle, and
   * outside the shutdown-purged temp folder), so it can still execute after the bundle swap.
   *
   * @return the script path (parent folder is created)
   * @throws IOException
   *           when the working directory is not writable
   */
  public static Path prepareHelperScriptFile() throws IOException {
    return ensureUpdateWorkDir().resolve("tmm-mac-update.sh");
  }

  /**
   * Build the shell script that performs the actual swap after our process is gone.
   *
   * @param pid
   *          PID of the JVM that is about to terminate – the helper waits for it to disappear
   * @param appBundle
   *          absolute path to the currently running {@code .app}
   * @param stagedAppBundle
   *          absolute path to the freshly-extracted {@code .app} (same volume)
   * @param stagingDir
   *          the sibling staging folder – removed by the helper once the swap succeeded
   * @param logFile
   *          helper script writes everything there – next app start can report failures
   * @return the script content
   */
  public static String buildHelperScript(long pid, Path appBundle, Path stagedAppBundle, Path stagingDir, Path logFile) {
    String bundleName = appBundle.getFileName().toString();
    Path bundleParent = appBundle.getParent();
    String backupPath = bundleParent.resolve(bundleName + ".old").toString();

    // keep this simple and defensive: no set -e (we want to always try to reopen the old app on any failure),
    // quote every path (spaces are common on macOS), write a status file next to the log for the caller.
    return "#!/bin/sh\n" + "# tinyMediaManager macOS bundle swap helper – generated, do not edit\n" + "set -u\n" + "APP="
        + shellQuote(appBundle.toString()) + "\n" + "NEW=" + shellQuote(stagedAppBundle.toString()) + "\n" + "OLD=" + shellQuote(backupPath) + "\n"
        + "STAGING=" + shellQuote(stagingDir.toString()) + "\n" + "PID=" + pid + "\n" + "LOG=" + shellQuote(logFile.toString()) + "\n" + "STATUS="
        + shellQuote(logFile.toString() + ".status") + "\n" + "echo \"[helper] $(date) start\" >> \"$LOG\" 2>&1\n"
        + "# give the JVM up to 30s to shut down cleanly; if it does not, keep the old copy and abort\n" + "i=0\n"
        + "while kill -0 \"$PID\" 2>/dev/null; do\n"
        + "  i=$((i+1)); if [ $i -gt 60 ]; then echo \"[helper] pid $PID did not exit\" >> \"$LOG\"; echo failed > \"$STATUS\"; exit 1; fi\n"
        + "  sleep 0.5\n" + "done\n" + "echo \"[helper] pid $PID gone\" >> \"$LOG\"\n"
        + "# move the old bundle out of the way (POSIX allows renaming a running bundle)\n" + "if ! mv \"$APP\" \"$OLD\" 2>>\"$LOG\"; then\n"
        + "  echo \"[helper] mv old bundle failed\" >> \"$LOG\"; echo failed > \"$STATUS\"; exit 1\n" + "fi\n"
        + "# place the new bundle at the original path\n" + "if ! mv \"$NEW\" \"$APP\" 2>>\"$LOG\"; then\n"
        + "  echo \"[helper] mv new bundle failed – rolling back\" >> \"$LOG\"\n" + "  mv \"$OLD\" \"$APP\" 2>>\"$LOG\"\n"
        + "  echo failed > \"$STATUS\"\n" + "  open \"$APP\"\n" + "  exit 1\n" + "fi\n" + "echo ok > \"$STATUS\"\n"
        + "# swap succeeded – drop the (now empty) staging folder and reopen the updated app\n" + "rm -rf \"$STAGING\"\n"
        + "open \"$APP\" 2>>\"$LOG\"\n" + "rm -f \"$0\"\n";
  }

  /**
   * Wrap a value in single quotes for {@code /bin/sh} (also escapes any inner single quotes).
   *
   * @param value
   *          the raw string
   * @return the shell-safe representation
   */
  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  /**
   * Marker file left in the bundle's parent folder by the previous run (the {@code .old} bundle).<br>
   * Present on first launch of the newly-updated app – used to trigger cleanup.
   *
   * @return the path of the backup bundle (may or may not exist)
   */
  public static Path getBackupAppBundlePath() {
    Path bundle = getRunningAppBundle();
    if (bundle == null) {
      return null;
    }
    Path parent = bundle.getParent();
    String name = bundle.getFileName().toString();
    return parent.resolve(name + ".old");
  }

  /**
   * The sibling staging directory used while an update is being prepared ({@code .tmm-update-staging}). May or may not exist – used by
   * {@link #handleFinishedUpdate()} to sweep leftovers from a deferred/failed update.
   *
   * @return the staging path, or {@code null} if we are not running from a bundle
   */
  public static Path getStagingDirectoryPath() {
    Path bundle = getRunningAppBundle();
    if (bundle == null || bundle.getParent() == null) {
      return null;
    }
    return bundle.getParent().resolve(".tmm-update-staging");
  }

  /**
   * Location of the last written helper log – the newly-started app can peek at it to detect a failed swap.
   *
   * @return the log file path (in {@link #getUpdateWorkDir()})
   */
  public static Path getHelperLogFile() {
    return getUpdateWorkDir().resolve("tmm-mac-update.log");
  }

  /**
   * Called once on startup (after the message manager is up) to finish a bundle swap that was performed by the detached helper while this instance
   * was booting:
   * <ul>
   * <li>if the helper reported a failed swap, push an error message,</li>
   * <li>otherwise remove the {@code .old} backup bundle the helper left behind to reclaim disk space.</li>
   * </ul>
   * Does nothing if the previous launch was not a macOS bundle update.
   */
  public static void handleFinishedUpdate() {
    if (!SystemUtils.IS_OS_MAC) {
      return;
    }

    Path statusFile = Paths.get(getHelperLogFile() + ".status");
    if (Files.exists(statusFile)) {
      try {
        String status = Files.readString(statusFile, StandardCharsets.UTF_8).strip();
        Files.deleteIfExists(statusFile);
        Files.deleteIfExists(getHelperLogFile());
        if (!"ok".equalsIgnoreCase(status)) {
          LOGGER.warn("the macOS bundle swap reported status '{}' – the update was not applied", status);
          MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, "Updater", "tmm.updater.mac.swapfailed"));
        }
      }
      catch (IOException e) {
        LOGGER.debug("could not read helper status file '{}' - '{}'", statusFile, e.getMessage());
      }
    }

    Path backup = getBackupAppBundlePath();
    if (backup != null && Files.exists(backup)) {
      // the new bundle is validly running – the previous version is now redundant, remove it
      try {
        Utils.deleteDirectoryRecursive(backup);
        LOGGER.debug("Removed the previous bundle backup '{}'", backup.getFileName());
        MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.INFO, "Updater", "tmm.updater.mac.success"));
      }
      catch (IOException e) {
        LOGGER.debug("Could not remove the old bundle '{}' - '{}'", backup, e.getMessage());
        MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.WARN, "Updater", "tmm.updater.mac.leftover"));
      }
    }

    // sweep any staging folder left behind by a deferred or failed update (the helper only removes it on success)
    Path stagingDir = getStagingDirectoryPath();
    if (stagingDir != null && Files.exists(stagingDir)) {
      try {
        Utils.deleteDirectoryRecursive(stagingDir);
        LOGGER.debug("Removed leftover update staging folder '{}'", stagingDir);
      }
      catch (IOException e) {
        LOGGER.debug("Could not remove leftover staging folder '{}' - '{}'", stagingDir, e.getMessage());
      }
    }

    // the work dir (script/log/status/dmg) is outside the shutdown-purged temp folder, so sweep it here once
    // the swap is done - otherwise leftovers would survive across reboots
    Utils.deleteDirectorySafely(getUpdateWorkDir());
  }
}

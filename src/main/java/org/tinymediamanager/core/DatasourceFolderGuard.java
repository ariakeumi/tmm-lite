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
package org.tinymediamanager.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.tinymediamanager.Globals;
import org.tinymediamanager.TmmOsUtils;

/**
 * The {@link DatasourceFolderGuard} - prevents adding dangerous system folders (or the tinyMediaManager data folder itself) as a data source.
 * <p>
 * Data sources are actively worked on by tinyMediaManager (renaming, deleting, creating folders), so adding the operating system root, system
 * folders, the whole user folder or the tinyMediaManager data folder can cause severe damage.
 * </p>
 *
 * @author Manuel Laggner
 */
public final class DatasourceFolderGuard {
  /**
   * The operating system family to evaluate a path against (independent of the actually running OS, for testability).
   */
  public enum Os {
    WINDOWS,
    LINUX,
    MAC_OS
  }

  private DatasourceFolderGuard() {
    // static utility only
    throw new IllegalAccessError();
  }

  /**
   * Checks whether the given folder must not be used as a data source on the currently running OS.
   *
   * @param folder
   *          the folder to check
   * @return true if the folder is considered dangerous
   */
  public static boolean isDangerous(Path folder) {
    return getDangerReason(folder).isPresent();
  }

  /**
   * Checks whether the given folder must not be used as a data source on the currently running OS.
   *
   * @param folder
   *          the folder to check
   * @return the reason why the folder is dangerous, or empty if it can be used as a data source
   */
  public static Optional<String> getDangerReason(Path folder) {
    if (folder == null) {
      return Optional.empty();
    }

    Os os;
    if (SystemUtils.IS_OS_WINDOWS) {
      os = Os.WINDOWS;
    }
    else if (SystemUtils.IS_OS_MAC) {
      os = Os.MAC_OS;
    }
    else {
      os = Os.LINUX;
    }

    String home = System.getProperty("user.home");

    List<String> systemFolders = new ArrayList<>();
    if (os == Os.WINDOWS) {
      addIfNotBlank(systemFolders, System.getenv("SystemRoot"));
      addIfNotBlank(systemFolders, System.getenv("ProgramFiles"));
      addIfNotBlank(systemFolders, System.getenv("ProgramFiles(x86)"));
      addIfNotBlank(systemFolders, System.getenv("CommonProgramFiles"));
      addIfNotBlank(systemFolders, System.getenv("CommonProgramFiles(x86)"));
      addIfNotBlank(systemFolders, System.getenv("ProgramData"));
      addIfNotBlank(systemFolders, System.getenv("APPDATA"));
      addIfNotBlank(systemFolders, System.getenv("LOCALAPPDATA"));
      if (StringUtils.isNotBlank(home)) {
        systemFolders.add(home + "\\AppData");
      }
    }
    if (os == Os.MAC_OS && StringUtils.isNotBlank(home)) {
      systemFolders.add(home + "/Library");
    }

    List<String> tmmDataFolders = new ArrayList<>();
    tmmDataFolders.add(Globals.DATA_FOLDER);
    tmmDataFolders.add(TmmOsUtils.getUserDir().toString());

    return check(folder.toString(), os, home, systemFolders, tmmDataFolders);
  }

  private static void addIfNotBlank(List<String> list, String value) {
    if (StringUtils.isNotBlank(value)) {
      list.add(value);
    }
  }

  /**
   * The actual (pure) check, decoupled from the running OS to be unit-testable on any platform.
   *
   * @param rawPath
   *          the absolute path to check (may contain the separators of the given OS)
   * @param os
   *          the OS family to evaluate against
   * @param home
   *          the user home folder (may be blank)
   * @param systemFolders
   *          system folders which are protected completely (the folder itself and everything below it)
   * @param tmmDataFolders
   *          tinyMediaManager data folders which are protected (the folder itself and all its ancestors, but NOT their children)
   * @return the reason why the folder is dangerous, or empty if it can be used as a data source
   */
  static Optional<String> check(String rawPath, Os os, String home, Collection<String> systemFolders, Collection<String> tmmDataFolders) {
    if (StringUtils.isBlank(rawPath)) {
      return Optional.of("empty path");
    }

    boolean caseInsensitive = os == Os.WINDOWS;
    String unified = rawPath.replace('\\', '/');
    if (caseInsensitive) {
      unified = unified.toLowerCase(Locale.ROOT);
    }

    // UNC paths (\\server\share) are no local system folders - let them pass
    if (os == Os.WINDOWS && unified.startsWith("//")) {
      return Optional.empty();
    }

    List<String> parts = splitPath(unified);

    List<String> homeParts = StringUtils.isBlank(home) ? List.of() : splitPath(unify(home, caseInsensitive));

    if (os == Os.WINDOWS) {
      // only drive roots which hold system folders are dangerous (system drive, redirected user profile) -
      // plain USB or NAS drives (e.g. H:\ or Z:\) are legit data sources
      if (parts.size() == 1 && startsWith(homeParts, parts)) {
        return Optional.of("root of the drive containing the user home");
      }

      if (parts.size() == 1) {
        for (String systemFolder : systemFolders) {
          if (startsWith(splitPath(unify(systemFolder, caseInsensitive)), parts)) {
            return Optional.of("root of a drive containing system folders");
          }
        }
      }
    }
    else if (parts.size() <= 1) {
      // the root itself or a direct child of it (e.g. /dev, /var, /mnt, /Volumes, /Library, ...)
      return Optional.of("system folder at top level");
    }

    if (!homeParts.isEmpty()) {
      if (parts.equals(homeParts)) {
        // the whole user folder
        return Optional.of("the user home folder");
      }

      if (os == Os.WINDOWS) {
        List<String> usersRoot = homeParts.subList(0, homeParts.size() - 1);
        if (!usersRoot.isEmpty() && parts.equals(usersRoot)) {
          // the folder containing all user profiles (e.g. C:\Users)
          return Optional.of("the user profiles folder");
        }
      }

      if (parts.size() > homeParts.size() && startsWith(parts, homeParts)) {
        String firstChild = parts.get(homeParts.size());
        if (firstChild.startsWith(".")) {
          // hidden folders directly in the user folder (e.g. ~/.local, ~/.config)
          return Optional.of("hidden folder in the user home");
        }
      }
    }

    for (String systemFolder : systemFolders) {
      List<String> systemParts = splitPath(unify(systemFolder, caseInsensitive));
      if (systemParts.isEmpty()) {
        continue;
      }

      if (startsWith(parts, systemParts) || startsWith(systemParts, parts)) {
        // inside a protected system folder (e.g. C:\Windows, ~/Library) or an ancestor of it (e.g. C:\Users\<name>\AppData)
        return Optional.of("protected system folder");
      }
    }

    for (String tmmDataFolder : tmmDataFolders) {
      List<String> dataParts = splitPath(unify(tmmDataFolder, caseInsensitive));
      if (dataParts.isEmpty()) {
        continue;
      }

      if (startsWith(dataParts, parts)) {
        // the tinyMediaManager data folder itself or one of its ancestors
        return Optional.of("the tinyMediaManager data folder (or an ancestor of it)");
      }
    }

    return Optional.empty();
  }

  private static String unify(String path, boolean lowerCase) {
    String unified = path.replace('\\', '/');
    if (lowerCase) {
      unified = unified.toLowerCase(Locale.ROOT);
    }
    return unified;
  }

  /**
   * Splits an already unified (forward slashes, no drive letter casing issues) path into its components. The root {@code /} yields an empty list, a
   * Windows drive root yields a single element list (e.g. {@code [c:]}).
   */
  private static List<String> splitPath(String unifiedPath) {
    List<String> parts = new ArrayList<>();
    for (String part : unifiedPath.split("/")) {
      if (StringUtils.isNotBlank(part)) {
        parts.add(part);
      }
    }

    return parts;
  }

  private static boolean startsWith(List<String> path, List<String> prefix) {
    if (path.size() < prefix.size()) {
      return false;
    }
    for (int i = 0; i < prefix.size(); i++) {
      if (!path.get(i).equals(prefix.get(i))) {
        return false;
      }
    }
    return true;
  }
}

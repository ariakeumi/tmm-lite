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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.core.Utils;

/**
 * Unit tests for the platform-independent, pure-logic parts of {@link MacAppUpdater} (URL derivation, arch mapping and the generated swap-helper
 * script). The actual macOS interactions (hdiutil / ditto / codesign / bundle swap) are inherently platform specific and are covered by manual QA,
 * not here.
 *
 * @author Manuel Laggner
 */
public class MacAppUpdaterTest {
  private String originalArch;

  @Before
  public void setUp() {
    originalArch = System.getProperty("os.arch");
  }

  @After
  public void tearDown() {
    if (originalArch != null) {
      System.setProperty("os.arch", originalArch);
    }
    else {
      System.clearProperty("os.arch");
    }
  }

  @Test
  public void macArchTokenMapsAppleSilicon() {
    System.setProperty("os.arch", "aarch64");
    assertThat(MacAppUpdater.getMacArchToken()).isEqualTo("aarch64");

    System.setProperty("os.arch", "arm64");
    assertThat(MacAppUpdater.getMacArchToken()).isEqualTo("aarch64");
  }

  @Test
  public void macArchTokenMapsIntel() {
    System.setProperty("os.arch", "x86_64");
    assertThat(MacAppUpdater.getMacArchToken()).isEqualTo("x86_64");
  }

  @Test
  public void deriveDmgUrlMapsBuildToDist() {
    System.setProperty("os.arch", "x86_64");

    String url = MacAppUpdater.deriveDmgUrl("https://release.tinymediamanager.org/v5/build/", "5.3.2");
    assertThat(url).isEqualTo("https://release.tinymediamanager.org/v5/dist/tinyMediaManager-5.3.2-macos-x86_64.dmg");
  }

  @Test
  public void deriveDmgUrlKeepsChannelAndArch() {
    System.setProperty("os.arch", "aarch64");

    // nightly human versions carry the channel suffix, which is part of the DMG filename
    String url = MacAppUpdater.deriveDmgUrl("https://nightly.tinymediamanager.org/v5/build/", "5.3.3-NIGHTLY");
    assertThat(url).isEqualTo("https://nightly.tinymediamanager.org/v5/dist/tinyMediaManager-5.3.3-NIGHTLY-macos-aarch64.dmg");
  }

  @Test
  public void deriveDmgUrlWithoutBuildSuffixJustAppends() {
    System.setProperty("os.arch", "aarch64");

    String url = MacAppUpdater.deriveDmgUrl("https://example.org/whatever/", "5.4.0");
    assertThat(url).isEqualTo("https://example.org/whatever/tinyMediaManager-5.4.0-macos-aarch64.dmg");
  }

  @Test
  public void deriveDmgUrlRequiresDirectoryUrl() {
    assertThatThrownBy(() -> MacAppUpdater.deriveDmgUrl("https://example.org/file.txt", "5.4.0")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void helperScriptEmbedsQuotedPathsAndControlFlow() {
    Path bundle = Paths.get("/Applications/tinyMediaManager.app");
    Path staged = Paths.get("/Applications/.tmm-update-staging/tinyMediaManager.app");
    Path staging = Paths.get("/Applications/.tmm-update-staging");
    Path log = Paths.get("/tmp/tmm-mac-update.log");

    String script = MacAppUpdater.buildHelperScript(4242, bundle, staged, staging, log);

    // waits for our JVM to exit before touching anything
    assertThat(script).contains("while kill -0 \"$PID\"");
    assertThat(script).contains("PID=4242");
    // moves the running bundle aside, then the staged one into place (same-volume atomic rename)
    assertThat(script).contains("mv \"$APP\" \"$OLD\"");
    assertThat(script).contains("mv \"$NEW\" \"$APP\"");
    // writes a status file the next startup inspects, then relaunches via LaunchServices
    assertThat(script).contains("echo ok > \"$STATUS\"");
    assertThat(script).contains("open \"$APP\"");
    // paths are single-quoted so a space in /Applications/my app would be safe
    assertThat(script).contains("APP='/Applications/tinyMediaManager.app'");
  }

  @Test
  public void helperScriptEscapesSingleQuotesInPaths() {
    Path bundle = Paths.get("/Applications/it's tinyMediaManager.app");
    Path staged = Paths.get("/Applications/it's tinyMediaManager.app");
    Path staging = Paths.get("/Applications/.tmm-update-staging");
    Path log = Paths.get("/tmp/tmm-mac-update.log");

    String script = MacAppUpdater.buildHelperScript(1, bundle, staged, staging, log);

    // '\'' idiom: close quote, escaped literal quote, reopen quote
    assertThat(script).contains("'\\''");
    assertThat(script).doesNotContain("''/Applications");
  }

  /**
   * Regression guard for the failed first live test: the swap helper, its log/status and the DMG must NOT live inside {@code Globals.TEMP_FOLDER},
   * because {@code Utils.clearTempFolder()} wipes that directory during {@code TinyMediaManager.shutdown()} – which runs <em>before</em>
   * {@code closeTmmAndStart()} execs the helper, so the script would already be gone at spawn time.
   */
  @Test
  public void updateWorkDirIsOutsideTheShutdownPurgedTempFolder() {
    Path workDir = MacAppUpdater.getUpdateWorkDir();
    Path purgedTemp = Paths.get(Utils.getTempFolder());

    assertThat(workDir.getFileName().toString()).isEqualTo("tmm-mac-update");
    // the helper would be deleted before it could run if this ever became a child of the purged temp folder
    assertThat(workDir.startsWith(purgedTemp)).isFalse();
  }
}

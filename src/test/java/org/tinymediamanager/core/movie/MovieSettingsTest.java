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

package org.tinymediamanager.core.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.junit.Test;
import org.tinymediamanager.core.Utils;

public class MovieSettingsTest extends BasicMovieTest {

  @Test
  public void testMovieSettings() throws Exception {
    MovieSettings settings = MovieSettings.getInstance();
    assertThat(settings).isNotNull();
    settings.getDefaultRenamerProfile().setAsciiReplacement(true);

    // let the dirty flag set by the async propertychange listener
    Thread.sleep(100);

    settings.saveSettings();

    // cannot re-instantiate settings - need to check plain file
    String config = Utils.readFileToString(getSettingsFolder().resolve(MovieModuleManager.getInstance().getSettings().getConfigFilename()));
    assertTrue(config.contains("\"asciiReplacement\" : true"));
  }

  @Test
  public void testMovieSettingsLoading() throws Exception {
    copyResourceFolderToWorkFolder("settings/movie_renamer_loading");

    Path settingsFolder = getWorkFolder().resolve("settings/movie_renamer_loading");
    MovieSettings settings = MovieSettings.getInstance(settingsFolder.toAbsolutePath().toString());
    assertThat(settings.getMovieDataSource()).isNotEmpty();
  }

  @Test
  public void testUpgradeRenamerProfile() throws Exception {
    copyResourceFolderToWorkFolder("settings/movie_renamer_upgrade");

    Path settingsFolder = getWorkFolder().resolve("settings/movie_renamer_upgrade");
    MovieSettings settings = MovieSettings.getInstance(settingsFolder.toAbsolutePath().toString());
    assertThat(settings.getRenamerProfiles()).hasSize(1);

    MovieRenamerProfile profile = settings.getDefaultRenamerProfile();
    assertNotNull(profile);
    assertThat(profile.getName()).isEqualTo(MovieRenamerProfile.DEFAULT_RENAMER_PROFILE);

    // verify all renamer fields were upgraded from the old-style JSON
    assertThat(profile.getRenamerPathname()).isEqualTo("${title} ${- ,edition,} (${year}) xxx");
    assertThat(profile.getRenamerFilename()).isEqualTo("${title} ${- ,edition,} (${year}) ${videoFormat} ${audioCodec} xxx");

    assertThat(profile.isRenamerPathnameSpaceSubstitution()).isTrue();
    assertThat(profile.getRenamerPathnameSpaceReplacement()).isEqualTo("-");
    assertThat(profile.isRenamerFilenameSpaceSubstitution()).isTrue();
    assertThat(profile.getRenamerFilenameSpaceReplacement()).isEqualTo("-");

    assertThat(profile.getRenamerColonReplacement()).isEqualTo("\u2236");
    assertThat(profile.isRenamerNfoCleanup()).isTrue();
    assertThat(profile.isRenamerCleanupUnwanted()).isTrue();
    assertThat(profile.isRenamerCreateMoviesetForSingleMovie()).isTrue();
    assertThat(profile.getRenamerFirstCharacterNumberReplacement()).isEqualTo("#");

    assertThat(profile.isAsciiReplacement()).isFalse();
    assertThat(profile.isUnicodeReplacement()).isTrue();
    assertThat(profile.isAllowMultipleMoviesInSameDir()).isTrue();
  }
}

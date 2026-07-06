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

package org.tinymediamanager.core.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.junit.Test;
import org.tinymediamanager.core.Utils;

public class TvShowSettingsTest extends BasicTvShowTest {

  @Test
  public void testTvShowSettings() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    assertThat(settings).isNotNull();
    settings.getDefaultRenamerProfile().setAsciiReplacement(true);
    Thread.sleep(1000); // sleep here because the dirty listener is async
    settings.saveSettings();

    // cannot re-instantiate settings - need to check plain file
    String config = Utils.readFileToString(getSettingsFolder().resolve(TvShowModuleManager.getInstance().getSettings().getConfigFilename()));
    assertTrue(config.contains("\"asciiReplacement\" : true"));
  }

  @Test
  public void testTvShowSettingsLoading() throws Exception {
    copyResourceFolderToWorkFolder("settings/tvshow_renamer_loading");

    Path settingsFolder = getWorkFolder().resolve("settings/tvshow_renamer_loading");
    TvShowSettings settings = TvShowSettings.getInstance(settingsFolder.toAbsolutePath().toString());
    assertThat(settings.getTvShowDataSource()).isNotEmpty();
  }

  @Test
  public void testRenamerProfileCRUD() {
    TvShowSettings settings = TvShowSettings.getInstance();

    // initially the "Default" profile exists
    assertThat(settings.getRenamerProfiles()).containsKey(TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE);
    assertThat(settings.getRenamerProfiles()).hasSize(1);

    // add a new profile
    settings.addRenamerProfile(new TvShowRenamerProfile("MyCustom"));
    assertThat(settings.getRenamerProfiles()).containsKey("MyCustom");
    assertThat(settings.getRenamerProfiles()).hasSize(2);

    // get it by name
    TvShowRenamerProfile custom = settings.getRenamerProfile("MyCustom");
    assertNotNull(custom);
    assertThat(custom.getName()).isEqualTo("MyCustom");

    // getDefaultRenamerProfile returns the "Default" one
    TvShowRenamerProfile def = settings.getDefaultRenamerProfile();
    assertNotNull(def);
    assertThat(def.getName()).isEqualTo(TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE);

    // delete the custom profile
    settings.deleteRenamerProfile("MyCustom");
    assertThat(settings.getRenamerProfiles()).doesNotContainKey("MyCustom");
    assertThat(settings.getRenamerProfiles()).hasSize(1);
  }

  @Test
  public void testRenamerDefaultProfileCannotBeDeleted() {
    TvShowSettings settings = TvShowSettings.getInstance();
    assertThrows(IllegalArgumentException.class, () -> settings.deleteRenamerProfile(TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE));
  }

  @Test
  public void testRenamerProfileCopyConstructors() {
    TvShowSettings settings = TvShowSettings.getInstance();
    TvShowRenamerProfile original = settings.getDefaultRenamerProfile();
    original.setRenamerTvShowFoldername("CustomShowFolder");
    original.setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle.RANGE);

    // copy via no-name copy constructor
    TvShowRenamerProfile copy = new TvShowRenamerProfile(original);
    assertThat(copy.getName()).isEqualTo(original.getName());
    assertThat(copy.getRenamerTvShowFoldername()).isEqualTo("CustomShowFolder");
    assertThat(copy.getRenamerMultiEpisodeStyle()).isEqualTo(TvShowMultiEpisodeStyle.RANGE);

    // copy via named copy constructor
    TvShowRenamerProfile namedCopy = new TvShowRenamerProfile("NamedCopy", original);
    assertThat(namedCopy.getName()).isEqualTo("NamedCopy");
    assertThat(namedCopy.getRenamerTvShowFoldername()).isEqualTo("CustomShowFolder");
    assertThat(namedCopy.getRenamerMultiEpisodeStyle()).isEqualTo(TvShowMultiEpisodeStyle.RANGE);

    // modifying the copy does not affect the original
    copy.setRenamerTvShowFoldername("Changed");
    assertThat(original.getRenamerTvShowFoldername()).isEqualTo("CustomShowFolder");
  }

  @Test
  public void testRenamerProfilePersistence() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();

    // add a custom profile with distinct settings
    TvShowRenamerProfile custom = new TvShowRenamerProfile("PersistentProfile");
    custom.setRenamerTvShowFoldername("${showTitle} - Custom");
    custom.setRenamerColonReplacement(" - ");
    custom.setRenamerCleanupUnwanted(true);
    settings.addRenamerProfile(custom);

    Thread.sleep(500); // let the async dirty listener fire
    settings.saveSettings();

    // read config file and verify both profiles are present
    String config = Utils.readFileToString(getSettingsFolder().resolve(settings.getConfigFilename()));
    assertTrue(config.contains("\"Default\""));
    assertTrue(config.contains("\"PersistentProfile\""));
    assertTrue(config.contains("${showTitle} - Custom"));
    assertTrue(config.contains("\"renamerCleanupUnwanted\" : true"));
  }

  @Test
  public void testRenamerProfileReplaceUpdatesSettings() {
    TvShowSettings settings = TvShowSettings.getInstance();

    // add a custom profile
    settings.addRenamerProfile(new TvShowRenamerProfile("ReplaceTest"));
    assertThat(settings.getRenamerProfiles()).hasSize(2);

    // replace it with an updated one
    TvShowRenamerProfile updated = new TvShowRenamerProfile("ReplaceTest");
    updated.setRenamerFilename("CustomFilePattern");
    settings.addRenamerProfile(updated);

    assertThat(settings.getRenamerProfiles()).hasSize(2); // no duplicate
    assertThat(settings.getRenamerProfile("ReplaceTest").getRenamerFilename()).isEqualTo("CustomFilePattern");
  }

  @Test
  public void testRenamerProfileGetOrCreateAutoVivifies() {
    TvShowSettings settings = TvShowSettings.getInstance();

    // requesting a non-existent profile creates it
    TvShowRenamerProfile auto = settings.getRenamerProfile("AutoCreated");
    assertNotNull(auto);
    assertThat(auto.getName()).isEqualTo("AutoCreated");
    assertThat(settings.getRenamerProfiles()).containsKey("AutoCreated");

    // requesting the same name returns the same instance
    TvShowRenamerProfile autoAgain = settings.getRenamerProfile("AutoCreated");
    assertThat(autoAgain).isSameAs(auto);
  }

  @Test
  public void testRenamerProfilesAreIndependent() {
    TvShowSettings settings = TvShowSettings.getInstance();

    // add two custom profiles
    settings.addRenamerProfile(new TvShowRenamerProfile("ProfileA"));
    settings.addRenamerProfile(new TvShowRenamerProfile("ProfileB"));

    TvShowRenamerProfile a = settings.getRenamerProfile("ProfileA");
    TvShowRenamerProfile b = settings.getRenamerProfile("ProfileB");

    // modify them independently
    a.setRenamerTvShowFoldername("FolderA");
    b.setRenamerTvShowFoldername("FolderB");
    a.setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle.RANGE);
    b.setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle.REPEAT);

    assertThat(a.getRenamerTvShowFoldername()).isEqualTo("FolderA");
    assertThat(b.getRenamerTvShowFoldername()).isEqualTo("FolderB");
    assertThat(a.getRenamerMultiEpisodeStyle()).isEqualTo(TvShowMultiEpisodeStyle.RANGE);
    assertThat(b.getRenamerMultiEpisodeStyle()).isEqualTo(TvShowMultiEpisodeStyle.REPEAT);
  }

  @Test
  public void testUpgradeRenamerProfile() throws Exception {
    copyResourceFolderToWorkFolder("settings/tvshow_renamer_upgrade");

    Path settingsFolder = getWorkFolder().resolve("settings/tvshow_renamer_upgrade");
    TvShowSettings settings = TvShowSettings.getInstance(settingsFolder.toAbsolutePath().toString());
    assertThat(settings.getRenamerProfiles()).hasSize(1);

    TvShowRenamerProfile profile = settings.getDefaultRenamerProfile();
    assertNotNull(profile);
    assertThat(profile.getName()).isEqualTo(TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE);

    // verify all renamer fields were upgraded from the old-style JSON
    assertThat(profile.getRenamerTvShowFoldername()).isEqualTo("${showTitle} (${showYear}) xxx");
    assertThat(profile.getRenamerSeasonFoldername()).isEqualTo("Season ${seasonNr} xxx");
    assertThat(profile.getRenamerFilename()).isEqualTo("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title} xxx");

    assertThat(profile.isRenamerShowPathnameSpaceSubstitution()).isTrue();
    assertThat(profile.getRenamerShowPathnameSpaceReplacement()).isEqualTo("-");
    assertThat(profile.isRenamerSeasonPathnameSpaceSubstitution()).isTrue();
    assertThat(profile.getRenamerSeasonPathnameSpaceReplacement()).isEqualTo("-");
    assertThat(profile.isRenamerFilenameSpaceSubstitution()).isTrue();
    assertThat(profile.getRenamerFilenameSpaceReplacement()).isEqualTo("-");

    assertThat(profile.getRenamerColonReplacement()).isEqualTo("\u2236");
    assertThat(profile.isRenamerCleanupUnwanted()).isTrue();
    assertThat(profile.getRenamerFirstCharacterNumberReplacement()).isEqualTo("#");

    assertThat(profile.isAsciiReplacement()).isFalse();
    assertThat(profile.isUnicodeReplacement()).isTrue();
    assertThat(profile.isSpecialSeason()).isTrue();
    assertThat(profile.isCreateMissingSeasonItems()).isFalse();
  }
}

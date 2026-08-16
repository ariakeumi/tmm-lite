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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * tests the one-time migration of the legacy trakt.tv OAuth tokens from the settings JSON to the {@link TmmStore}
 *
 * @author Manuel Laggner
 */
public class TraktTokenMigrationTest extends BasicTest {

  @Test
  public void testMigrateLegacyTraktTokens() throws Exception {
    String accessToken = "legacy-access-token";
    String refreshToken = "legacy-refresh-token";

    // the legacy tokens have been stored encrypted in the settings JSON
    String encryptedAccessToken = AesUtil.DEFAULT_INSTANCE.encrypt(AesUtil.DEFAULT_SALT, AesUtil.DEFAULT_VECTOR, AesUtil.DEFAULT_VECTOR, accessToken);
    String encryptedRefreshToken = AesUtil.DEFAULT_INSTANCE.encrypt(AesUtil.DEFAULT_SALT, AesUtil.DEFAULT_VECTOR, AesUtil.DEFAULT_VECTOR,
        refreshToken);

    // write a legacy settings JSON containing the tokens
    Path settingsFolder = getSettingsFolder();
    Path configFile = settingsFolder.resolve("tmm.json");
    String json = "{\"traktAccessToken\":\"" + encryptedAccessToken + "\",\"traktRefreshToken\":\"" + encryptedRefreshToken + "\"}";
    Files.writeString(configFile, json);

    // reload the settings to trigger the migration
    Settings.clearInstance();
    Settings.getInstance(settingsFolder.toString());

    // the tokens should now live in the TmmStore
    assertEqual(accessToken, TmmStore.getInstance().get("trakt.access_token.secret"));
    assertEqual(refreshToken, TmmStore.getInstance().get("trakt.refresh_token.secret"));

    // and they should no longer be part of the settings JSON
    Settings.getInstance().forceSaveSettings();
    String savedJson = Files.readString(configFile);
    assertEqual(false, savedJson.contains("traktAccessToken"));
    assertEqual(false, savedJson.contains("traktRefreshToken"));
  }
}

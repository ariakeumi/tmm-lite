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
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/**
 * tests for the general purpose {@link TmmStore}
 *
 * @author Manuel Laggner
 */
public class TmmStoreTest extends BasicTest {

  @Test
  public void testPutGetRemove() {
    TmmStore store = TmmStore.getInstance();

    store.put("foo", "bar");
    assertEqual("bar", store.get("foo"));
    assertEqual(true, store.contains("foo"));

    store.remove("foo");
    assertEqual(null, store.get("foo"));
    assertEqual(false, store.contains("foo"));
  }

  @Test
  public void testTypedAccessors() {
    TmmStore store = TmmStore.getInstance();

    store.put("timestamp", String.valueOf(System.currentTimeMillis() - 1000));
    store.put("counter", "42");
    store.put("enabled", "true");

    long timestamp = store.getAsLong("timestamp", 0L);
    assertEqual(false, timestamp == 0L);
    assertEqual(true, timestamp <= System.currentTimeMillis() - 1000L);
    assertEqual(1000L, store.getAsLong("wrong", 1000L));
    assertEqual(0, store.getAsInt("wrong", 0));
    assertEqual(42, store.getAsInt("counter", 0));
    assertEqual(false, store.getAsBoolean("wrong", false));
    assertEqual(true, store.getAsBoolean("enabled", false));
  }

  @Test
  public void testSecretEncryption() throws Exception {
    TmmStore store = TmmStore.getInstance();

    store.put("apiKey.secret", "my-secret-key");
    assertEqual("my-secret-key", store.get("apiKey.secret"));
    assertNotEqual("my-secret-key", store.getPlain("apiKey.secret"));

    // the value should not be stored in plain text on disk
    Path databaseFile = Paths.get(getSettingsFolder().toString(), "tmm.db");
    Assert.assertTrue(Files.exists(databaseFile));

    String content = new String(Files.readAllBytes(databaseFile), java.nio.charset.StandardCharsets.ISO_8859_1);
    assertEqual(false, content.contains("my-secret-key"));
  }

  @Test
  public void testPersistenceAcrossRestart() throws Exception {
    TmmStore store = TmmStore.getInstance();
    store.put("persistent", "value");

    TmmStore.clearInstances();

    TmmStore store2 = TmmStore.getInstance();
    assertEqual("value", store2.get("persistent"));
  }
}

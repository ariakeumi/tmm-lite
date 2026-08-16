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
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;

/**
 * the class {@link TmmStore} provides a general purpose key/value store based on an H2 MVStore.<br>
 * <p>
 * It is meant to store various things on the level of tinyMediaManager itself - like API keys, access tokens, timestamps, etc.<br>
 * Values stored under a key postfixed with {@code .secret} will be encrypted with the {@link AesUtil} on disk.
 * </p>
 *
 * @author Manuel Laggner
 */
public class TmmStore {
  private static final Logger          LOGGER         = LoggerFactory.getLogger(TmmStore.class);
  private static final String          STORE_DB       = "tmm.db";
  private static final String          SECRET_POSTFIX = ".secret";
  private static final String          SALT           = "3FF2EB019C627B9652257EAAD71812269851E84295370EB132882F88C0A59A76";
  private static final String          IV             = "E17D2C8927726ACE1E7510A1BDD3D439";

  private static final AesUtil         AES_UTIL       = new AesUtil(128, 100);

  private static TmmStore              instance;
  private static MVStore               mvStore;
  private static MVMap<String, String> storeMap;
  private static String                overriddenStoreFolder;

  private TmmStore() {
    init();
  }

  /**
   * override the folder where the store resides in<br>
   * <b>Should only be used for unit testing et al.!</b><br>
   *
   * @param folder
   *          the folder to store the database in (null to revert to the default folder)
   */
  static synchronized void setStoreFolder(String folder) {
    shutdown();
    overriddenStoreFolder = folder;
  }

  /**
   * get the folder where the store resides in
   *
   * @return the folder
   */
  private static String getStoreFolder() {
    return StringUtils.isNotBlank(overriddenStoreFolder) ? overriddenStoreFolder : Globals.DATA_FOLDER;
  }

  /**
   * get the single instance of the {@link TmmStore}
   *
   * @return the instance
   */
  public static synchronized TmmStore getInstance() {
    if (instance == null) {
      instance = new TmmStore();
    }
    return instance;
  }

  /**
   * removes the active instance <br>
   * <b>Should only be used for unit testing et al.!</b><br>
   */
  static synchronized void clearInstances() {
    shutdown();
    instance = null;
    overriddenStoreFolder = null;
  }

  /**
   * initialize the MVStore - if it is not opened yet, or has been closed before
   */
  private static synchronized void init() {
    if (mvStore != null && !mvStore.isClosed()) {
      return;
    }

    Path databaseFile = Paths.get(getStoreFolder(), STORE_DB);

    try {
      try {
        mvStore = new MVStore.Builder().fileName(databaseFile.toString()).compressHigh().autoCommitDisabled().open();
      }
      catch (Exception e) {
        LOGGER.debug("Could not open TMM store database - '{}'", e.getMessage());
        Utils.deleteFileSafely(databaseFile);
        mvStore = new MVStore.Builder().fileName(databaseFile.toString()).compressHigh().autoCommitDisabled().open();
      }
      storeMap = mvStore.openMap("store");
    }
    catch (Exception e) {
      LOGGER.warn("Could not create TMM store database - '{}'", e.getMessage());
      Utils.deleteFileSafely(databaseFile);
      shutdown();
    }
  }

  /**
   * put a key/value pair into the store. Values under the {@code secret.} prefix will be encrypted on disk
   *
   * @param key
   *          the key
   * @param value
   *          the value
   */
  public void put(String key, String value) {
    init();

    if (storeMap == null) {
      // still null? we obviously have a problem opening the store - so just ignore that
      return;
    }

    try {
      String storedValue = value;
      if (key.endsWith(SECRET_POSTFIX)) {
        storedValue = AES_UTIL.encrypt(SALT, IV, key, value);
      }
      storeMap.put(key, storedValue);
      mvStore.commit();
    }
    catch (Exception e) {
      LOGGER.debug("could not write to the MVstore - '{}'", e.getMessage());
      Utils.deleteFileSafely(Paths.get(getStoreFolder(), STORE_DB));
      shutdown();
    }
  }

  /**
   * get the value for the given key
   *
   * @param key
   *          the key to search the value for
   * @return the value or null
   */
  public String get(String key) {
    init();

    if (storeMap == null) {
      // still null? we obviously have a problem opening the store - so just ignore that
      return null;
    }

    try {
      String value = storeMap.get(key);
      if (StringUtils.isBlank(value)) {
        return null;
      }
      if (key.endsWith(SECRET_POSTFIX)) {
        return AES_UTIL.decrypt(SALT, IV, key, value);
      }
      return value;
    }
    catch (Exception e) {
      LOGGER.debug("could not read the MVstore - '{}'", e.getMessage());
      Utils.deleteFileSafely(Paths.get(getStoreFolder(), STORE_DB));
      shutdown();
    }

    return null;
  }

  /**
   * get the plain value for the given key
   *
   * @param key
   *          the key to search the value for
   * @return the value or null
   */
  public String getPlain(String key) {
    init();

    if (storeMap == null) {
      // still null? we obviously have a problem opening the store - so just ignore that
      return null;
    }

    try {
      String value = storeMap.get(key);
      if (StringUtils.isBlank(value)) {
        return null;
      }
      return value;
    }
    catch (Exception e) {
      LOGGER.debug("could not read the MVstore - '{}'", e.getMessage());
      Utils.deleteFileSafely(Paths.get(getStoreFolder(), STORE_DB));
      shutdown();
    }

    return null;
  }

  /**
   * remove the given key from the store
   *
   * @param key
   *          the key to remove
   */
  public void remove(String key) {
    init();

    if (storeMap == null) {
      // still null? we obviously have a problem opening the store - so just ignore that
      return;
    }

    try {
      storeMap.remove(key);
      mvStore.commit();
    }
    catch (Exception e) {
      LOGGER.debug("could not write to the MVstore - '{}'", e.getMessage());
      Utils.deleteFileSafely(Paths.get(getStoreFolder(), STORE_DB));
      shutdown();
    }
  }

  /**
   * check whether the given key exists in the store
   *
   * @param key
   *          the key to check
   * @return true if the key exists
   */
  public boolean contains(String key) {
    return get(key) != null;
  }

  /**
   * get the value for the given key as a long<br>
   * if the value is not available or not parsable, this will return the given default value
   *
   * @param key
   *          the key to search the value for
   * @param defaultValue
   *          a default value, when key not found
   * @return the value or the default value
   */
  public long getAsLong(String key, long defaultValue) {
    String value = get(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }

    try {
      return Long.parseLong(value);
    }
    catch (Exception ignored) {
      // ignored
    }

    return defaultValue;
  }

  /**
   * get the value for the given key as an int<br>
   * if the value is not available or not parsable, this will return the given default value
   *
   * @param key
   *          the key to search the value for
   * @param defaultValue
   *          a default value, when key not found
   * @return the value or the default value
   */
  public int getAsInt(String key, int defaultValue) {
    String value = get(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }

    try {
      return Integer.parseInt(value);
    }
    catch (Exception ignored) {
      // ignored
    }

    return defaultValue;
  }

  /**
   * get the value for the given key as a boolean<br>
   * if the value is not available or not parsable, this will return the given default value
   *
   * @param key
   *          the key to search the value for
   * @param defaultValue
   *          a default value, when key not found
   * @return the value or the default value
   */
  public boolean getAsBoolean(String key, boolean defaultValue) {
    String value = get(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }

    return Boolean.parseBoolean(value);
  }

  /**
   * close the MVStore and release all resources
   */
  static synchronized void shutdown() {
    try {
      if (mvStore != null && !mvStore.isClosed()) {
        mvStore.close();
      }
    }
    catch (Exception e) {
      LOGGER.debug("could not close MVstore - deleting the store");
      Utils.deleteFileSafely(Paths.get(getStoreFolder(), STORE_DB));
    }
    finally {
      mvStore = null;
      storeMap = null;
    }
  }
}

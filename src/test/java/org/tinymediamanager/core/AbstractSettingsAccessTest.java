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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * The class {@link AbstractSettingsAccessTest} verifies startup failure behavior for unwritable settings storage.
 */
public class AbstractSettingsAccessTest {
  private static final String CONFIG_FILE = "dummy.json";

  @Rule
  public TemporaryFolder      tmpFolder   = new TemporaryFolder();

  /**
   * Tests that a non-directory folder path causes a fatal settings access exception.
   *
   * @throws Exception
   *           thrown if temporary test setup fails
   */
  @Test
  public void testGetInstanceFailsForNonDirectoryFolderPath() throws Exception {
    Path notAFolder = tmpFolder.newFile("not-a-folder").toPath();

    assertSettingsAccessFailure(notAFolder.toString(), CONFIG_FILE, "not a directory");
  }

  /**
   * Tests that an unwritable settings folder causes a fatal settings access exception.
   *
   * @throws Exception
   *           thrown if temporary test setup fails
   */
  @Test
  public void testGetInstanceFailsForUnwritableFolder() throws Exception {
    Path settingsFolder = tmpFolder.newFolder("settings-unwritable-folder").toPath();
    makeUnwritable(settingsFolder, true);

    try {
      assertFalse("Test folder should not be writable", Files.isWritable(settingsFolder));
      assertSettingsAccessFailure(settingsFolder.toString(), CONFIG_FILE, "Settings folder is not writable");
    }
    finally {
      assertThat(settingsFolder.toFile().setWritable(true, false)).isTrue();
    }
  }

  /**
   * Tests that a non-regular settings file path causes a fatal settings access exception.
   *
   * @throws Exception
   *           thrown if temporary test setup fails
   */
  @Test
  public void testGetInstanceFailsForNonRegularSettingsFile() throws Exception {
    Path settingsFolder = tmpFolder.newFolder("settings-non-regular-file").toPath();
    Files.createDirectory(settingsFolder.resolve(CONFIG_FILE));

    assertSettingsAccessFailure(settingsFolder.toString(), CONFIG_FILE, "not a regular file");
  }

  /**
   * Tests that an unreadable settings file causes a fatal settings access exception.
   *
   * @throws Exception
   *           thrown if temporary test setup fails
   */
  @Test
  public void testGetInstanceFailsForUnreadableFile() throws Exception {
    Path settingsFolder = tmpFolder.newFolder("settings-unreadable-file").toPath();
    Path settingsFile = settingsFolder.resolve(CONFIG_FILE);
    Files.writeString(settingsFile, "{}");
    makeUnreadable(settingsFile);

    try {
      assertFalse("Test file should not be readable", Files.isReadable(settingsFile));
      assertSettingsAccessFailure(settingsFolder.toString(), CONFIG_FILE, "not readable");
    }
    finally {
      assertThat(settingsFile.toFile().setReadable(true, false)).isTrue();
      assertThat(settingsFile.toFile().setWritable(true, false)).isTrue();
    }
  }

  /**
   * Tests that an unwritable settings file causes a fatal settings access exception.
   *
   * @throws Exception
   *           thrown if temporary test setup fails
   */
  @Test
  public void testGetInstanceFailsForUnwritableFile() throws Exception {
    Path settingsFolder = tmpFolder.newFolder("settings-unwritable-file").toPath();
    Path settingsFile = settingsFolder.resolve(CONFIG_FILE);
    Files.writeString(settingsFile, "{}");
    makeUnwritable(settingsFile, false);

    try {
      assertFalse("Test file should not be writable", Files.isWritable(settingsFile));
      assertSettingsAccessFailure(settingsFolder.toString(), CONFIG_FILE, "not writable");
    }
    finally {
      assertThat(settingsFile.toFile().setWritable(true, false)).isTrue();
    }
  }

  /**
   * Tests that settings loading works when the settings file does not exist yet.
   *
   * @throws Exception
   *           thrown if temporary test setup fails
   */
  @Test
  public void testGetInstancePassesWhenConfigFileDoesNotExist() throws Exception {
    Path settingsFolder = tmpFolder.newFolder("settings-no-file").toPath();

    AbstractSettings instance = AbstractSettings.getInstance(settingsFolder.toString(), CONFIG_FILE, DummySettings.class);

    assertThat(instance).isNotNull();
    assertThat(instance).isInstanceOf(DummySettings.class);
    assertThat(instance.isNewConfig()).isTrue();
  }

  /**
   * Tests that settings loading works when a readable/writable regular config file exists.
   *
   * @throws Exception
   *           thrown if temporary test setup fails
   */
  @Test
  public void testGetInstancePassesWhenConfigFileIsAccessible() throws Exception {
    Path settingsFolder = tmpFolder.newFolder("settings-valid-file").toPath();
    Path settingsFile = settingsFolder.resolve(CONFIG_FILE);
    Files.writeString(settingsFile, "{\"someValue\":\"loaded\"}");

    AbstractSettings instance = AbstractSettings.getInstance(settingsFolder.toString(), CONFIG_FILE, DummySettings.class);

    assertThat(instance).isInstanceOf(DummySettings.class);
    assertThat(((DummySettings) instance).getSomeValue()).isEqualTo("loaded");
    assertThat(instance.isNewConfig()).isFalse();
  }

  /**
   * Asserts that a settings access failure is thrown with an expected message fragment.
   *
   * @param folder
   *          the settings folder path
   * @param filename
   *          the settings filename
   * @param expectedMessagePart
   *          the expected message fragment
   */
  private void assertSettingsAccessFailure(String folder, String filename, String expectedMessagePart) {
    try {
      AbstractSettings.getInstance(folder, filename, DummySettings.class);
      fail("Expected SettingsAccessException for: " + folder + "/" + filename);
    }
    catch (SettingsAccessException e) {
      assertThat(e.getMessage()).contains(expectedMessagePart);
    }
  }

  /**
   * Makes the given path non-writable and skips the test if the filesystem cannot apply the permission.
   *
   * @param path
   *          the path to modify
   * @param isDirectory
   *          true if the path is a directory
   */
  private void makeUnwritable(Path path, boolean isDirectory) {
    if (isDirectory) {
      Assume.assumeTrue("Could not set test folder to non-writable", path.toFile().setWritable(false, false));
    }
    else {
      Assume.assumeTrue("Could not set test file to non-writable", path.toFile().setWritable(false, false));
    }

    // root users often still have write access despite permission bits
    Assume.assumeFalse("Current user still has write access to test path", Files.isWritable(path));
  }

  /**
   * Makes the given file unreadable and skips the test if the filesystem cannot apply the permission.
   *
   * @param path
   *          the file path to modify
   */
  private void makeUnreadable(Path path) {
    Set<String> views = FileSystems.getDefault().supportedFileAttributeViews();
    Assume.assumeTrue("POSIX file attributes are required for unreadable-file test", views.contains("posix"));

    Assume.assumeTrue("Could not set test file to unreadable", path.toFile().setReadable(false, false));
    // Keep the file writable to specifically hit the readability guard.
    Assume.assumeTrue("Could not keep test file writable", path.toFile().setWritable(true, false));

    // root users often still have read access despite permission bits
    Assume.assumeFalse("Current user still has read access to test file", Files.isReadable(path));
  }

  /**
   * The class {@link DummySettings} provides a minimal settings implementation for access checks.
   */
  public static class DummySettings extends AbstractSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummySettings.class);

    private String              someValue;

    /**
     * Gets the someValue property.
     *
     * @return the someValue property
     */
    public String getSomeValue() {
      return someValue;
    }

    /**
     * Sets the someValue property.
     *
     * @param someValue
     *          the someValue property
     */
    public void setSomeValue(String someValue) {
      this.someValue = someValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ObjectWriter createObjectWriter() {
      return objectMapper.writerFor(DummySettings.class);
    }

    @Override
    protected void upgradeSettings() {
      // no-op for this test class
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void afterLoading() {
      // no-op for this test class
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeDefaultSettings() {
      // no-op for this test class
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConfigFilename() {
      return CONFIG_FILE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Logger getLogger() {
      return LOGGER;
    }
  }
}

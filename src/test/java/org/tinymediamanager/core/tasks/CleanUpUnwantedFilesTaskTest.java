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
package org.tinymediamanager.core.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.movie.BasicMovieTest;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;

/**
 * The class {@link CleanUpUnwantedFilesTaskTest} verifies the cleanup of unwanted leftover files - deletion of the configured file types, the dry run
 * mode and the guards against deleting too much.
 */
public class CleanUpUnwantedFilesTaskTest extends BasicMovieTest {

  /**
   * Sets up a clean temp data source without the trash/backup folder to keep the assertions simple.
   *
   * @throws Exception
   *           thrown if the setup fails
   */
  @Before
  public void setupCleanup() throws Exception {
    Settings.getInstance().setEnableTrash(false);
  }

  /**
   * Tests that a dry run keeps every file in place but still counts the unwanted ones.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void dryRunKeepsAllFiles() throws Exception {
    Movie movie = createMovieOnDisk("Dry Run Movie");
    Path folder = Path.of(movie.getPath());
    createMovieFiles(folder);

    CleanUpUnwantedFilesTask task = new CleanUpUnwantedFilesTask(List.of(movie), true);
    task.run();

    assertThat(folder.resolve("movie.mkv")).exists();
    assertThat(folder.resolve("movie.nfo")).exists();
    assertThat(folder.resolve("subtitle.txt")).exists();
    assertThat(folder.resolve("shortcut.url")).exists();
    assertThat(folder.resolve("index.html")).exists();
    assertThat(folder.resolve("checksum.sfv")).exists();

    // the unwanted files are counted even in dry run mode
    assertThat(task.getProgressDone()).isEqualTo(4);
  }

  /**
   * Tests that the default cleanup file types are deleted while media and NFO files are kept.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void deletesUnwantedFilesAndKeepsMedia() throws Exception {
    Movie movie = createMovieOnDisk("Cleanup Movie");
    Path folder = Path.of(movie.getPath());
    createMovieFiles(folder);

    CleanUpUnwantedFilesTask task = new CleanUpUnwantedFilesTask(List.of(movie), false);
    task.run();

    assertThat(folder.resolve("subtitle.txt")).doesNotExist();
    assertThat(folder.resolve("shortcut.url")).doesNotExist();
    assertThat(folder.resolve("index.html")).doesNotExist();
    assertThat(folder.resolve("checksum.sfv")).doesNotExist();

    assertThat(folder.resolve("movie.mkv")).exists();
    assertThat(folder.resolve("movie.nfo")).exists();
    assertThat(folder).exists();

    // all 4 unwanted files have been handled
    assertThat(task.getProgressDone()).isEqualTo(4);
  }

  /**
   * Tests that additionally configured cleanup file types are honored.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void respectsCustomCleanupFileType() throws Exception {
    Settings.getInstance().addCleanupFileType(".log$");

    Movie movie = createMovieOnDisk("Custom Type Movie");
    Path folder = Path.of(movie.getPath());
    Files.createFile(folder.resolve("debug.log"));
    Files.createFile(folder.resolve("movie.mkv"));

    new CleanUpUnwantedFilesTask(List.of(movie), false).run();

    assertThat(folder.resolve("debug.log")).doesNotExist();
    assertThat(folder.resolve("movie.mkv")).exists();
  }

  /**
   * Tests that video files are never deleted, even when their name matches a cleanup regex.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void refusesToDeleteVideoFiles() throws Exception {
    Settings.getInstance().addCleanupFileType("^movie\\.mkv$");

    Movie movie = createMovieOnDisk("Video Guard Movie");
    Path folder = Path.of(movie.getPath());
    Files.createFile(folder.resolve("movie.mkv"));

    new CleanUpUnwantedFilesTask(List.of(movie), false).run();

    assertThat(folder.resolve("movie.mkv")).exists();
  }

  /**
   * Tests that the root folder of an entity is never deleted, even when it matches a cleanup regex.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void neverDeletesTheEntityRootFolder() throws Exception {
    Movie movie = createMovieOnDisk("Protected Root Movie");
    Path folder = Path.of(movie.getPath());
    createMovieFiles(folder);

    // matches the entity root folder itself
    Settings.getInstance().addCleanupFileType("Protected Root Movie$");

    new CleanUpUnwantedFilesTask(List.of(movie), false).run();

    assertThat(folder).exists();
    assertThat(folder.resolve("movie.mkv")).exists();
    assertThat(folder.resolve("subtitle.txt")).doesNotExist();
  }

  /**
   * Tests that files in a folder shared by several entities (e.g. shows/episodes) are handled only once.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void handlesSharedFolderOnlyOnce() throws Exception {
    Path folder = getWorkFolder().resolve("Shared Folder");
    Files.createDirectories(folder);
    Files.createFile(folder.resolve("subtitle.txt"));

    Movie first = createMovieAt("Shared First", folder);
    Movie second = createMovieAt("Shared Second", folder);

    CleanUpUnwantedFilesTask task = new CleanUpUnwantedFilesTask(List.of(first, second), false);
    task.run();

    assertThat(folder.resolve("subtitle.txt")).doesNotExist();
    // one single file - handled exactly once
    assertThat(task.getProgressDone()).isEqualTo(1);
  }

  private Movie createMovieOnDisk(String title) throws Exception {
    Path folder = getWorkFolder().resolve(title);
    Files.createDirectories(folder);
    return createMovieAt(title, folder);
  }

  private Movie createMovieAt(String title, Path folder) {
    Movie movie = new Movie();
    movie.setTitle(title);
    movie.setDataSource(getWorkFolder().toString());
    movie.setPath(folder.toString());
    MovieModuleManager.getInstance().getMovieList().addMovie(movie);
    return movie;
  }

  private void createMovieFiles(Path folder) throws Exception {
    Files.createFile(folder.resolve("movie.mkv"));
    Files.createFile(folder.resolve("movie.nfo"));
    Files.createFile(folder.resolve("subtitle.txt"));
    Files.createFile(folder.resolve("shortcut.url"));
    Files.createFile(folder.resolve("index.html"));
    Files.createFile(folder.resolve("checksum.sfv"));
  }
}

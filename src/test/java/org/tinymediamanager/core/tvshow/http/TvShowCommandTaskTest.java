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
package org.tinymediamanager.core.tvshow.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.http.AbstractCommandHandler;
import org.tinymediamanager.core.threading.TmmTaskHandle.TaskState;
import org.tinymediamanager.core.tvshow.BasicTvShowTest;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;

/**
 * The class {@link TvShowCommandTaskTest} checks the TV show HTTP command task end-to-end with locally safe actions (the {@code cleanup} action and
 * the scope resolution) - no network / external services are involved.
 */
public class TvShowCommandTaskTest extends BasicTvShowTest {

  /**
   * Disables the trash folder so deleted files are really gone.
   */
  @Before
  public void setupCommandTask() {
    Settings.getInstance().setEnableTrash(false);
  }

  /**
   * Tests that a {@code cleanup} command with scope {@code all} deletes the unwanted files of all shows.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void cleanupScopeAllDeletesUnwantedFiles() throws Exception {
    TvShow show = createShowWithJunk("Scratched Show");

    new TvShowCommandTask(List.of(cleanupCommand("all"))).run();

    assertThat(showJunk(show)).doesNotExist();
    assertThat(showFolder(show).resolve("episode-s01e01.mkv")).exists();
  }

  /**
   * Tests that {@code dryRun=true} is honored by the cleanup command.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void cleanupDryRunKeepsFiles() throws Exception {
    TvShow show = createShowWithJunk("Dry Run Show");

    AbstractCommandHandler.Command command = cleanupCommand("all");
    command.args.put("dryRun", "true");

    new TvShowCommandTask(List.of(command)).run();

    assertThat(showJunk(show)).exists();
  }

  /**
   * Tests that the {@code path} scope only processes the shows of the given paths.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void cleanupScopePathOnlyProcessesGivenPaths() throws Exception {
    TvShow selected = createShowWithJunk("Selected Show");
    TvShow other = createShowWithJunk("Other Show");

    AbstractCommandHandler.Command command = cleanupCommand("path");
    command.scope.args = new String[] { showFolder(selected).toString() };

    new TvShowCommandTask(List.of(command)).run();

    assertThat(showJunk(selected)).doesNotExist();
    assertThat(showJunk(other)).exists();
  }

  /**
   * Tests that a {@code cleanup} command without a scope name defaults to the {@code new} scope - and therefore processes nothing when no
   * {@code update} command ran before.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void cleanupWithoutUpdateAndBlankScopeProcessesNothing() throws Exception {
    TvShow show = createShowWithJunk("Blank Scope Show");

    new TvShowCommandTask(List.of(cleanupCommand(null))).run();

    assertThat(showJunk(show)).exists();
  }

  /**
   * Tests that locked shows are filtered out of every scope.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void cleanupSkipsLockedShows() throws Exception {
    TvShow locked = createShowWithJunk("Locked Show");
    locked.setLocked(true);
    TvShow unlocked = createShowWithJunk("Unlocked Show");

    new TvShowCommandTask(List.of(cleanupCommand("all"))).run();

    assertThat(showJunk(locked)).exists();
    assertThat(showJunk(unlocked)).doesNotExist();
  }

  /**
   * Tests that an unknown action is simply ignored and the task finishes without an error.
   *
   * @throws Exception
   *           thrown on I/O errors
   */
  @Test
  public void unknownActionIsIgnored() throws Exception {
    TvShow show = createShowWithJunk("Untouched Show");

    AbstractCommandHandler.Command command = new AbstractCommandHandler.Command();
    command.action = "definitelyNotAnAction";
    command.scope.name = "all";

    TvShowCommandTask task = new TvShowCommandTask(List.of(command));
    task.run();

    assertThat(task.getState()).isEqualTo(TaskState.FINISHED);
    assertThat(showJunk(show)).exists();
  }

  private AbstractCommandHandler.Command cleanupCommand(String scopeName) {
    AbstractCommandHandler.Command command = new AbstractCommandHandler.Command();
    command.action = "cleanup";
    command.scope.name = scopeName;
    return command;
  }

  private TvShow createShowWithJunk(String title) throws Exception {
    createFakeShow(title);

    TvShow show = TvShowModuleManager.getInstance()
        .getTvShowList()
        .getTvShows()
        .stream()
        .filter(s -> title.equals(s.getTitle()))
        .findFirst()
        .orElseThrow();

    Path folder = showFolder(show);
    Files.createDirectories(folder);
    Files.createFile(folder.resolve("episode-s01e01.mkv"));
    Files.createFile(folder.resolve("leftover.txt"));

    return show;
  }

  private Path showFolder(TvShow show) {
    return Path.of(show.getPath());
  }

  private Path showJunk(TvShow show) {
    return showFolder(show).resolve("leftover.txt");
  }
}

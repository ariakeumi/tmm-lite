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
package org.tinymediamanager.core.movie.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;
import org.tinymediamanager.core.http.AbstractCommandHandler;
import org.tinymediamanager.core.movie.BasicMovieTest;
import org.tinymediamanager.core.threading.TmmTaskHandle.TaskState;

/**
 * The class {@link MovieSetCommandTaskTest} is a smoke test for the new movie set command task - all its actions (scraping, artwork, Kodi, Trakt)
 * need external services, so only the wiring itself is checked here.
 */
public class MovieSetCommandTaskTest extends BasicMovieTest {

  /**
   * Tests that a task with an empty command list finishes cleanly.
   */
  @Test
  public void emptyCommandsFinishWithoutError() {
    MovieSetCommandTask task = new MovieSetCommandTask(List.of());
    task.run();

    assertThat(task.getState()).isEqualTo(TaskState.FINISHED);
  }

  /**
   * Tests that an unknown action is simply ignored and the task finishes without an error.
   */
  @Test
  public void unknownActionIsIgnored() {
    AbstractCommandHandler.Command command = new AbstractCommandHandler.Command();
    command.action = "definitelyNotAnAction";
    command.scope.name = "all";

    MovieSetCommandTask task = new MovieSetCommandTask(List.of(command));
    task.run();

    assertThat(task.getState()).isEqualTo(TaskState.FINISHED);
  }
}

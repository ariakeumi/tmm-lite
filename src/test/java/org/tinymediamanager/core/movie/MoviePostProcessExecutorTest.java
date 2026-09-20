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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.threading.TmmTaskHandle.TaskState;

/**
 * The class {@link MoviePostProcessExecutorTest} tests the abort-on-failure behavior of the post process executor.
 *
 * @author Manuel Laggner
 */
public class MoviePostProcessExecutorTest extends BasicMovieTest {

  @Test
  public void abortOnFailureAbortsTheQueue() throws Exception {
    List<Movie> movies = createMovies(3);

    MoviePostProcessExecutor executor = new MoviePostProcessExecutor(createPostProcess("exit 1", true), movies);
    executor.run();

    assertThat(executor.getExecutionResults()).hasSize(1);
    assertThat(executor.getExecutionResults().get(0).entityName()).isEqualTo("movie 1");
    assertThat(executor.getState()).isEqualTo(TaskState.FAILED);
  }

  @Test
  public void continueOnFailureByDefault() throws Exception {
    List<Movie> movies = createMovies(3);

    MoviePostProcessExecutor executor = new MoviePostProcessExecutor(createPostProcess("exit 1", false), movies);
    executor.run();

    assertThat(executor.getExecutionResults()).hasSize(3);
    assertThat(executor.getState()).isEqualTo(TaskState.FINISHED);
  }

  @Test
  public void keepProcessingWhenAllSucceed() throws Exception {
    List<Movie> movies = createMovies(3);

    MoviePostProcessExecutor executor = new MoviePostProcessExecutor(createPostProcess("exit 0", true), movies);
    executor.run();

    assertThat(executor.getExecutionResults()).hasSize(3);
    assertThat(executor.getState()).isEqualTo(TaskState.FINISHED);
  }

  private PostProcess createPostProcess(String command, boolean abortOnFailure) {
    PostProcess postProcess = new PostProcess();
    postProcess.setName("test post process");
    postProcess.setPath("");
    postProcess.setCommand(command);
    postProcess.setAbortOnFailure(abortOnFailure);
    return postProcess;
  }

  private List<Movie> createMovies(int count) throws Exception {
    List<Movie> movies = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      String title = "movie " + i;
      Path folder = getWorkFolder().resolve(title);
      Files.createDirectories(folder);

      Movie movie = new Movie();
      movie.setTitle(title);
      movie.setDataSource(getWorkFolder().toString());
      movie.setPath(folder.toString());
      movies.add(movie);
    }
    return movies;
  }
}

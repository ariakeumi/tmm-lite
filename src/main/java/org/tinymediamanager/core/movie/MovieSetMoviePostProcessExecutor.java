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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.movie.entities.Movie;

/**
 * the class {@link MovieSetMoviePostProcessExecutor} executes post process steps for movie sets
 * 
 * @author Manuel Laggner
 */
public class MovieSetMoviePostProcessExecutor extends MoviePostProcessExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(MovieSetMoviePostProcessExecutor.class);

  public MovieSetMoviePostProcessExecutor(PostProcess postProcess, List<Movie> movies) {
    super(postProcess, movies);
  }

  @Override
  public void execute() {
    for (MediaEntity entity : entities) {
      if (!(entity instanceof Movie movie)) {
        continue;
      }

      LOGGER.info("Executing post process '{}' for movie '{}'", postProcess.getName(), movie.getTitle());
      String[] command = substituteMovieTokens(movie);
      try {
        executeCommand(command, movie);
        LOGGER.info("Successfully executed post process '{}' for movie '{}'", postProcess.getName(), movie.getTitle());
      }
      catch (Exception e) {
        if (handleExecutionFailure(movie, e)) {
          return;
        }
        // otherwise already logged in executeCommand
      }
    }
  }
}

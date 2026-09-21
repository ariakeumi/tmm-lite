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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.PostProcessExecutor;
import org.tinymediamanager.core.jmte.JmteUtils;
import org.tinymediamanager.core.jmte.TmmModelAdaptor;
import org.tinymediamanager.core.movie.entities.Movie;

import com.floreysoft.jmte.Engine;

/**
 * the class {@link MoviePostProcessExecutor} executes post process steps for movies
 * 
 * @author Wolfgang Janes
 */
public class MoviePostProcessExecutor extends PostProcessExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(MoviePostProcessExecutor.class);

  private final Engine        engine;
  private final int           itemCount;

  public MoviePostProcessExecutor(PostProcess postProcess, List<Movie> movies) {
    super(postProcess, movies);
    // copy to make it immutable
    MovieRenamerProfile renamerProfile = new MovieRenamerProfile(MovieModuleManager.getInstance().getSettings().getDefaultRenamerProfile());
    engine = MovieRenamer.createEngine(renamerProfile);
    engine.setModelAdaptor(new TmmModelAdaptor());
    itemCount = movies.size();
  }

  @Override
  protected void execute() {
    int index = 0;

    for (Movie movie : getMovies()) {
      index++;

      LOGGER.info("Executing post process '{}' for movie '{}'", postProcess.getName(), movie.getTitle());
      String[] command = substituteMovieTokens(movie, index);
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

  protected List<Movie> getMovies() {
    return entities.stream().filter(e -> e instanceof Movie).map(e -> (Movie) e).toList();
  }

  protected String[] substituteMovieTokens(Movie movie, int index) {
    Map<String, Object> root = new HashMap<>();
    root.put("movie", movie);
    root.put("itemCount", itemCount);
    root.put("index", index);

    if (postProcess.getPath() == null || postProcess.getPath().isEmpty()) {
      // scripting mode - transform as single string
      String transformed = engine.transform(JmteUtils.morphTemplate(postProcess.getCommand(), MovieRenamer.getTokenMap()), root);
      return new String[] { transformed };
    }
    else {
      // parameter mode - transform every line to have separated params
      String[] splitted = postProcess.getCommand().split("\\n");
      for (int i = 0; i < splitted.length; i++) {
        splitted[i] = engine.transform(JmteUtils.morphTemplate(splitted[i], MovieRenamer.getTokenMap()), root);
      }
      return splitted;
    }
  }
}

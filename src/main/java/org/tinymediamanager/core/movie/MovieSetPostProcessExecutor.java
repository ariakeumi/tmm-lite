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
import org.tinymediamanager.core.jmte.TmmModelAdaptor;
import org.tinymediamanager.core.movie.entities.MovieSet;

import com.floreysoft.jmte.Engine;

/**
 * the class {@link MovieSetPostProcessExecutor} executes post process steps for movie sets
 * 
 * @author Manuel Laggner
 */
public class MovieSetPostProcessExecutor extends PostProcessExecutor {
  private static final Logger       LOGGER = LoggerFactory.getLogger(MovieSetPostProcessExecutor.class);

  private final MovieRenamerProfile renamerProfile;
  private final int                 itemCount;

  /**
   * Creates a new movie set post-process executor.
   *
   * @param postProcess
   *          the post-process definition
   * @param movieSets
   *          the movie sets to process
   */
  public MovieSetPostProcessExecutor(PostProcess postProcess, List<MovieSet> movieSets) {
    super(postProcess, movieSets);
    // copy to make it immutable
    this.renamerProfile = new MovieRenamerProfile(MovieModuleManager.getInstance().getSettings().getDefaultRenamerProfile());
    this.itemCount = getMovieSets().size();
  }

  @Override
  public void execute() {
    int index = 0;
    for (MovieSet movieSet : getMovieSets()) {
      index++;

      LOGGER.info("Executing post process '{}' for movie set '{}'", postProcess.getName(), movieSet.getTitle());
      String[] command = substituteMovieSetTokens(movieSet, index);
      try {
        executeCommand(command, movieSet);
        LOGGER.info("Successfully executed post process '{}' for movie set '{}'", postProcess.getName(), movieSet.getTitle());
      }
      catch (Exception e) {
        if (handleExecutionFailure(movieSet, e)) {
          return;
        }
        // otherwise already logged in executeCommand
      }
    }
  }

  private List<MovieSet> getMovieSets() {
    return entities.stream().filter(e -> e instanceof MovieSet).map(e -> (MovieSet) e).toList();
  }

  private String[] substituteMovieSetTokens(MovieSet movieSet, int index) {
    Engine engine = MovieRenamer.createEngine(renamerProfile);
    engine.setModelAdaptor(new TmmModelAdaptor());

    Map<String, Object> root = new HashMap<>();
    root.put("movieSet", movieSet);
    root.put("itemCount", itemCount);
    root.put("index", index);

    if (postProcess.getPath() == null || postProcess.getPath().isEmpty()) {
      // scripting mode - transform as single string
      String transformed = engine.transform(postProcess.getCommand(), root);
      return new String[] { transformed };
    }
    else {
      // parameter mode - transform every line to have separated params
      String[] splitted = postProcess.getCommand().split("\\n");
      for (int i = 0; i < splitted.length; i++) {
        splitted[i] = engine.transform(splitted[i], root);
      }
      return splitted;
    }
  }
}

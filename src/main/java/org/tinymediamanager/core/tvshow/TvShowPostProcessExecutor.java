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
package org.tinymediamanager.core.tvshow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.PostProcessExecutor;
import org.tinymediamanager.core.jmte.JmteUtils;
import org.tinymediamanager.core.jmte.TmmModelAdaptor;
import org.tinymediamanager.core.tvshow.entities.TvShow;

import com.floreysoft.jmte.Engine;

/**
 * the class {@link TvShowPostProcessExecutor} executes post process steps for movies
 *
 * @author Wolfgang Janes
 */
public class TvShowPostProcessExecutor extends PostProcessExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(TvShowPostProcessExecutor.class);

  private final Engine        engine;
  private final int           itemCount;

  public TvShowPostProcessExecutor(PostProcess postProcess, List<TvShow> tvShows) {
    super(postProcess, tvShows);
    // copy to make it immutable
    TvShowRenamerProfile renamerProfile = new TvShowRenamerProfile(TvShowModuleManager.getInstance().getSettings().getDefaultRenamerProfile());
    engine = TvShowRenamer.createEngine(renamerProfile);
    engine.setModelAdaptor(new TmmModelAdaptor());
    itemCount = getTvShows().size();
  }

  @Override
  protected void execute() {
    int index = 0;
    for (TvShow tvShow : getTvShows()) {
      index++;

      LOGGER.info("Executing post process '{}' for TV show '{}'", postProcess.getName(), tvShow.getTitle());

      Map<String, Object> mappings = new HashMap<>();
      mappings.put("tvShow", tvShow);
      mappings.put("itemCount", itemCount);
      mappings.put("index", index);

      String[] command = substituteTokens(mappings);

      try {
        executeCommand(command, tvShow);
        LOGGER.info("Successfully executed post process '{}' for TV show '{}'", postProcess.getName(), tvShow.getTitle());
      }
      catch (Exception e) {
        if (handleExecutionFailure(tvShow, e)) {
          return;
        }
        // otherwise already logged in executeCommand
      }
    }
  }

  private List<TvShow> getTvShows() {
    return entities.stream().filter(e -> e instanceof TvShow).map(e -> (TvShow) e).toList();
  }

  private String[] substituteTokens(Map<String, Object> mappings) {
    if (postProcess.getPath() == null || postProcess.getPath().isEmpty()) {
      // scripting mode - transform as single string
      String transformed = engine.transform(JmteUtils.morphTemplate(postProcess.getCommand(), TvShowRenamer.getTokenMap()), mappings);
      return new String[] { transformed };
    }
    else {
      // parameter mode - transform every line to have separated params
      String[] splitted = postProcess.getCommand().split("\\n");
      for (int i = 0; i < splitted.length; i++) {
        splitted[i] = engine.transform(JmteUtils.morphTemplate(splitted[i], TvShowRenamer.getTokenMap()), mappings);
      }
      return splitted;
    }
  }
}

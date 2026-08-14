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
package org.tinymediamanager.ui.movies.actions;

import java.awt.event.ActionEvent;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.thirdparty.simkl.MovieSyncSimklTask;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.actions.TmmAction;

/**
 * The class {@link MovieSyncSimklAction}. To synchronize your movie library/watched state with Simkl.com
 * 
 * @author Manuel Laggner
 */
public class MovieSyncSimklAction extends TmmAction {
  public MovieSyncSimklAction() {
    putValue(NAME, TmmResourceBundle.getString("movie.syncsimkl"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movie.syncsimkl.desc"));
    putValue(SMALL_ICON, IconManager.SYNC);
    putValue(LARGE_ICON_KEY, IconManager.SYNC);
  }

  @Override
  protected void processAction(ActionEvent e) {
    MovieSyncSimklTask task = new MovieSyncSimklTask(MovieModuleManager.getInstance().getMovieList().getMovies());
    task.setSyncWatched(true);

    TmmTaskManager.getInstance().addUnnamedTask(task);
  }
}

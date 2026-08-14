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
package org.tinymediamanager.thirdparty.simkl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.util.MediaIdUtil;

/**
 * Sync your data with Simkl.com
 * 
 * @author Manuel Laggner
 */
public class TvShowSyncSimklTask extends TmmTask {
  private static final Logger LOGGER            = LoggerFactory.getLogger(TvShowSyncSimklTask.class);

  private final List<TvShow>  tvShows           = new ArrayList<>();

  private boolean             syncWatched       = false;
  private boolean             removeFromWatched = false;

  public TvShowSyncSimklTask(List<TvShow> tvShows) {
    super(TmmResourceBundle.getString("simkl.sync"), 0, TaskType.BACKGROUND_TASK);
    this.tvShows.addAll(tvShows);
  }

  public void setSyncWatched(boolean value) {
    this.syncWatched = value;
  }

  public void setRemoveFromWatched(boolean value) {
    this.removeFromWatched = value;
  }

  @Override
  protected void doInBackground() {
    if (!isFeatureEnabled()) {
      return;
    }

    if (!syncNeeded(tvShows)) {
      return;
    }

    Simkl simkl = Simkl.getInstance();

    if (syncWatched) {
      publishState(TmmResourceBundle.getString("simkl.sync.tvshow.watched"), 0);
      try {
        simkl.syncTvShowWatched(tvShows);
      }
      catch (Exception e) {
        LOGGER.error("Could not sync watched to Simkl - '{}'", e.getMessage());
      }
    }

    if (removeFromWatched) {
      publishState(TmmResourceBundle.getString("simkl.sync.tvshow.watched.remove"), 0);
      try {
        simkl.removeFromSimklTvShowWatched(tvShows);
      }
      catch (Exception e) {
        LOGGER.error("Could not remove watched from Simkl - '{}'", e.getMessage());
      }
    }
  }

  private boolean syncNeeded(List<TvShow> tvShows) {
    for (TvShow show : tvShows) {
      if (show.getTmdbId() > 0) {
        return true;
      }
      if (MediaIdUtil.isValidImdbId(show.getImdbId())) {
        return true;
      }
      if (show.getIdAsInt(MediaMetadata.TVDB) > 0) {
        return true;
      }
    }
    return false;
  }
}

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
package org.tinymediamanager.core.tvshow.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowRenamer;
import org.tinymediamanager.core.tvshow.TvShowRenamerProfile;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;

/**
 * The class TvShowRenameTask. rename all chosen TV shows
 *
 * @author Manuel Laggner
 */
public class TvShowRenameTask extends TmmThreadPool {
  private static final Logger        LOGGER           = LoggerFactory.getLogger(TvShowRenameTask.class);

  private final List<TvShow>         tvShowsToRename  = new ArrayList<>();
  private final List<TvShowEpisode>  episodesToRename = new ArrayList<>();
  private final TvShowRenamerProfile profile;

  /**
   * Rename just the given {@link TvShow} root (and {@link org.tinymediamanager.core.entities.MediaFile}s)
   *
   * @param tvShowToRename
   *          the {@link TvShow} to rename
   * @param profile
   *          the {@link TvShowRenamerProfile} to use for renaming
   */
  public TvShowRenameTask(TvShow tvShowToRename, TvShowRenamerProfile profile) {
    this(Collections.singletonList(tvShowToRename), null, profile);
  }

  /**
   * Rename just the given {@link TvShow} roots (and {@link org.tinymediamanager.core.entities.MediaFile}s)
   *
   * @param tvShowsToRename
   *          the {@link TvShow}s to rename
   * @param profile
   *          the {@link TvShowRenamerProfile} to use for renaming
   */
  public TvShowRenameTask(Collection<TvShow> tvShowsToRename, TvShowRenamerProfile profile) {
    this(tvShowsToRename, null, profile);
  }

  /**
   * Rename {@link TvShow}s and {@link TvShowEpisode}s together
   *
   * @param tvShowsToRename
   *          the {@link TvShow}s to rename (only TV show MFs and root folder)
   * @param episodesToRename
   *          the {@link TvShowEpisode}s to rename
   * @param profile
   *          the {@link TvShowRenamerProfile} to use for renaming
   */
  public TvShowRenameTask(Collection<TvShow> tvShowsToRename, Collection<TvShowEpisode> episodesToRename, TvShowRenamerProfile profile) {
    super(TmmResourceBundle.getString("tvshow.rename"));
    this.profile = new TvShowRenamerProfile(profile);

    if (tvShowsToRename != null) {
      this.tvShowsToRename.addAll(tvShowsToRename);
    }

    if (episodesToRename != null) {
      this.episodesToRename.addAll(episodesToRename);
    }
  }

  @Override
  protected void doInBackground() {
    try {
      LOGGER.info("Renaming '{}' TV shows / '{}' episodes", tvShowsToRename.size(), episodesToRename.size());

      initThreadPool(1, "rename");

      // 1. episodes first (to get the right season folders for moving season artwork)
      for (TvShowEpisode tvEpisodesToRename : episodesToRename) {
        if (cancel) {
          break;
        }
        submitTask(new RenameEpisodeTask(tvEpisodesToRename, profile));
      }

      waitForCompletionOrCancel();
      if (cancel) {
        return;
      }

      // 2. rename TV show root
      for (TvShow tvShow : tvShowsToRename) {
        TvShowRenamer.renameTvShow(tvShow, profile); // rename root and artwork and update ShowMFs
      }

      LOGGER.info("Finished renaming TV shows/episodes - took {} ms", getRuntime());
    }
    catch (Exception e) {
      LOGGER.error("Could not rename TV shows - '{}'", e.getMessage());
      MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "Settings.renamer", "message.renamer.threadcrashed"));
    }
  }

  /**
   * ThreadpoolWorker to work off ONE episode
   */
  private static class RenameEpisodeTask implements Callable<Object> {
    private final TvShowEpisode        episode;
    private final TvShowRenamerProfile renamerProfile;

    public RenameEpisodeTask(TvShowEpisode episode, TvShowRenamerProfile renamerProfile) {
      this.episode = episode;
      this.renamerProfile = renamerProfile;
    }

    @Override
    public String call() {
      TvShowRenamer.renameEpisode(episode, renamerProfile);
      return episode.getTitle();
    }
  }

  @Override
  public void callback(Object obj) {
    publishState((String) obj, progressDone);
  }
}

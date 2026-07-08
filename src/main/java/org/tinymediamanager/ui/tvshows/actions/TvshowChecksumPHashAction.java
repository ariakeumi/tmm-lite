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
package org.tinymediamanager.ui.tvshows.actions;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskHandle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.util.VideoPHash;
import org.tinymediamanager.thirdparty.FFmpeg;
import org.tinymediamanager.thirdparty.FFprobe;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel.SelectedObjects;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

public class TvshowChecksumPHashAction extends TmmAction {
  private static final Logger LOGGER           = LoggerFactory.getLogger(TvshowChecksumPHashAction.class);
  private static final long   serialVersionUID = 1L;

  public TvshowChecksumPHashAction() {
    putValue(NAME, TmmResourceBundle.getString("checksum.phash.calculate"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("checksum.phash.calculate"));
  }

  @Override
  protected void processAction(ActionEvent e) {
    // check prequisites early
    if (!FFprobe.isAvailable() && !FFmpeg.isAvailable()) {
      LOGGER.warn("Would have executed perceptual hash generation - unfortunately, FFprobe/FFmpeg could not be found.");
      MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, "task.phash", "message.ard.ffmpegmissing"));
      return;
    }

    SelectedObjects sel = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects(false, false);

    TmmTask task = new TmmTask(TmmResourceBundle.getString("checksum.crc32.calculate"), sel.getEpisodes().size(),
        TmmTaskHandle.TaskType.BACKGROUND_TASK) {
      @Override
      protected void doInBackground() {
        Set<TvShowEpisode> selectedEpisodes = sel.getEpisodesRecursive(); // all EPs, even when show/Season clicked!
        int i = 0;
        for (TvShowEpisode ep : selectedEpisodes) {
          if (cancel) {
            break;
          }

          MediaFile main = ep.getMainVideoFile();
          if (main.getPHash().isEmpty()) {
            try {
              String phash = VideoPHash.generate(main.getFileAsPath());
              if (!phash.isEmpty()) {
                main.setPHash(phash);
                ep.saveToDb();
              }
            }
            catch (IOException | InterruptedException e) {
              LOGGER.debug("Error generating PHASH for episode {}: {}", ep.getTitle(), e.getMessage());
            }
          }

          publishState(++i);
        }
      }
    };

    TmmTaskManager.getInstance().addUnnamedTask(task);
  }
}

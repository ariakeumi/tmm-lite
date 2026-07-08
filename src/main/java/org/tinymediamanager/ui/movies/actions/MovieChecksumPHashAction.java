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
import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskHandle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.scraper.util.VideoPHash;
import org.tinymediamanager.thirdparty.FFmpeg;
import org.tinymediamanager.thirdparty.FFprobe;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.movies.MovieUIModule;

public class MovieChecksumPHashAction extends TmmAction {
  private static final Logger LOGGER           = LoggerFactory.getLogger(MovieChecksumPHashAction.class);
  private static final long   serialVersionUID = 1L;

  public MovieChecksumPHashAction() {
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

    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies(true);
    if (selectedMovies.isEmpty()) {
      JOptionPane.showMessageDialog(MainWindow.getInstance(), TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    TmmTask task = new TmmTask(TmmResourceBundle.getString("checksum.phash.calculate"), selectedMovies.size(),
        TmmTaskHandle.TaskType.BACKGROUND_TASK) {
      @Override
      protected void doInBackground() {
        int i = 0;

        for (Movie movie : selectedMovies) {
          if (cancel) {
            break;
          }

          MediaFile main = movie.getMainVideoFile();
          if (main.getPHash().isEmpty()) {
            try {
              String phash = VideoPHash.generate(main.getFileAsPath());
              if (!phash.isEmpty()) {
                main.setPHash(phash);
                movie.saveToDb();
              }
            }
            catch (IOException | InterruptedException e) {
              LOGGER.debug("Error generating PHASH for movie {}: {}", movie.getTitle(), e.getMessage());
            }
          }
          publishState(++i);
        }
      }
    };

    TmmTaskManager.getInstance().addUnnamedTask(task);
  }
}

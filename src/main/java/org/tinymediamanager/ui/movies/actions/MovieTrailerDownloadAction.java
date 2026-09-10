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

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.KeyStroke;

import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.tasks.MovieTrailerDownloadTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;

/**
 * The class {@link MovieTrailerDownloadAction} is used to trigger trailer download for selected movies
 *
 * @author Manuel Laggner
 */
public class MovieTrailerDownloadAction extends TmmAction {
  public MovieTrailerDownloadAction() {
    putValue(NAME, TmmResourceBundle.getString("movie.downloadtrailer"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movie.downloadtrailer"));
    putValue(SMALL_ICON, IconManager.DOWNLOAD);
    putValue(LARGE_ICON_KEY, IconManager.DOWNLOAD);
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.ALT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies();

    if (selectedMovies.isEmpty()) {
      TmmToastManager.showErrorToast(MovieUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("movie.downloadtrailer"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    boolean existingTrailer = false;
    for (Movie movie : selectedMovies) {
      if (!movie.getMediaFiles(MediaFileType.TRAILER).isEmpty()) {
        existingTrailer = true;
        break;
      }
    }

    if (!existingTrailer) {
      startDownloadTasks(selectedMovies, false);
      return;
    }

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("movie.downloadtrailer"));

    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("movie.overwritetrailer"));
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> startDownloadTasks(selectedMovies, true));
    popupPanel.setOnCancelHandler(() -> startDownloadTasks(selectedMovies, false));
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void startDownloadTasks(List<Movie> selectedMovies, boolean overwriteTrailer) {
    for (Movie movie : selectedMovies) {
      if (!movie.getMediaFiles(MediaFileType.TRAILER).isEmpty() && !overwriteTrailer) {
        continue;
      }
      if (movie.getTrailer().isEmpty()) {
        continue;
      }

      TmmTaskManager.getInstance().addDownloadTask(new MovieTrailerDownloadTask(movie));
    }
  }
}

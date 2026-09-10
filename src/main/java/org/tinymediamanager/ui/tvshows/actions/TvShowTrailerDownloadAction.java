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

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.KeyStroke;

import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.tasks.TvShowTrailerDownloadTask;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * the class {@link TvShowTrailerDownloadAction} is used to download trailers for Tv shows
 *
 * @author Wolfgang Janes
 */
public class TvShowTrailerDownloadAction extends TmmAction {

  public TvShowTrailerDownloadAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.downloadtrailer"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tvshow.downloadtrailer"));
    putValue(SMALL_ICON, IconManager.DOWNLOAD);
    putValue(LARGE_ICON_KEY, IconManager.DOWNLOAD);
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.ALT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<TvShow> selectedTvShows = TvShowUIModule.getInstance().getSelectionModel().getSelectedTvShows();

    if (selectedTvShows.isEmpty()) {
      TmmToastManager.showErrorToast(TvShowUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("tvshow.downloadtrailer"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    boolean existingTrailer = false;
    for (TvShow tvShow : selectedTvShows) {
      if (!tvShow.getMediaFiles(MediaFileType.TRAILER).isEmpty()) {
        existingTrailer = true;
        break;
      }
    }

    if (!existingTrailer) {
      startDownloadTasks(selectedTvShows, false);
      return;
    }

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("tvshow.downloadtrailer"));
    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("movie.overwritetrailer"));
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> startDownloadTasks(selectedTvShows, true));
    popupPanel.setOnCancelHandler(() -> startDownloadTasks(selectedTvShows, false));
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void startDownloadTasks(List<TvShow> selectedTvShows, boolean overwriteTrailer) {
    for (TvShow tvShow : selectedTvShows) {
      if (!tvShow.getMediaFiles(MediaFileType.TRAILER).isEmpty() && !overwriteTrailer) {
        continue;
      }
      if (tvShow.getTrailer().isEmpty()) {
        continue;
      }

      TmmTaskManager.getInstance().addDownloadTask(new TvShowTrailerDownloadTask(tvShow));
    }
  }
}

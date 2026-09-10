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

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;

/**
 * The class {@link MovieDownloadActorImagesAction} is used to download images from actors / producers for selected Movies
 *
 * @author Wolfgang Janes
 */
public class MovieDownloadActorImagesAction extends TmmAction {

  public MovieDownloadActorImagesAction() {
    putValue(NAME, TmmResourceBundle.getString("movie.downloadactorimages"));
    putValue(SMALL_ICON, IconManager.IMAGE);
    putValue(LARGE_ICON_KEY, IconManager.IMAGE);
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.ALT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies();

    if (selectedMovies.isEmpty()) {
      TmmToastManager.showErrorToast(MovieUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("movie.downloadactorimages"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("movie.downloadactorimages"));

    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("movie.downloadactorimages.overwrite"));
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      for (Movie movie : selectedMovies) {
        movie.writeActorImages(true);
      }
    });
    popupPanel.setOnCancelHandler(() -> {
      for (Movie movie : selectedMovies) {
        movie.writeActorImages(false);
      }
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }
}

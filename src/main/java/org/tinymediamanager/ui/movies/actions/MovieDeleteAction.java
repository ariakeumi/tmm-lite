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

import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;

/**
 * The {@link MovieDeleteAction} - to remove all selected movies from the database and **physically** from the datasource
 * 
 * @author Manuel Laggner
 */
public class MovieDeleteAction extends TmmAction {
  public MovieDeleteAction() {
    putValue(SMALL_ICON, IconManager.DELETE_FOREVER_RED);
    putValue(NAME, TmmResourceBundle.getString("movie.delete"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movie.delete.hint"));
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.SHIFT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies();

    if (selectedMovies.isEmpty()) {
      TmmToastManager.showErrorToast(MovieUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("movie.delete"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    String message;
    if (Settings.getInstance().isEnableTrash()) {
      message = TmmResourceBundle.getString("movie.delete.desc");
    }
    else {
      message = TmmResourceBundle.getString("movie.delete.desc2");
    }

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("movie.delete"));
    ConfirmationPanel confirmationPanel = new ConfirmationPanel(message);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(
        () -> TmmTaskManager.getInstance().addUnnamedTask(() -> MovieModuleManager.getInstance().getMovieList().deleteMovies(selectedMovies)));
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }
}

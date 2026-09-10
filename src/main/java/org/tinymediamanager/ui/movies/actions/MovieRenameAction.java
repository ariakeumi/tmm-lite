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

import static org.tinymediamanager.ui.TmmFontHelper.L1;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.KeyStroke;

import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.tasks.MovieRenameTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;

/**
 * The class {@link MovieRenameAction} is used to rename movies
 * 
 * @author Manuel Laggner
 */
public class MovieRenameAction extends TmmAction {
  public MovieRenameAction() {
    putValue(NAME, TmmResourceBundle.getString("movie.rename"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movie.rename"));
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.SHIFT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies();

    if (selectedMovies.isEmpty()) {
      TmmToastManager.showErrorToast(MovieUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("movie.rename"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    if (TmmProperties.getInstance().getPropertyAsBoolean("movie.hiderenamehint")) {
      executeRename(selectedMovies);
      return;
    }

    JCheckBox checkBox = new JCheckBox(TmmResourceBundle.getString("tmm.donotshowagain"));
    TmmFontHelper.changeFont(checkBox, L1);

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("movie.rename"));

    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("movie.rename.desc"), checkBox);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      if (confirmationPanel.isCheckBoxSelected()) {
        TmmProperties.getInstance().putProperty("movie.hiderenamehint", String.valueOf(confirmationPanel.isCheckBoxSelected()));
      }
      executeRename(selectedMovies);
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void executeRename(List<Movie> selectedMovies) {
    TmmThreadPool renameTask = new MovieRenameTask(selectedMovies, MovieModuleManager.getInstance().getSettings().getDefaultRenamerProfile());
    TmmTaskManager.getInstance().addMainTask(renameTask);
  }
}

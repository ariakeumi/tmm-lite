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
package org.tinymediamanager.ui.moviesets.actions;

import static org.tinymediamanager.ui.TmmFontHelper.L1;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.JCheckBox;

import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.MovieSet;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.moviesets.MovieSetUIModule;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;

/**
 * @author Manuel Laggner
 * 
 */
public class MovieSetRemoveAction extends TmmAction {
  /**
   * Instantiates a new removes the movie set action.
   */
  public MovieSetRemoveAction() {
    putValue(NAME, TmmResourceBundle.getString("movieset.remove.desc"));
    putValue(LARGE_ICON_KEY, IconManager.DELETE);
    putValue(SMALL_ICON, IconManager.DELETE);
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movieset.remove.desc"));
    setDeleteAccelerators(0);
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<MovieSet> selectedMovieSets = MovieSetUIModule.getInstance().getSelectionModel().getSelectedMovieSets();

    if (selectedMovieSets.isEmpty()) {
      return;
    }

    if (TmmProperties.getInstance().getPropertyAsBoolean("movieset.hideremovehint")) {
      executeRemove(selectedMovieSets);
      return;
    }

    JCheckBox checkBox = new JCheckBox(TmmResourceBundle.getString("tmm.donotshowagain"));
    TmmFontHelper.changeFont(checkBox, L1);

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("movieset.remove.desc"));
    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("movieset.remove.confirm"), checkBox);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      if (confirmationPanel.isCheckBoxSelected()) {
        TmmProperties.getInstance().putProperty("movieset.hideremovehint", String.valueOf(confirmationPanel.isCheckBoxSelected()));
      }
      executeRemove(selectedMovieSets);
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void executeRemove(List<MovieSet> selectedMovieSets) {
    MainWindow.getInstance().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    for (MovieSet movieSet : selectedMovieSets) {
      MovieModuleManager.getInstance().getMovieList().removeMovieSet(movieSet);
    }
    MainWindow.getInstance().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }
}

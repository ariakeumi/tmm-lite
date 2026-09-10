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

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.JCheckBox;

import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.tasks.MovieReloadMediaInformationTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;

/**
 * the class {@link MovieRebuildMediainfoXmlAction} is used to delete mediainfo.xml for selected movies
 *
 * @author Manuel Laggner
 */
public class MovieRebuildMediainfoXmlAction extends TmmAction {
  public MovieRebuildMediainfoXmlAction() {
    putValue(NAME, TmmResourceBundle.getString("movie.rebuildmediainfoxml"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movie.rebuildmediainfoxml"));
    putValue(SMALL_ICON, IconManager.MEDIAINFO);
    putValue(LARGE_ICON_KEY, IconManager.MEDIAINFO);
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies();

    if (selectedMovies.isEmpty()) {
      TmmToastManager.showErrorToast(MovieUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("movie.rebuildmediainfoxml"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    if (TmmProperties.getInstance().getPropertyAsBoolean("movie.hidedeletemediainfoxmlhint")) {
      executeRebuild(selectedMovies);
      return;
    }

    JCheckBox checkBox = new JCheckBox(TmmResourceBundle.getString("tmm.donotshowagain"));
    TmmFontHelper.changeFont(checkBox, L1);

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("movie.deletemediainfoxml"));
    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("movie.deletemediainfoxml.desc"), checkBox);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      if (confirmationPanel.isCheckBoxSelected()) {
        TmmProperties.getInstance().putProperty("movie.hidedeletemediainfoxmlhint", String.valueOf(confirmationPanel.isCheckBoxSelected()));
      }
      executeRebuild(selectedMovies);
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void executeRebuild(List<Movie> selectedMovies) {
    for (Movie movie : selectedMovies) {
      movie.getMediaFiles(MediaFileType.MEDIAINFO).forEach(mediaFile -> {
        Utils.deleteFileSafely(mediaFile.getFileAsPath());
        movie.removeFromMediaFiles(mediaFile);
      });
    }

    TmmThreadPool task = new MovieReloadMediaInformationTask(selectedMovies);
    TmmTaskManager.getInstance().addMainTask(task);
  }
}

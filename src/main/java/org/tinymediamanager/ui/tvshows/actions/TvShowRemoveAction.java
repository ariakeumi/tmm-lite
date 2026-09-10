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

import static org.tinymediamanager.ui.TmmFontHelper.L1;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JCheckBox;
import javax.swing.KeyStroke;

import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * The class {@link TvShowRemoveAction} is used to remove selected elements (TV shows/episodes)
 * 
 * @author Manuel Laggner
 */
public class TvShowRemoveAction extends TmmAction {
  public TvShowRemoveAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.remove"));
    putValue(SMALL_ICON, IconManager.DELETE);
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tvshow.remove"));
    putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
  }

  @Override
  protected void processAction(ActionEvent e) {
    TvShowSelectionModel.SelectedObjects selectedObjects = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects(true, false);

    if (selectedObjects.isLockedFound()) {
      TvShowSelectionModel.showLockedInformation();
    }

    if (selectedObjects.isEmpty()) {
      TmmToastManager.showErrorToast(TvShowUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("tvshow.remove"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    if (Boolean.TRUE.equals(TmmProperties.getInstance().getPropertyAsBoolean("tvshow.hideremovehint"))) {
      executeRemove(selectedObjects);
      return;
    }

    JCheckBox checkBox = new JCheckBox(TmmResourceBundle.getString("tmm.donotshowagain"));
    TmmFontHelper.changeFont(checkBox, L1);

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("tvshow.remove"));
    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("tvshow.remove.desc"), checkBox);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      if (confirmationPanel.isCheckBoxSelected()) {
        TmmProperties.getInstance().putProperty("tvshow.hideremovehint", String.valueOf(confirmationPanel.isCheckBoxSelected()));
      }
      executeRemove(selectedObjects);
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void executeRemove(TvShowSelectionModel.SelectedObjects selectedObjects) {
    TmmTaskManager.getInstance().addUnnamedTask(() -> {
      for (TvShowEpisode episode : selectedObjects.getEpisodes()) {
        if (episode.isDummy()) {
          episode.getTvShow().removeDummyEpisode(episode);
        }
        else {
          episode.getTvShow().removeEpisode(episode);
        }
      }

      for (TvShowSeason season : selectedObjects.getSeasons()) {
        for (TvShowEpisode episode : season.getEpisodesForDisplay()) {
          if (episode.isDummy()) {
            season.getTvShow().removeDummyEpisode(episode);
          }
          else {
            season.getTvShow().removeEpisode(episode);
          }
        }
      }

      for (TvShow tvShow : selectedObjects.getTvShows()) {
        TvShowModuleManager.getInstance().getTvShowList().removeTvShow(tvShow);
      }
    });
  }
}

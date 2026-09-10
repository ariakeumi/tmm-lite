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

import javax.swing.KeyStroke;

import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * The class {@link TvShowDeleteAction} is used to remove selected elements and delete it from the data source
 * 
 * @author Manuel Laggner
 */
public class TvShowDeleteAction extends TmmAction {
  public TvShowDeleteAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.delete"));
    putValue(SMALL_ICON, IconManager.DELETE_FOREVER_RED);
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tvshow.delete.hint"));
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.SHIFT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    TvShowSelectionModel.SelectedObjects selectedObjects = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects();

    if (selectedObjects.isEmpty()) {
      TmmToastManager.showErrorToast(TvShowUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("tvshow.delete"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    String message = Settings.getInstance().isEnableTrash() ? TmmResourceBundle.getString("tvshow.delete.desc")
        : TmmResourceBundle.getString("tvshow.delete.desc2");

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("tvshow.delete"));

    ConfirmationPanel confirmationPanel = new ConfirmationPanel(message);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      if (selectedObjects.isLockedFound()) {
        TvShowSelectionModel.showLockedInformation();
      }

      TmmTaskManager.getInstance().addUnnamedTask(() -> {
        for (TvShow tvShow : selectedObjects.getTvShows()) {
          TvShowModuleManager.getInstance().getTvShowList().deleteTvShow(tvShow);
        }

        for (TvShowSeason season : selectedObjects.getSeasons()) {
          for (TvShowEpisode episode : season.getEpisodes()) {
            season.getTvShow().deleteEpisode(episode);
          }
        }

        for (TvShowEpisode episode : selectedObjects.getEpisodes()) {
          episode.getTvShow().deleteEpisode(episode);
        }
      });
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }
}

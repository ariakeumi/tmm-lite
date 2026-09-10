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

import java.awt.Cursor;
import java.awt.event.ActionEvent;

import javax.swing.JCheckBox;

import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
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
 * the class {@link TvShowDeleteMediainfoXmlAction} is used to delete mediainfo.xml for selected TV shows/episodes
 *
 * @author Manuel Laggner
 */
public class TvShowDeleteMediainfoXmlAction extends TmmAction {
  public TvShowDeleteMediainfoXmlAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.deletemediainfoxml"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tvshow.deletemediainfoxml"));
    putValue(SMALL_ICON, IconManager.DELETE);
    putValue(LARGE_ICON_KEY, IconManager.DELETE);
  }

  @Override
  protected void processAction(ActionEvent e) {
    TvShowSelectionModel.SelectedObjects selectedObjects = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects();

    if (selectedObjects.isLockedFound()) {
      TvShowSelectionModel.showLockedInformation();
    }

    if (selectedObjects.isEmpty()) {
      TmmToastManager.showErrorToast(TvShowUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("tvshow.deletemediainfoxml"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    if (Boolean.TRUE.equals(TmmProperties.getInstance().getPropertyAsBoolean("tvshow.hidedeletemediainfoxmlhint"))) {
      executeDelete(selectedObjects);
      return;
    }

    JCheckBox checkBox = new JCheckBox(TmmResourceBundle.getString("tmm.donotshowagain"));
    TmmFontHelper.changeFont(checkBox, L1);

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("tvshow.deletemediainfoxml"));
    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("tvshow.deletemediainfoxml.desc"), checkBox);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      if (confirmationPanel.isCheckBoxSelected()) {
        TmmProperties.getInstance().putProperty("tvshow.hidedeletemediainfoxmlhint", String.valueOf(confirmationPanel.isCheckBoxSelected()));
      }
      executeDelete(selectedObjects);
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void executeDelete(TvShowSelectionModel.SelectedObjects selectedObjects) {
    MainWindow.getInstance().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    for (TvShow tvShow : selectedObjects.getTvShows()) {
      tvShow.getMediaFiles(MediaFileType.MEDIAINFO).forEach(mediaFile -> {
        Utils.deleteFileSafely(mediaFile.getFileAsPath());
        tvShow.removeFromMediaFiles(mediaFile);
      });
    }

    for (TvShowEpisode episode : selectedObjects.getEpisodesRecursive()) {
      episode.getMediaFiles(MediaFileType.MEDIAINFO).forEach(mediaFile -> {
        Utils.deleteFileSafely(mediaFile.getFileAsPath());
        episode.removeFromMediaFiles(mediaFile);
      });
    }
    MainWindow.getInstance().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }
}

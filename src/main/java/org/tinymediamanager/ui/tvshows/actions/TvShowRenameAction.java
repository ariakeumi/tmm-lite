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

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JCheckBox;
import javax.swing.KeyStroke;

import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.tasks.TvShowRenameTask;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.panels.ConfirmationPanel;
import org.tinymediamanager.ui.panels.ModalPopupPanel;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * The class {@link TvShowRenameAction} is used to rename TV shows/episodes
 * 
 * @author Manuel Laggner
 */
public class TvShowRenameAction extends TmmAction {
  public TvShowRenameAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.rename"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tvshow.rename"));
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.SHIFT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    TvShowSelectionModel.SelectedObjects selectedObjects = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects();

    if (selectedObjects.isLockedFound()) {
      TvShowSelectionModel.showLockedInformation();
    }

    if (selectedObjects.isEmpty()) {
      TmmToastManager.showErrorToast(TvShowUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("tvshow.rename"),
          TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    if (Boolean.TRUE.equals(TmmProperties.getInstance().getPropertyAsBoolean("tvshow.hiderenamehint"))) {
      executeRename(selectedObjects);
      return;
    }

    JCheckBox checkBox = new JCheckBox(TmmResourceBundle.getString("tmm.donotshowagain"));
    TmmFontHelper.changeFont(checkBox, L1);

    ModalPopupPanel popupPanel = MainWindow.getInstance().createModalPopupPanel();
    popupPanel.setTitle(TmmResourceBundle.getString("tvshow.rename"));
    ConfirmationPanel confirmationPanel = new ConfirmationPanel(TmmResourceBundle.getString("tvshow.rename.desc"), checkBox);
    popupPanel.setContent(confirmationPanel);
    popupPanel.setOnCloseHandler(() -> {
      if (confirmationPanel.isCheckBoxSelected()) {
        TmmProperties.getInstance().putProperty("tvshow.hiderenamehint", String.valueOf(confirmationPanel.isCheckBoxSelected()));
      }
      executeRename(selectedObjects);
    });
    MainWindow.getInstance().showModalPopupPanel(popupPanel);
  }

  private void executeRename(TvShowSelectionModel.SelectedObjects selectedObjects) {
    TmmThreadPool renameTask = new TvShowRenameTask(selectedObjects.getTvShows(), selectedObjects.getEpisodesRecursive(),
        TvShowModuleManager.getInstance().getSettings().getDefaultRenamerProfile());
    TmmTaskManager.getInstance().addMainTask(renameTask);
  }
}

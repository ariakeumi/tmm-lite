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

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;

import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowRenamerProfile;
import org.tinymediamanager.core.tvshow.tasks.TvShowRenameTask;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.dialogs.TmmOptionDialog;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * TvShowRenameWithProfileAction - rename TV shows/episodes with a specific renamer profile
 *
 * @author Manuel Laggner
 */
public class TvShowRenameWithProfileAction extends TmmAction {
  private final String profileName;

  public TvShowRenameWithProfileAction(String profileName) {
    this.profileName = profileName;
    putValue(NAME, profileName);
  }

  @Override
  protected void processAction(ActionEvent e) {
    TvShowSelectionModel.SelectedObjects selectedObjects = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects();

    if (selectedObjects.isLockedFound()) {
      TvShowSelectionModel.showLockedInformation();
    }

    if (selectedObjects.isEmpty()) {
      return;
    }

    // display warning and ask the user again
    if (Boolean.FALSE.equals(TmmProperties.getInstance().getPropertyAsBoolean("tvshow.hiderenamehint"))) {
      JCheckBox checkBox = new JCheckBox(TmmResourceBundle.getString("tmm.donotshowagain"));
      TmmFontHelper.changeFont(checkBox, L1);
      checkBox.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

      int answer = TmmOptionDialog.showOptionDialog(MainWindow.getInstance(), TmmResourceBundle.getString("tvshow.rename"),
          TmmResourceBundle.getString("tvshow.rename.desc"), checkBox);

      if (checkBox.isSelected()) {
        TmmProperties.getInstance().putProperty("tvshow.hiderenamehint", String.valueOf(checkBox.isSelected()));
      }

      if (answer != JOptionPane.YES_OPTION) {
        return;
      }
    }

    // rename
    TvShowRenamerProfile profile = TvShowModuleManager.getInstance().getSettings().getRenamerProfile(profileName);
    TmmThreadPool renameTask = new TvShowRenameTask(selectedObjects.getTvShows(), selectedObjects.getEpisodesRecursive(), profile);
    TmmTaskManager.getInstance().addMainTask(renameTask);
  }
}

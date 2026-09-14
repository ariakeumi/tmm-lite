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

import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.tinymediamanager.core.DatasourceFolderGuard;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * The {@link TvShowAddDatasourceAction} - to directly add a new data source
 * 
 * @author Manuel Laggner
 */
public class TvShowAddDatasourceAction extends TmmAction {
  public TvShowAddDatasourceAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.datasource.add"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tvshow.datasource.add"));
    putValue(SMALL_ICON, IconManager.ADD);
    putValue(LARGE_ICON_KEY, IconManager.ADD);
  }

  @Override
  protected void processAction(ActionEvent e) {
    SwingUtilities.invokeLater(() -> {
      String path = TmmProperties.getInstance().getProperty("tvshow.datasource.path");
      Path file = TmmUIHelper.selectDirectory(TmmResourceBundle.getString("Settings.tvshowdatasource.folderchooser"), path);
      if (file != null && Files.isDirectory(file)) {
        if (DatasourceFolderGuard.isDangerous(file)) {
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "Settings.datasource.dangerous"));
          TmmToastManager.showErrorToast(TvShowUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("Settings.datasource.dangerous"));
        }
        else if (TvShowModuleManager.getInstance().getSettings().addTvShowDataSources(file.toAbsolutePath().toString())) {
          TvShowModuleManager.getInstance().getSettings().saveSettings();
          TmmProperties.getInstance().putProperty("tvshow.datasource.path", file.toAbsolutePath().toString());
        }
        else {
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "Settings.datasource.nested"));
          TmmToastManager.showErrorToast(TvShowUIModule.getInstance().getDetailPanel(), TmmResourceBundle.getString("Settings.datasource.nested"));
        }
      }
    });
  }
}

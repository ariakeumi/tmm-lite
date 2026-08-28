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

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskHandle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.thirdparty.KodiRPC;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * The class {@link TvShowKodiRefreshNfoLightAction} is used to force a refresh of all selected items (TV shows only) in Kodi
 * 
 * @author Manuel Laggner
 */
public class TvShowKodiRefreshNfoLightAction extends TmmAction {
  public TvShowKodiRefreshNfoLightAction() {
    putValue(LARGE_ICON_KEY, IconManager.MEDIAINFO);
    putValue(SMALL_ICON, IconManager.MEDIAINFO);
    putValue(NAME, TmmResourceBundle.getString("kodi.rpc.refreshnfo") + " (" + (TmmResourceBundle.getString("metatag.tvshows") + ")"));
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

    TmmTaskManager.getInstance()
        .addUnnamedTask(new TmmTask(TmmResourceBundle.getString("kodi.rpc.refreshnfo"), selectedObjects.getTvShows().size(),
            TmmTaskHandle.TaskType.BACKGROUND_TASK) {

          @Override
          protected void doInBackground() {
            KodiRPC kodiRPC = KodiRPC.getInstance();
            int i = 0;

            // update show
            for (TvShow tvShow : selectedObjects.getTvShows()) {
              kodiRPC.refreshFromNfo(tvShow, false);
              publishState(++i);
              if (cancel) {
                return;
              }
            }

            // we need to re-match the shows
            try {
              // need some time to propagate the new showId
              Thread.sleep(1000);
            }
            catch (InterruptedException e) {
              // ignore
            }
            kodiRPC.updateTvShowMappings();
          }
        });
  }
}

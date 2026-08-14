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
package org.tinymediamanager.ui.actions;

import java.awt.event.ActionEvent;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskHandle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.thirdparty.simkl.Simkl;

/**
 * The {@link ClearSimklSyncStateAction} is used to reset the Simkl incremental sync state (cached data/watermarks), so the next sync performs a full
 * re-sync instead of an incremental one.
 *
 * @author Manuel Laggner
 */
public class ClearSimklSyncStateAction extends TmmAction {
  public ClearSimklSyncStateAction() {
    putValue(NAME, TmmResourceBundle.getString("tmm.clearsimklsyncstate"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tmm.clearsimklsyncstate.desc"));
  }

  @Override
  protected void processAction(ActionEvent arg0) {
    TmmTaskManager.getInstance()
        .addUnnamedTask(new TmmTask(TmmResourceBundle.getString("tmm.clearsimklsyncstate"), 0, TmmTaskHandle.TaskType.BACKGROUND_TASK) {
          @Override
          protected void doInBackground() {
            Simkl.getInstance().clearSyncState();
          }
        });
  }
}

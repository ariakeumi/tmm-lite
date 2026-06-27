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
import org.tinymediamanager.core.tasks.ImageCacheCleanupTask;
import org.tinymediamanager.core.threading.TmmTaskManager;

/**
 * The {@link CleanupImageCacheAction} class is used to remove all orphaned entries from the image cache
 * 
 * @author Manuel Laggner
 */
public class CleanupImageCacheAction extends TmmAction {

  public CleanupImageCacheAction() {
    putValue(NAME, TmmResourceBundle.getString("tmm.cleanupimagecache"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tmm.cleanupimagecache.desc"));
  }

  @Override
  protected void processAction(ActionEvent arg0) {
    TmmTaskManager.getInstance().addUnnamedTask(new ImageCacheCleanupTask());
  }
}

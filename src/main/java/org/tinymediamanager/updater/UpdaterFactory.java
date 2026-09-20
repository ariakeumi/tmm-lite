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
package org.tinymediamanager.updater;

import org.tinymediamanager.Globals;
import org.tinymediamanager.core.threading.TmmTask;

/**
 * The class {@link UpdaterFactory} picks the right in-place update task for the current platform.<br>
 * On macOS the getdown file-patching updater is never used (it would break the code signature), so we hand out a {@link MacUpdaterTask} that swaps
 * the whole {@code .app} bundle instead. Everywhere else the classic {@link UpdaterTask} is returned.
 *
 * @author Manuel Laggner
 */
public final class UpdaterFactory {
  private UpdaterFactory() {
    throw new IllegalAccessError();
  }

  /**
   * @return true when an in-place (self) update can be offered to the user on this installation
   */
  public static boolean isInPlaceUpdatePossible() {
    return Globals.isSelfUpdatable() || Globals.isBundleUpdatable();
  }

  /**
   * Create the {@link TmmTask} that performs the actual update for this platform. Only call this when {@link #isInPlaceUpdatePossible()} is
   * {@code true}.
   *
   * @return a ready-to-schedule update task
   */
  public static TmmTask createTask() {
    if (Globals.isBundleUpdatable()) {
      return new MacUpdaterTask();
    }
    return new UpdaterTask();
  }
}

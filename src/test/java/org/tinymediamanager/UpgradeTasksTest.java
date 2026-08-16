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
package org.tinymediamanager;

import org.junit.Test;
import org.tinymediamanager.core.BasicTest;
import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmStore;

/**
 * tests the one-time migration of volatile data from the tmm.prop to the {@link TmmStore}
 *
 * @author Manuel Laggner
 */
public class UpgradeTasksTest extends BasicTest {

  @Test
  public void testMigrateVolatilePropertiesToTmmStore() {
    TmmProperties properties = TmmProperties.getInstance();

    // store legacy values in the tmm.prop
    properties.putProperty("lastUpdateCheck", "1111111111111");
    properties.putProperty("lastYtDlpUpdateCheck", "2222222222222");

    UpgradeTasks.migrateVolatilePropertiesToTmmStore();

    // the values should now live in the TmmStore
    assertEqual(1111111111111L, TmmStore.getInstance().getAsLong("tmm.update.lastCheck", 0L));
    assertEqual(2222222222222L, TmmStore.getInstance().getAsLong("tmm.ytdlp.lastCheck", 0L));

    // and should have been removed from the tmm.prop
    assertEqual(null, properties.getProperty("lastUpdateCheck"));
    assertEqual(null, properties.getProperty("lastYtDlpUpdateCheck"));
  }
}

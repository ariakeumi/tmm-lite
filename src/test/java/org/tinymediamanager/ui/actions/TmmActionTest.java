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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Assume;
import org.junit.Test;

/**
 * The class {@link TmmActionTest} tests the accelerator handling of {@link TmmAction}s
 */
public class TmmActionTest {
  private static final int MENU_SHORTCUT = SystemUtils.IS_OS_MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

  @Test
  public void getAcceleratorKeysWithoutAccelerator() {
    TestAction action = new TestAction();
    assertThat(action.getAcceleratorKeys()).isEmpty();
  }

  @Test
  public void getAcceleratorKeysCoversBothDeleteKeys() {
    TestAction action = new TestAction();
    action.setDeleteKeys(0);

    assertThat(Arrays.asList(action.getAcceleratorKeys())) //
        .contains(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
  }

  @Test
  public void getAcceleratorKeysHaveNoDuplicates() {
    TestAction action = new TestAction();
    action.setDeleteKeys(0);

    assertThat(action.getAcceleratorKeys()).doesNotHaveDuplicates();
  }

  @Test
  public void deleteAcceleratorDefaultIsPlatformSpecific() {
    TestAction action = new TestAction();
    action.setDeleteKeys(0);

    assertThat(acceleratorOf(action)).isEqualTo(KeyStroke.getKeyStroke(defaultDeleteKey(), 0));
  }

  @Test
  public void deleteAcceleratorKeepsModifiers() {
    int modifiers = MENU_SHORTCUT + InputEvent.SHIFT_DOWN_MASK;

    TestAction action = new TestAction();
    action.setDeleteKeys(modifiers);

    assertThat(acceleratorOf(action)).isEqualTo(KeyStroke.getKeyStroke(defaultDeleteKey(), modifiers));
    assertThat(Arrays.asList(action.getAcceleratorKeys())).contains(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, modifiers));
  }

  @Test
  public void nullKeyStrokesAreIgnored() {
    TestAction action = new TestAction();
    action.setExtras(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), null);

    assertThat(action.getAcceleratorKeys()).containsExactly(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
  }

  @Test
  public void macConventionAlsoTriggersRemove() {
    Assume.assumeTrue(SystemUtils.IS_OS_MAC);

    TestAction action = new TestAction();
    action.setDeleteKeys(0);

    assertThat(Arrays.asList(action.getAcceleratorKeys())).contains(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, MENU_SHORTCUT));
  }

  private static int defaultDeleteKey() {
    return SystemUtils.IS_OS_MAC ? KeyEvent.VK_BACK_SPACE : KeyEvent.VK_DELETE;
  }

  private static KeyStroke acceleratorOf(TestAction action) {
    return (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
  }

  private static class TestAction extends TmmAction {
    void setDeleteKeys(int modifiers) {
      setDeleteAccelerators(modifiers);
    }

    void setExtras(KeyStroke... keyStrokes) {
      setExtraAcceleratorKeys(keyStrokes);
    }

    @Override
    protected void processAction(ActionEvent e) {
      // not needed here
    }
  }
}

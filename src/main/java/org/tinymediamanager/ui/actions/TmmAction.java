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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.TmmModuleManager;
import org.tinymediamanager.license.TmmFeature;

/**
 * The class TmmAction is an abstract action-wrapper to provide base logging
 */
public abstract class TmmAction extends AbstractAction implements TmmFeature {
  private static final Logger LOGGER          = LoggerFactory.getLogger(TmmAction.class);

  /**
   * the command key on macOS: the same value {@link java.awt.Toolkit#getMenuShortcutKeyMaskEx()} returns there, but without requiring a display
   */
  private static final int    MAC_COMMAND_KEY = InputEvent.META_DOWN_MASK;

  private KeyStroke[]         extraAcceleratorKeys;

  @Override
  public final void actionPerformed(ActionEvent e) {
    LOGGER.debug("action fired: {}", this.getClass().getSimpleName());

    // inform the statistics timer that tmm is active
    TmmModuleManager.getInstance().setActive();

    if (isEnabled()) {
      processAction(e);
    }
  }

  @Override
  public final Object getValue(String key) {
    switch (key) {
      case "enabled":
        return isEnabled();

      case NAME:
      case SHORT_DESCRIPTION:
        Object value = super.getValue(key);
        if (value != null && !isEnabled()) {
          return "*PRO* " + value.toString();
        }
        return value;

      default:
        return super.getValue(key);
    }
  }

  @Override
  public final boolean isEnabled() {
    return isFeatureEnabled();
  }

  /**
   * all keystrokes which trigger this action via the accelerator management of the UI modules: the {@link #ACCELERATOR_KEY} and the
   * {@link #getExtraAcceleratorKeys()}
   *
   * @return the keystrokes in registration order, empty if this action has no accelerator at all
   */
  public final KeyStroke[] getAcceleratorKeys() {
    Set<KeyStroke> keyStrokes = new LinkedHashSet<>();

    Object accelerator = getValue(ACCELERATOR_KEY);
    if (accelerator instanceof KeyStroke keyStroke) {
      keyStrokes.add(keyStroke);
    }
    for (KeyStroke extraAcceleratorKey : getExtraAcceleratorKeys()) {
      if (extraAcceleratorKey != null) {
        keyStrokes.add(extraAcceleratorKey);
      }
    }

    return keyStrokes.toArray(new KeyStroke[0]);
  }

  /**
   * additional keystrokes which trigger this action besides the {@link #ACCELERATOR_KEY}, e.g. because a single accelerator cannot cover the
   * different keyboards of all supported platforms
   *
   * @return the additional keystrokes, never null
   */
  protected KeyStroke[] getExtraAcceleratorKeys() {
    return extraAcceleratorKeys == null ? new KeyStroke[0] : extraAcceleratorKeys.clone();
  }

  /**
   * sets the additional keystrokes which trigger this action besides the {@link #ACCELERATOR_KEY}
   *
   * @param keyStrokes
   *          the additional keystrokes (null or empty to have none)
   */
  protected final void setExtraAcceleratorKeys(KeyStroke... keyStrokes) {
    extraAcceleratorKeys = keyStrokes == null ? null : keyStrokes.clone();
  }

  /**
   * Convenience for delete/remove style actions: sets the {@link #ACCELERATOR_KEY} and the extra accelerator keys in a platform aware manner. Since
   * the delete key of a Mac keyboard is the backspace key, {@link KeyEvent#VK_BACK_SPACE} is the default on macOS (with {@link KeyEvent#VK_DELETE} of
   * external keyboards and the macOS convention Cmd+Backspace as alternates) and {@link KeyEvent#VK_DELETE} everywhere else (with Backspace as
   * alternate).
   *
   * @param modifiers
   *          the modifier mask of the default keystroke (e.g. {@code 0} or the menu shortcut mask combined with shift)
   */
  protected final void setDeleteAccelerators(int modifiers) {
    KeyStroke[] keyStrokes = getDeleteAccelerators(modifiers);
    putValue(ACCELERATOR_KEY, keyStrokes[0]);
    setExtraAcceleratorKeys(Arrays.copyOfRange(keyStrokes, 1, keyStrokes.length));
  }

  /**
   * the platform aware keystrokes of a delete/remove style action
   *
   * @param modifiers
   *          the modifier mask of the default keystroke
   *
   * @return the keystrokes, the default one (to be used as {@link #ACCELERATOR_KEY}) first
   */
  private static KeyStroke[] getDeleteAccelerators(int modifiers) {
    if (!SystemUtils.IS_OS_MAC) {
      return new KeyStroke[] { KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, modifiers), KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, modifiers) };
    }

    KeyStroke backspace = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, modifiers);
    KeyStroke delete = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, modifiers);

    if ((modifiers & MAC_COMMAND_KEY) != 0) {
      return new KeyStroke[] { backspace, delete };
    }

    return new KeyStroke[] { backspace, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, modifiers | MAC_COMMAND_KEY), delete };
  }

  /**
   * the inheriting class should process the action in this method rather than actionPerformed()
   * 
   * @param e
   *          the ActionEvent from actionPerformed
   */
  protected abstract void processAction(ActionEvent e);
}

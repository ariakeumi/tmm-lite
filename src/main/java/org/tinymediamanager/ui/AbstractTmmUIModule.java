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

package org.tinymediamanager.ui;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.panels.IModalPopupPanelProvider;

public abstract class AbstractTmmUIModule implements ITmmUIModule {
  protected static final ResourceBundle BUNDLE       = ResourceBundle.getBundle("messages");

  protected final Map<Class<?>, Action> actionMap    = new HashMap<>();

  protected Action                      searchAction = null;
  protected Action                      editAction   = null;
  protected Action                      updateAction = null;
  protected Action                      renameAction = null;

  protected JPopupMenu                  popupMenu;
  protected JPopupMenu                  updatePopupMenu;
  protected JPopupMenu                  searchPopupMenu;
  protected JPopupMenu                  editPopupMenu;
  protected JPopupMenu                  renamePopupMenu;

  protected AbstractTmmUIModule() {
  }

  /**
   * this factory creates the action and registers the hotkeys for accelerator management
   *
   * @param actionClass
   *          the class of the action
   * @return the constructed action
   */
  protected Action createAndRegisterAction(Class<? extends Action> actionClass) {
    Action action = actionMap.get(actionClass);
    if (action == null) {
      try {
        action = actionClass.getDeclaredConstructor().newInstance();
        actionMap.put(actionClass, action);
      }
      catch (Exception ignored) {
        // ignored
      }
    }
    return action;
  }

  /**
   * register accelerators
   */
  protected void registerAccelerators() {
    for (Map.Entry<Class<?>, Action> entry : actionMap.entrySet()) {
      try {
        KeyStroke[] keyStrokes = getAcceleratorKeys(entry.getValue());
        if (keyStrokes.length == 0) {
          continue;
        }

        String actionMapKey = "action" + entry.getKey().getName();
        JComponent tabPanel = getTabPanel();
        for (KeyStroke keyStroke : keyStrokes) {
          tabPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionMapKey);
        }
        tabPanel.getActionMap().put(actionMapKey, new ModalPopupGuardAction(entry.getValue(), tabPanel));
      }
      catch (Exception ignored) {
        // just do not crash
      }
    }
  }

  /**
   * all keystrokes which trigger this action; {@link TmmAction}s may provide more than the single {@link Action#ACCELERATOR_KEY}
   *
   * @param action
   *          the action to get the keystrokes of
   *
   * @return the keystrokes, empty if the action has no accelerator
   */
  private KeyStroke[] getAcceleratorKeys(Action action) {
    if (action instanceof TmmAction tmmAction) {
      return tmmAction.getAcceleratorKeys();
    }

    KeyStroke keyStroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
    return keyStroke == null ? new KeyStroke[0] : new KeyStroke[] { keyStroke };
  }

  @Override
  public Action getSearchAction() {
    return searchAction;
  }

  @Override
  public JPopupMenu getSearchMenu() {
    return searchPopupMenu;
  }

  @Override
  public Action getEditAction() {
    return editAction;
  }

  @Override
  public JPopupMenu getEditMenu() {
    return editPopupMenu;
  }

  @Override
  public Action getUpdateAction() {
    return updateAction;
  }

  @Override
  public JPopupMenu getUpdateMenu() {
    return updatePopupMenu;
  }

  @Override
  public Action getRenameAction() {
    return renameAction;
  }

  @Override
  public JPopupMenu getRenameMenu() {
    return renamePopupMenu;
  }

  @Override
  public JPopupMenu getRenameButtonMenu() {
    return null;
  }

  @Override
  public Icon getSearchButtonIcon() {
    return IconManager.TOOLBAR_REFRESH;
  }

  @Override
  public Icon getSearchButtonHoverIcon() {
    return IconManager.TOOLBAR_REFRESH_HOVER;
  }

  /**
   * The class {@link ModalPopupGuardAction} suppresses accelerator invocations while a modal popup panel overlays the window, because those panels do
   * not block the window wide key bindings of the tab panels
   */
  private static class ModalPopupGuardAction extends AbstractAction {
    private final Action     delegate;
    private final JComponent tabPanel;

    ModalPopupGuardAction(Action delegate, JComponent tabPanel) {
      this.delegate = delegate;
      this.tabPanel = tabPanel;
    }

    @Override
    public boolean isEnabled() {
      // keep the dispatch semantics of the wrapped action
      return delegate.isEnabled();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      IModalPopupPanelProvider provider = IModalPopupPanelProvider.findModalProvider(tabPanel);
      if (provider != null && provider.isModalPopupPanelShowing()) {
        return;
      }

      delegate.actionPerformed(e);
    }
  }
}

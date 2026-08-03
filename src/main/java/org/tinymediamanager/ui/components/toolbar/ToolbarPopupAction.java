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
package org.tinymediamanager.ui.components.toolbar;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPopupMenu;

/**
 * The class {@link ToolbarPopupAction} is an action which shows a popup menu on demand when invoked. If the given popup menu provider returns a popup
 * menu, it will be shown relative to the invoker; otherwise the wrapped action is executed.
 *
 * @author Manuel Laggner
 */
public class ToolbarPopupAction extends AbstractAction {
  private final Action               wrappedAction;
  private final Supplier<JPopupMenu> popupMenuProvider;

  /**
   * Creates a new {@link ToolbarPopupAction}.
   *
   * @param wrappedAction
   *          the action to be executed when no popup menu is available
   * @param popupMenuProvider
   *          the provider which returns the popup menu to be shown, or null
   */
  public ToolbarPopupAction(Action wrappedAction, Supplier<JPopupMenu> popupMenuProvider) {
    this.wrappedAction = wrappedAction;
    this.popupMenuProvider = popupMenuProvider;

    if (wrappedAction instanceof AbstractAction abstractAction) {
      for (Object key : abstractAction.getKeys()) {
        putValue((String) key, abstractAction.getValue((String) key));
      }
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    JPopupMenu popupMenu = popupMenuProvider != null ? popupMenuProvider.get() : null;
    if (popupMenu != null && e.getSource() instanceof Component component) {
      popupMenu.show(component, component.getWidth() / 2, component.getHeight());
    }
    else if (wrappedAction != null) {
      wrappedAction.actionPerformed(e);
    }
  }
}

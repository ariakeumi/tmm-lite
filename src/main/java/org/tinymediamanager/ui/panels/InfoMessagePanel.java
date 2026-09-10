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
package org.tinymediamanager.ui.panels;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.tinymediamanager.core.TmmResourceBundle;

import net.miginfocom.swing.MigLayout;

public class InfoMessagePanel extends JPanel implements IModalPopupPanel {
  private final JButton   btnOk;
  private final JCheckBox checkBox;

  public InfoMessagePanel(String message) {
    this(message, null);
  }

  public InfoMessagePanel(String message, JCheckBox checkBox) {
    this.checkBox = checkBox;

    btnOk = new JButton(TmmResourceBundle.getString("Button.ok"));
    btnOk.addActionListener(e -> setVisible(false));

    setLayout(new MigLayout("insets 0", "[grow]", "[]0"));

    String[] lines = message.split("\n");
    int row = 0;
    for (String line : lines) {
      add(new JLabel(line), "cell 0 " + row + ", growx, wrap");
      row++;
    }

    if (checkBox != null) {
      add(checkBox, "cell 0 " + row + ", gaptop 20lp, growx");
    }
  }

  public boolean isCheckBoxSelected() {
    return checkBox != null && checkBox.isSelected();
  }

  @Override
  public JComponent getContent() {
    return this;
  }

  @Override
  public JButton getCloseButton() {
    return btnOk;
  }

  @Override
  public JButton getCancelButton() {
    return null;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }
}

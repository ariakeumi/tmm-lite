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
package org.tinymediamanager.ui.movies.settings;

import static org.tinymediamanager.ui.TmmFontHelper.H3;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.button.SquareIconButton;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.table.PostProcessTable;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.dialogs.PostProcessDialog;
import org.tinymediamanager.ui.dialogs.SettingsDialog;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import net.miginfocom.swing.MigLayout;

/**
 * the class {@link MoviePostProcessingSettingsPanel} holds the settings for post process action for movies
 *
 * @author Wolfgang Janes
 */
public class MoviePostProcessingSettingsPanel extends JPanel {

  private final EventList<PostProcess> postProcessEventList;

  private TmmTable                     tablePostProcesses;
  private JButton                      btnRemoveProcess;
  private JButton                      btnAddProcess;
  private JButton                      btnMoveProcessUp;
  private JButton                      btnMoveProcessDown;

  MoviePostProcessingSettingsPanel() {
    MovieSettings settings = MovieModuleManager.getInstance().getSettings();

    postProcessEventList = GlazedLists.eventList(settings.getPostProcess());
    GlazedLists.syncEventListToList(postProcessEventList, settings.getPostProcess());

    initComponents();

    tablePostProcesses.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // button listeners
    btnAddProcess.addActionListener(e -> {
      PostProcessDialog dialog = new MoviePostProcessDialog();
      dialog.pack();
      dialog.setLocationRelativeTo(SettingsDialog.getInstance());
      dialog.setVisible(true);
      tablePostProcesses.adjustColumnPreferredWidths(5);
    });

    btnRemoveProcess.addActionListener(e -> {
      int row = tablePostProcesses.convertRowIndexToModel(tablePostProcesses.getSelectedRow());

      if (row >= 0 && row < postProcessEventList.size()) {
        postProcessEventList.remove(row);
        MovieModuleManager.getInstance().getSettings().forceSaveSettings();
      }

      tablePostProcesses.adjustColumnPreferredWidths(5);
    });

    btnMoveProcessUp.addActionListener(e -> {
      int row = tablePostProcesses.convertRowIndexToModel(tablePostProcesses.getSelectedRow());

      if (row != -1 && row != 0) {
        ListUtils.swap(postProcessEventList, row, row - 1);
        MovieModuleManager.getInstance().getSettings().forceSaveSettings();

        tablePostProcesses.getSelectionModel().setSelectionInterval(row - 1, row - 1);
      }
    });

    btnMoveProcessDown.addActionListener(e -> {
      int row = tablePostProcesses.convertRowIndexToModel(tablePostProcesses.getSelectedRow());

      if (row != -1 && row != postProcessEventList.size() - 1) {
        ListUtils.swap(postProcessEventList, row, row + 1);
        MovieModuleManager.getInstance().getSettings().forceSaveSettings();

        tablePostProcesses.getSelectionModel().setSelectionInterval(row + 1, row + 1);
      }
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[grow]", "[]"));
    {
      JPanel panelProcess = new JPanel(new MigLayout("hidemode 1, insets 0", "[20lp!][600lp,grow]10lp[]", "[500lp,grow]"));
      JLabel lblProcess = new TmmLabel(TmmResourceBundle.getString("Settings.postprocessing"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelProcess, lblProcess, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#post-processing"));
      add(collapsiblePanel, "growx,wmin 0");

      {
        JScrollPane spProcesses = new JScrollPane();
        panelProcess.add(spProcesses, "cell 1 0,grow");
        tablePostProcesses = new PostProcessTable(postProcessEventList) {
          @Override
          protected void editButtonClicked(int row) {
            int index = convertRowIndexToModel(row);
            PostProcess postProcess = postProcessList.get(index);

            if (postProcess != null) {
              PostProcessDialog dialog = new MoviePostProcessingSettingsPanel.MoviePostProcessDialog();
              dialog.setProcess(postProcess);
              dialog.pack();
              dialog.setLocationRelativeTo(SettingsDialog.getInstance());
              dialog.setVisible(true);
              tablePostProcesses.adjustColumnPreferredWidths(5);
            }
          }
        };
        tablePostProcesses.configureScrollPane(spProcesses);
      }

      {
        btnAddProcess = new SquareIconButton(IconManager.ADD_INV);
        panelProcess.add(btnAddProcess, "flowy,cell 2 0,aligny top");
        btnRemoveProcess = new SquareIconButton(IconManager.REMOVE_INV);
        panelProcess.add(btnRemoveProcess, "cell 2 0");

        btnMoveProcessUp = new SquareIconButton(IconManager.ARROW_UP_INV);
        panelProcess.add(btnMoveProcessUp, "cell 2 0");

        btnMoveProcessDown = new SquareIconButton(IconManager.ARROW_DOWN_INV);
        panelProcess.add(btnMoveProcessDown, "cell 2 0");
      }
    }
  }

  /**
   * the class {@link MoviePostProcessDialog} is used to add/edit a post-process action
   *
   * @author Manuel Laggner
   */
  private class MoviePostProcessDialog extends PostProcessDialog {
    public MoviePostProcessDialog() {
      super();
    }

    @Override
    public void save() {
      if (StringUtils.isBlank(tfProcessName.getText()) || (StringUtils.isBlank(tfCommand.getText()) && StringUtils.isBlank(tfPath.getText()))) {
        showErrorToast(TmmResourceBundle.getString("message.missingitems"));
        return;
      }

      if (process == null) {
        // create a new post-process
        process = new PostProcess();
        postProcessEventList.add(process);
      }

      process.setName(tfProcessName.getText());
      process.setCommand(tfCommand.getText());
      process.setPath(tfPath.getText());
      process.setShowOutput(chkbxShowOutput.isSelected());
      process.setAbortOnFailure(chkbxAbortOnFailure.isSelected());

      MovieModuleManager.getInstance().getSettings().forceSaveSettings();

      setVisible(false);
    }
  }
}

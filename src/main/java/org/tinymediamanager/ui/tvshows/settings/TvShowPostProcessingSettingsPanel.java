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
package org.tinymediamanager.ui.tvshows.settings;

import static org.tinymediamanager.ui.TmmFontHelper.H3;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowSettings;
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
 * the class {@link TvShowPostProcessingSettingsPanel} holds the settings for post process action for TV shows
 *
 * @author Manuel Laggner
 */
public class TvShowPostProcessingSettingsPanel extends JPanel {

  private final EventList<PostProcess> postProcessTvShowEventList;
  private final EventList<PostProcess> postProcessEpisodeEventList;

  private TmmTable                     tablePostProcessesTvShow;
  private TmmTable                     tablePostProcessesEpisode;
  private JButton                      btnAddProcessTvShow;
  private JButton                      btnRemoveProcessTvShow;
  private JButton                      btnMoveProcessUpTvShow;
  private JButton                      btnMoveProcessDownTvShow;
  private JButton                      btnAddProcessEpisode;
  private JButton                      btnRemoveProcessEpisode;
  private JButton                      btnMoveProcessUpEpisode;
  private JButton                      btnMoveProcessDownEpisode;

  TvShowPostProcessingSettingsPanel() {
    TvShowSettings settings = TvShowModuleManager.getInstance().getSettings();

    postProcessTvShowEventList = GlazedLists.eventList(settings.getPostProcessTvShow());
    GlazedLists.syncEventListToList(postProcessTvShowEventList, settings.getPostProcessTvShow());

    postProcessEpisodeEventList = GlazedLists.eventList(settings.getPostProcessEpisode());
    GlazedLists.syncEventListToList(postProcessEpisodeEventList, settings.getPostProcessEpisode());

    initComponents();

    tablePostProcessesTvShow.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    tablePostProcessesEpisode.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // TV Show buttons
    btnAddProcessTvShow.addActionListener(e -> {
      PostProcessDialog dialog = new TvShowPostProcessDialog();
      dialog.pack();
      dialog.setLocationRelativeTo(SettingsDialog.getInstance());
      dialog.setVisible(true);
      tablePostProcessesTvShow.adjustColumnPreferredWidths(5);
    });

    btnRemoveProcessTvShow.addActionListener(e -> {
      int row = tablePostProcessesTvShow.convertRowIndexToModel(tablePostProcessesTvShow.getSelectedRow());
      if (row >= 0 && row < postProcessTvShowEventList.size()) {
        postProcessTvShowEventList.remove(row);
        TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
      }
      tablePostProcessesTvShow.adjustColumnPreferredWidths(5);
    });

    btnMoveProcessUpTvShow.addActionListener(e -> {
      int row = tablePostProcessesTvShow.convertRowIndexToModel(tablePostProcessesTvShow.getSelectedRow());
      if (row != -1 && row != 0) {
        ListUtils.swap(postProcessTvShowEventList, row, row - 1);
        TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
        tablePostProcessesTvShow.getSelectionModel().setSelectionInterval(row - 1, row - 1);
      }
    });

    btnMoveProcessDownTvShow.addActionListener(e -> {
      int row = tablePostProcessesTvShow.convertRowIndexToModel(tablePostProcessesTvShow.getSelectedRow());
      if (row != -1 && row != postProcessTvShowEventList.size() - 1) {
        ListUtils.swap(postProcessTvShowEventList, row, row + 1);
        TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
        tablePostProcessesTvShow.getSelectionModel().setSelectionInterval(row + 1, row + 1);
      }
    });

    // Episode buttons
    btnAddProcessEpisode.addActionListener(e -> {
      PostProcessDialog dialog = new EpisodePostProcessDialog();
      dialog.pack();
      dialog.setLocationRelativeTo(SettingsDialog.getInstance());
      dialog.setVisible(true);
      tablePostProcessesEpisode.adjustColumnPreferredWidths(5);
    });

    btnRemoveProcessEpisode.addActionListener(e -> {
      int row = tablePostProcessesEpisode.convertRowIndexToModel(tablePostProcessesEpisode.getSelectedRow());
      if (row >= 0 && row < postProcessEpisodeEventList.size()) {
        postProcessEpisodeEventList.remove(row);
        TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
      }
      tablePostProcessesEpisode.adjustColumnPreferredWidths(5);
    });

    btnMoveProcessUpEpisode.addActionListener(e -> {
      int row = tablePostProcessesEpisode.convertRowIndexToModel(tablePostProcessesEpisode.getSelectedRow());
      if (row != -1 && row != 0) {
        ListUtils.swap(postProcessEpisodeEventList, row, row - 1);
        TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
        tablePostProcessesEpisode.getSelectionModel().setSelectionInterval(row - 1, row - 1);
      }
    });

    btnMoveProcessDownEpisode.addActionListener(e -> {
      int row = tablePostProcessesEpisode.convertRowIndexToModel(tablePostProcessesEpisode.getSelectedRow());
      if (row != -1 && row != postProcessEpisodeEventList.size() - 1) {
        ListUtils.swap(postProcessEpisodeEventList, row, row + 1);
        TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
        tablePostProcessesEpisode.getSelectionModel().setSelectionInterval(row + 1, row + 1);
      }
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[grow]", "[]"));
    JPanel panelProcess = new JPanel(
        new MigLayout("hidemode 1, insets 0", "[20lp!][300lp:600lp,grow][]", "[][grow][150lp:200lp,grow][][][grow][150lp:200lp,grow]"));
    JLabel lblProcess = new TmmLabel(TmmResourceBundle.getString("Settings.postprocessing"), H3);
    CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelProcess, lblProcess, true);
    collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#post-processing"));
    add(collapsiblePanel, "growx,wmin 0");

    // TV Show table
    JScrollPane spTvShow = new JScrollPane();
    panelProcess.add(spTvShow, "cell 1 2,grow");
    tablePostProcessesTvShow = new PostProcessTable(postProcessTvShowEventList) {
      @Override
      protected void editButtonClicked(int row) {
        int index = convertRowIndexToModel(row);
        PostProcess postProcess = postProcessList.get(index);
        if (postProcess != null) {
          PostProcessDialog dialog = new TvShowPostProcessingSettingsPanel.TvShowPostProcessDialog();
          dialog.setProcess(postProcess);
          dialog.pack();
          dialog.setLocationRelativeTo(SettingsDialog.getInstance());
          dialog.setVisible(true);
          tablePostProcessesTvShow.adjustColumnPreferredWidths(5);
        }
      }
    };
    tablePostProcessesTvShow.configureScrollPane(spTvShow);

    btnAddProcessTvShow = new SquareIconButton(IconManager.ADD_INV);
    panelProcess.add(btnAddProcessTvShow, "flowy,cell 2 2,aligny top");
    btnRemoveProcessTvShow = new SquareIconButton(IconManager.REMOVE_INV);
    panelProcess.add(btnRemoveProcessTvShow, "cell 2 2");
    btnMoveProcessUpTvShow = new SquareIconButton(IconManager.ARROW_UP_INV);
    panelProcess.add(btnMoveProcessUpTvShow, "cell 2 2");
    btnMoveProcessDownTvShow = new SquareIconButton(IconManager.ARROW_DOWN_INV);
    panelProcess.add(btnMoveProcessDownTvShow, "cell 2 2");

    // Episode table
    JScrollPane spEpisode = new JScrollPane();
    panelProcess.add(spEpisode, "cell 1 6,grow");
    tablePostProcessesEpisode = new PostProcessTable(postProcessEpisodeEventList) {
      @Override
      protected void editButtonClicked(int row) {
        int index = convertRowIndexToModel(row);
        PostProcess postProcess = postProcessList.get(index);
        if (postProcess != null) {
          PostProcessDialog dialog = new EpisodePostProcessDialog();
          dialog.setProcess(postProcess);
          dialog.pack();
          dialog.setLocationRelativeTo(SettingsDialog.getInstance());
          dialog.setVisible(true);
          tablePostProcessesEpisode.adjustColumnPreferredWidths(5);
        }
      }
    };
    tablePostProcessesEpisode.configureScrollPane(spEpisode);

    btnAddProcessEpisode = new SquareIconButton(IconManager.ADD_INV);
    panelProcess.add(btnAddProcessEpisode, "flowy,cell 2 6,aligny top");
    btnRemoveProcessEpisode = new SquareIconButton(IconManager.REMOVE_INV);
    panelProcess.add(btnRemoveProcessEpisode, "cell 2 6");
    btnMoveProcessUpEpisode = new SquareIconButton(IconManager.ARROW_UP_INV);
    panelProcess.add(btnMoveProcessUpEpisode, "cell 2 6");
    btnMoveProcessDownEpisode = new SquareIconButton(IconManager.ARROW_DOWN_INV);
    panelProcess.add(btnMoveProcessDownEpisode, "cell 2 6");
  }

  // Dialog for TV Show post-process
  private class TvShowPostProcessDialog extends PostProcessDialog {
    public TvShowPostProcessDialog() {
      super();
    }

    @Override
    public void save() {
      if (StringUtils.isBlank(tfProcessName.getText()) || (StringUtils.isBlank(tfCommand.getText()) && StringUtils.isBlank(tfPath.getText()))) {
        showErrorToast(TmmResourceBundle.getString("message.missingitems"));
        return;
      }
      if (process == null) {
        process = new PostProcess();
        postProcessTvShowEventList.add(process);
      }
      process.setName(tfProcessName.getText());
      process.setCommand(tfCommand.getText());
      process.setPath(tfPath.getText());
      process.setShowOutput(chkbxShowOutput.isSelected());
      process.setAbortOnFailure(chkbxAbortOnFailure.isSelected());

      TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
      setVisible(false);
    }
  }

  // Dialog for Episode post-process
  private class EpisodePostProcessDialog extends PostProcessDialog {
    public EpisodePostProcessDialog() {
      super();
    }

    @Override
    public void save() {
      if (StringUtils.isBlank(tfProcessName.getText()) || (StringUtils.isBlank(tfCommand.getText()) && StringUtils.isBlank(tfPath.getText()))) {
        showErrorToast(TmmResourceBundle.getString("message.missingitems"));
        return;
      }
      if (process == null) {
        process = new PostProcess();
        postProcessEpisodeEventList.add(process);
      }
      process.setName(tfProcessName.getText());
      process.setCommand(tfCommand.getText());
      process.setPath(tfPath.getText());
      process.setShowOutput(chkbxShowOutput.isSelected());
      process.setAbortOnFailure(chkbxAbortOnFailure.isSelected());

      TvShowModuleManager.getInstance().getSettings().forceSaveSettings();
      setVisible(false);
    }
  }
}
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
import static org.tinymediamanager.ui.TmmFontHelper.L2;

import java.awt.event.ItemListener;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Property;
import org.tinymediamanager.addon.YtDlpAddon;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.TrailerQuality;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.core.tvshow.filenaming.TvShowTrailerNaming;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;

import net.miginfocom.swing.MigLayout;

public class TvShowTrailerOptionsSettingsPanel extends JPanel {

  private final TvShowSettings      settings                   = TvShowModuleManager.getInstance().getSettings();
  private final ItemListener        checkBoxListener;
  private final ButtonGroup         trailerFilenameButtonGroup = new ButtonGroup();

  private JComboBox<TrailerQuality> cbTrailerQuality;
  private JComboBox<MediaLanguages> cbTrailerLanguage;
  private JCheckBox                 checkBox;
  private JCheckBox                 chckbxAutomaticTrailerDownload;
  private JRadioButton              rbTrailerFilename1;
  private JRadioButton              rbTrailerFilename2;
  private JRadioButton              rbTrailerFilename3;
  private JRadioButton              rbTrailerFilename4;
  private JCheckBox                 chckbxUseYtDlp;
  private JLabel                    lblAutomaticTrailerDownloadHint;
  private JLabel                    lblTrailerQuality;
  private JLabel                    lblTrailerLanguage;

  TvShowTrailerOptionsSettingsPanel() {
    checkBoxListener = e -> checkChanges();

    // implement checkBoxListener for preset events
    settings.addPropertyChangeListener(evt -> {
      if ("preset".equals(evt.getPropertyName())) {
        buildCheckBoxes();
      }
    });

    // UI init
    initComponents();
    initDataBindings();

    buildCheckBoxes();

    chckbxUseYtDlp.setEnabled(new YtDlpAddon().isAvailable());
  }

  private void buildCheckBoxes() {
    clearSelection(rbTrailerFilename1, rbTrailerFilename2, rbTrailerFilename3, rbTrailerFilename4);

    // trailer filenames
    List<TvShowTrailerNaming> trailerFilenames = settings.getTrailerFilenames();
    if (trailerFilenames.contains(TvShowTrailerNaming.TVSHOW_TRAILER)) {
      rbTrailerFilename1.setSelected(true);
    }
    else if (trailerFilenames.contains(TvShowTrailerNaming.TVSHOWNAME_TRAILER)) {
      rbTrailerFilename2.setSelected(true);
    }
    else if (trailerFilenames.contains(TvShowTrailerNaming.TRAILERS_TVSHOWNAME_TRAILER)) {
      rbTrailerFilename3.setSelected(true);
    }
    else if (trailerFilenames.contains(TvShowTrailerNaming.TRAILER)) {
      rbTrailerFilename4.setSelected(true);
    }

    rbTrailerFilename1.addItemListener(checkBoxListener);
    rbTrailerFilename2.addItemListener(checkBoxListener);
    rbTrailerFilename3.addItemListener(checkBoxListener);
    rbTrailerFilename4.addItemListener(checkBoxListener);
  }

  private void clearSelection(JToggleButton... toggleButtons) {
    for (JToggleButton button : toggleButtons) {
      button.removeItemListener(checkBoxListener);
      button.setSelected(false);
    }
  }

  private void checkChanges() {
    // set trailer filenames
    settings.clearTrailerFilenames();
    if (rbTrailerFilename1.isSelected()) {
      settings.addTrailerFilename(TvShowTrailerNaming.TVSHOW_TRAILER);
    }
    if (rbTrailerFilename2.isSelected()) {
      settings.addTrailerFilename(TvShowTrailerNaming.TVSHOWNAME_TRAILER);
    }
    if (rbTrailerFilename3.isSelected()) {
      settings.addTrailerFilename(TvShowTrailerNaming.TRAILERS_TVSHOWNAME_TRAILER);
    }
    if (rbTrailerFilename4.isSelected()) {
      settings.addTrailerFilename(TvShowTrailerNaming.TRAILER);
    }
  }

  private void initComponents() {
    setLayout(new MigLayout("hidemode 0", "[grow]", "[]"));
    {
      JPanel panelOptions = new JPanel();
      panelOptions.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][][][][]")); // 16lp ~ width of the

      JLabel lblOptionsT = new TmmLabel(TmmResourceBundle.getString("Settings.advancedoptions"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelOptions, lblOptionsT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#advanced-options-2"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");

      {
        chckbxUseYtDlp = new JCheckBox(TmmResourceBundle.getString("Settings.trailer.ytdlp"));
        panelOptions.add(chckbxUseYtDlp, "cell 1 0 2 1");

        checkBox = new JCheckBox(TmmResourceBundle.getString("Settings.trailer.preferred"));
        panelOptions.add(checkBox, "cell 1 1 2 1");

        lblTrailerQuality = new JLabel(TmmResourceBundle.getString("Settings.trailer.quality"));
        panelOptions.add(lblTrailerQuality, "cell 2 2");

        cbTrailerQuality = new JComboBox();
        cbTrailerQuality.setModel(new DefaultComboBoxModel<>(TrailerQuality.values()));
        panelOptions.add(cbTrailerQuality, "cell 2 2");

        lblTrailerLanguage = new JLabel(TmmResourceBundle.getString("Settings.preferredLanguage"));
        panelOptions.add(lblTrailerLanguage, "cell 2 3");

        cbTrailerLanguage = new JComboBox(MediaLanguages.valuesSorted());
        panelOptions.add(cbTrailerLanguage, "cell 2 3");

        chckbxAutomaticTrailerDownload = new JCheckBox(TmmResourceBundle.getString("Settings.trailer.automaticdownload"));
        panelOptions.add(chckbxAutomaticTrailerDownload, "cell 2 4");

        lblAutomaticTrailerDownloadHint = new JLabel(TmmResourceBundle.getString("Settings.trailer.automaticdownload.hint"));
        panelOptions.add(lblAutomaticTrailerDownloadHint, "cell 2 5");
        TmmFontHelper.changeFont(lblAutomaticTrailerDownloadHint, L2);

        JPanel panelTrailerFilenames = new JPanel();
        panelOptions.add(panelTrailerFilenames, "cell 1 6 2 1");
        panelTrailerFilenames.setLayout(new MigLayout("insets 0", "[][]", "[][][]"));

        JLabel lblTrailerFileNaming = new JLabel(TmmResourceBundle.getString("Settings.trailerFileNaming"));
        panelTrailerFilenames.add(lblTrailerFileNaming, "cell 0 0");

        rbTrailerFilename1 = new JRadioButton("tvshow-trailer.*");
        trailerFilenameButtonGroup.add(rbTrailerFilename1);
        panelTrailerFilenames.add(rbTrailerFilename1, "cell 1 0");

        rbTrailerFilename2 = new JRadioButton(TmmResourceBundle.getString("Settings.trailer.tvshowtitle") + "-trailer.*");
        trailerFilenameButtonGroup.add(rbTrailerFilename2);
        panelTrailerFilenames.add(rbTrailerFilename2, "cell 1 1");

        rbTrailerFilename3 = new JRadioButton("trailers/" + TmmResourceBundle.getString("Settings.trailer.tvshowtitle") + "-trailer.*");
        trailerFilenameButtonGroup.add(rbTrailerFilename3);
        panelTrailerFilenames.add(rbTrailerFilename3, "cell 1 2");

        rbTrailerFilename4 = new JRadioButton("trailer.*");
        trailerFilenameButtonGroup.add(rbTrailerFilename4);
        panelTrailerFilenames.add(rbTrailerFilename4, "cell 1 3");
      }
    }
  }

  protected void initDataBindings() {
    Property tvShowSettingsBeanProperty_1 = BeanProperty.create("trailerQuality");
    Property jComboBoxBeanProperty_1 = BeanProperty.create("selectedItem");
    AutoBinding autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, tvShowSettingsBeanProperty_1, cbTrailerQuality,
        jComboBoxBeanProperty_1);
    autoBinding_2.bind();
    //
    Property tvShowSettingsBeanProperty_2 = BeanProperty.create("useTrailerPreference");
    Property jCheckBoxBeanProperty = BeanProperty.create("selected");
    AutoBinding autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, tvShowSettingsBeanProperty_2, checkBox,
        jCheckBoxBeanProperty);
    autoBinding_3.bind();
    //
    Property tvShowSettingsBeanProperty_3 = BeanProperty.create("automaticTrailerDownload");
    AutoBinding autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, tvShowSettingsBeanProperty_3,
        chckbxAutomaticTrailerDownload, jCheckBoxBeanProperty);
    autoBinding_4.bind();
    //
    Property tvShowSettingsBeanProperty_4 = BeanProperty.create("useYtDlp");
    AutoBinding autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, tvShowSettingsBeanProperty_4, chckbxUseYtDlp,
        jCheckBoxBeanProperty);
    autoBinding_5.bind();
    //
    Property jCheckBoxBeanProperty_1 = BeanProperty.create("enabled");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ, checkBox, jCheckBoxBeanProperty, chckbxAutomaticTrailerDownload,
        jCheckBoxBeanProperty_1);
    autoBinding.bind();
    //
    AutoBinding autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ, checkBox, jCheckBoxBeanProperty, lblAutomaticTrailerDownloadHint,
        jCheckBoxBeanProperty_1);
    autoBinding_6.bind();
    //
    AutoBinding autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ, checkBox, jCheckBoxBeanProperty, lblTrailerQuality,
        jCheckBoxBeanProperty_1);
    autoBinding_1.bind();
    //
    AutoBinding autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ, checkBox, jCheckBoxBeanProperty, cbTrailerQuality,
        jCheckBoxBeanProperty_1);
    autoBinding_7.bind();
    //
    AutoBinding autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ, checkBox, jCheckBoxBeanProperty, lblTrailerLanguage,
        jCheckBoxBeanProperty_1);
    autoBinding_8.bind();
    //
    AutoBinding autoBinding_9 = Bindings.createAutoBinding(UpdateStrategy.READ, checkBox, jCheckBoxBeanProperty, cbTrailerLanguage,
        jCheckBoxBeanProperty_1);
    autoBinding_9.bind();
    //
    Property tvShowSettingsBeanProperty = BeanProperty.create("trailerLanguage");
    AutoBinding autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, tvShowSettingsBeanProperty, cbTrailerLanguage,
        jComboBoxBeanProperty_1);
    autoBinding_10.bind();
  }
}

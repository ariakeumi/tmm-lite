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
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Property;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.core.movie.MovieSettingsDefaults;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.thirdparty.trakttv.MovieClearTraktTvTask;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.toast.TmmToastManager;
import org.tinymediamanager.ui.dialogs.SettingsDialog;

import net.miginfocom.swing.MigLayout;

/**
 * The class MovieSettingsPanel is used for displaying some movie related settings
 * 
 * @author Manuel Laggner
 */
public class MovieSettingsPanel extends JPanel {
  private final MovieSettings settings = MovieModuleManager.getInstance().getSettings();

  private JButton             btnClearTraktData;
  private JCheckBox           chckbxTraktSync;
  private JCheckBox           chckbxRenameAfterScrape;
  private JCheckBox           chckbxAutoUpdateOnStart;
  private JCheckBox           chckbxBuildImageCache;
  private JCheckBox           chckbxExtractArtworkFromVsmeta;
  private JCheckBox           chckbxRuntimeFromMi;
  private JButton             btnPresetKodi;
  private JButton             btnPresetXbmc;
  private JButton             btnPresetPlex;
  private JButton             btnPresetMediaPig;
  private JButton             btnPresetDefault;
  private JButton             btnPresetReadonly;
  private JCheckBox           chckbxUseMediainfoMetadata;

  private JCheckBox           chckbxTraktSyncWatched;
  private JCheckBox           chckbxTraktSyncRating;
  private JCheckBox           chckbxTraktSyncCollection;
  private JButton             btnPresetJellyfin;
  private JButton             btnPresetEmby;
  private JCheckBox           chckbxResetNewFlag;
  private JCheckBox           chckbxReadNomedia;

  public MovieSettingsPanel() {
    // UI initializations
    initComponents();
    initDataBindings();

    btnClearTraktData.addActionListener(e -> {
      Object[] options = { TmmResourceBundle.getString("Button.yes"), TmmResourceBundle.getString("Button.no") };
      int confirm = JOptionPane.showOptionDialog(SettingsDialog.getInstance(), TmmResourceBundle.getString("Settings.trakt.clearmovies.hint"),
          TmmResourceBundle.getString("Settings.trakt.clearmovies"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);
      if (confirm == JOptionPane.YES_OPTION) {
        TmmTask task = new MovieClearTraktTvTask();
        TmmTaskManager.getInstance().addUnnamedTask(task);
      }
    });

    btnPresetXbmc.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettingsForXbmc();
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.preset.message").replace("{}", "XBMC"));
    });
    btnPresetKodi.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettingsForKodi();
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.preset.message").replace("{}", "Kodi"));
    });
    btnPresetJellyfin.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettingsForJellyfin();
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.preset.message").replace("{}", "Jellyfin"));
    });
    btnPresetEmby.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettingsForEmby();
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.preset.message").replace("{}", "Emby"));
    });
    btnPresetPlex.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettingsForPlex();
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.preset.message").replace("{}", "Plex"));
    });
    btnPresetMediaPig.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettingsForMediaPig();
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.preset.message").replace("{}", "MediaPIG"));
    });
    btnPresetDefault.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettings();
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.preset.default.success.movie"));
    });
    btnPresetReadonly.addActionListener(evt -> {
      MovieSettingsDefaults.setDefaultSettingsForReadonly();
      TmmToastManager.showSuccessToast(this,
          TmmResourceBundle.getString("Settings.preset.message").replace("{}", TmmResourceBundle.getString("Settings.preset.readonly")));
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[grow]", "[][15lp!][][15lp!][]"));
    {
      JPanel panelAutomaticTasks = new JPanel();
      // 16lp ~ width of the checkbox
      panelAutomaticTasks.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][][][][15lp!][]"));

      JLabel lblAutomaticTasksT = new TmmLabel(TmmResourceBundle.getString("Settings.automatictasks"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelAutomaticTasks, lblAutomaticTasksT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#automatic-tasks"));
      {
        JPanel panelPresets = new JPanel(
            new MigLayout("hidemode 1, insets 0", "[20lp!][15lp][120lp:n][15lp!][120lp:n][15lp!][120lp:n][15lp!][120lp:n][grow]", "[][][][10lp!][]"));

        JLabel lblPresets = new TmmLabel(TmmResourceBundle.getString("Settings.preset"), H3);
        CollapsiblePanel collapsiblePanel_1 = new CollapsiblePanel(panelPresets, lblPresets, true);
        collapsiblePanel_1.addExtraTitleComponent(new DocsButton("/movies/settings#media-center-presets"));
        add(collapsiblePanel_1, "cell 0 0,growx,wmin 0");
        {
          JLabel lblPresetHintT = new JLabel(TmmResourceBundle.getString("Settings.preset.desc"));
          panelPresets.add(lblPresetHintT, "cell 1 0 7 1");
        }
        {
          btnPresetKodi = new JButton("Kodi v17+");
          panelPresets.add(btnPresetKodi, "cell 2 1,growx");

          btnPresetXbmc = new JButton("XBMC/Kodi <v17");
          panelPresets.add(btnPresetXbmc, "cell 4 1,growx");
        }
        {
          btnPresetPlex = new JButton("Plex");
          panelPresets.add(btnPresetPlex, "cell 2 2,growx");

          btnPresetJellyfin = new JButton("Jellyfin");
          panelPresets.add(btnPresetJellyfin, "cell 4 2,growx");

          btnPresetEmby = new JButton("Emby");
          panelPresets.add(btnPresetEmby, "cell 6 2,growx");

          btnPresetMediaPig = new JButton("MediaPIG");
          panelPresets.add(btnPresetMediaPig, "cell 8 2,growx");
        }
        {
          btnPresetDefault = new JButton(TmmResourceBundle.getString("Settings.preset.default"));
          btnPresetDefault.setToolTipText(TmmResourceBundle.getString("Settings.preset.default.desc"));
          btnPresetDefault.setIcon(IconManager.HINT);
          btnPresetDefault.setHorizontalTextPosition(SwingConstants.LEFT);
          panelPresets.add(btnPresetDefault, "cell 2 4,growx");

          btnPresetReadonly = new JButton(TmmResourceBundle.getString("Settings.preset.readonly"));
          btnPresetReadonly.setToolTipText(TmmResourceBundle.getString("Settings.preset.readonly.desc"));
          btnPresetReadonly.setIcon(IconManager.HINT);
          btnPresetReadonly.setHorizontalTextPosition(SwingConstants.LEFT);
          panelPresets.add(btnPresetReadonly, "cell 4 4,growx");
        }
      }
      add(collapsiblePanel, "cell 0 2,growx,wmin 0");
      {
        chckbxRenameAfterScrape = new JCheckBox(TmmResourceBundle.getString("Settings.movie.automaticrename"));
        panelAutomaticTasks.add(chckbxRenameAfterScrape, "cell 1 0 2 1");

        JLabel lblAutomaticRenameHint = new JLabel(IconManager.HINT);
        lblAutomaticRenameHint.setToolTipText(TmmResourceBundle.getString("Settings.movie.automaticrename.desc"));
        panelAutomaticTasks.add(lblAutomaticRenameHint, "cell 1 0 2 1");

        chckbxTraktSync = new JCheckBox(TmmResourceBundle.getString("Settings.trakt"));
        panelAutomaticTasks.add(chckbxTraktSync, "cell 1 2 2 1");

        btnClearTraktData = new JButton(TmmResourceBundle.getString("Settings.trakt.clearmovies"));
        panelAutomaticTasks.add(btnClearTraktData, "cell 1 2 2 1");

        chckbxTraktSyncCollection = new JCheckBox(TmmResourceBundle.getString("Settings.trakt.collection"));
        panelAutomaticTasks.add(chckbxTraktSyncCollection, "cell 2 3");

        chckbxTraktSyncWatched = new JCheckBox(TmmResourceBundle.getString("Settings.trakt.watched"));
        panelAutomaticTasks.add(chckbxTraktSyncWatched, "cell 2 4");

        chckbxTraktSyncRating = new JCheckBox(TmmResourceBundle.getString("Settings.trakt.rating"));
        panelAutomaticTasks.add(chckbxTraktSyncRating, "cell 2 5");

        chckbxAutoUpdateOnStart = new JCheckBox(TmmResourceBundle.getString("Settings.movie.automaticupdate"));
        panelAutomaticTasks.add(chckbxAutoUpdateOnStart, "cell 1 7 2 1");

        JLabel lblAutomaticUpdateHint = new JLabel(IconManager.HINT);
        lblAutomaticUpdateHint.setToolTipText(TmmResourceBundle.getString("Settings.movie.automaticupdate.desc"));
        panelAutomaticTasks.add(lblAutomaticUpdateHint, "cell 1 7 2 1");

      }
    }
    {
      JPanel panelMisc = new JPanel();
      panelMisc.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][][][]")); // 16lp ~ width of the

      JLabel lblMiscT = new TmmLabel(TmmResourceBundle.getString("Settings.misc"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelMisc, lblMiscT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#misc-settings"));
      add(collapsiblePanel, "cell 0 4,growx,wmin 0");

      chckbxResetNewFlag = new JCheckBox(TmmResourceBundle.getString("Settings.resetnewflag"));
      panelMisc.add(chckbxResetNewFlag, "cell 1 0 2 1");

      chckbxReadNomedia = new JCheckBox(TmmResourceBundle.getString("Settings.nomedia"));
      panelMisc.add(chckbxReadNomedia, "cell 1 1 2 1");

      chckbxUseMediainfoMetadata = new JCheckBox(TmmResourceBundle.getString("Settings.usemediainfometadata"));
      panelMisc.add(chckbxUseMediainfoMetadata, "cell 1 2 2 1");
      {
        chckbxExtractArtworkFromVsmeta = new JCheckBox(TmmResourceBundle.getString("Settings.extractartworkfromvsmeta"));
        panelMisc.add(chckbxExtractArtworkFromVsmeta, "cell 1 3 2 1");

        chckbxBuildImageCache = new JCheckBox(TmmResourceBundle.getString("Settings.imagecacheimport"));
        panelMisc.add(chckbxBuildImageCache, "cell 1 4 2 1");

        JLabel lblBuildImageCacheHint = new JLabel(IconManager.HINT);
        lblBuildImageCacheHint.setToolTipText(TmmResourceBundle.getString("Settings.imagecacheimporthint"));
        panelMisc.add(lblBuildImageCacheHint, "cell 1 4 2 1");

        chckbxRuntimeFromMi = new JCheckBox(TmmResourceBundle.getString("Settings.runtimefrommediafile"));
        panelMisc.add(chckbxRuntimeFromMi, "cell 1 5 2 1");
      }
    }
  }

  protected void initDataBindings() {
    Property movieSettingsBeanProperty_1 = BeanProperty.create("renameAfterScrape");
    Property jCheckBoxBeanProperty = BeanProperty.create("selected");
    AutoBinding autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_1, chckbxRenameAfterScrape,
        jCheckBoxBeanProperty);
    autoBinding_1.bind();
    //
    Property movieSettingsBeanProperty_2 = BeanProperty.create("syncTrakt");
    AutoBinding autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_2, chckbxTraktSync,
        jCheckBoxBeanProperty);
    autoBinding_2.bind();
    //
    Property movieSettingsBeanProperty_3 = BeanProperty.create("buildImageCacheOnImport");
    AutoBinding autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_3, chckbxBuildImageCache,
        jCheckBoxBeanProperty);
    autoBinding_3.bind();
    //
    Property movieSettingsBeanProperty_4 = BeanProperty.create("runtimeFromMediaInfo");
    AutoBinding autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_4, chckbxRuntimeFromMi,
        jCheckBoxBeanProperty);
    autoBinding_4.bind();
    //
    Property movieSettingsBeanProperty_11 = BeanProperty.create("extractArtworkFromVsmeta");
    AutoBinding autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_11,
        chckbxExtractArtworkFromVsmeta, jCheckBoxBeanProperty);
    autoBinding_11.bind();
    //
    Property movieSettingsBeanProperty_12 = BeanProperty.create("updateOnStart");
    AutoBinding autoBinding_12 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_12,
        chckbxAutoUpdateOnStart, jCheckBoxBeanProperty);
    autoBinding_12.bind();
    //
    Property movieSettingsBeanProperty_6 = BeanProperty.create("useMediainfoMetadata");
    AutoBinding autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_6,
        chckbxUseMediainfoMetadata, jCheckBoxBeanProperty);
    autoBinding_5.bind();
    //
    Property jCheckBoxBeanProperty_1 = BeanProperty.create("enabled");
    AutoBinding autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxTraktSync, jCheckBoxBeanProperty, chckbxTraktSyncCollection,
        jCheckBoxBeanProperty_1);
    autoBinding_6.bind();
    //
    AutoBinding autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxTraktSync, jCheckBoxBeanProperty, chckbxTraktSyncWatched,
        jCheckBoxBeanProperty_1);
    autoBinding_7.bind();
    //
    AutoBinding autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxTraktSync, jCheckBoxBeanProperty, chckbxTraktSyncRating,
        jCheckBoxBeanProperty_1);
    autoBinding_10.bind();
    //
    Property movieSettingsBeanProperty_7 = BeanProperty.create("syncTraktRating");
    AutoBinding autoBinding_18 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_7, chckbxTraktSyncRating,
        jCheckBoxBeanProperty);
    autoBinding_18.bind();
    //
    Property movieSettingsBeanProperty_10 = BeanProperty.create("syncTraktWatched");
    AutoBinding autoBinding_19 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_10, chckbxTraktSyncWatched,
        jCheckBoxBeanProperty);
    autoBinding_19.bind();
    //
    Property movieSettingsBeanProperty_18 = BeanProperty.create("syncTraktCollection");
    AutoBinding autoBinding_20 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_18,
        chckbxTraktSyncCollection, jCheckBoxBeanProperty);
    autoBinding_20.bind();
    //
    Property movieSettingsBeanProperty = BeanProperty.create("resetNewFlagOnUds");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty, chckbxResetNewFlag,
        jCheckBoxBeanProperty);
    autoBinding.bind();
    //
    Property movieSettingsBeanProperty_5 = BeanProperty.create("skipFoldersWithNomedia");
    AutoBinding autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_5, chckbxReadNomedia,
        jCheckBoxBeanProperty);
    autoBinding_8.bind();
  }
}

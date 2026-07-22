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

import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Property;
import org.tinymediamanager.core.AbstractModelObject;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.MovieRenamerProfile;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowMultiEpisodeStyle;
import org.tinymediamanager.core.tvshow.TvShowRenamer;
import org.tinymediamanager.core.tvshow.TvShowRenamerProfile;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.button.FlatButton;
import org.tinymediamanager.ui.components.button.JHintCheckBox;
import org.tinymediamanager.ui.components.label.LinkLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextArea;
import org.tinymediamanager.ui.components.textfield.TmmRoundTextArea;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;
import org.tinymediamanager.ui.tvshows.dialogs.TvShowJmteExplorerDialog;

import net.miginfocom.swing.MigLayout;

/**
 * The class TvShowRenamerSettingsPanel
 * 
 * @author Manuel Laggner
 */
public class TvShowRenamerSettingsPanel extends JPanel implements HierarchyListener {
  private final TvShowSettings                     settings          = TvShowModuleManager.getInstance().getSettings();
  private final List<String>                       spaceReplacements = new ArrayList<>(Arrays.asList("_", ".", "-"));
  private final List<String>                       colonReplacements = new ArrayList<>(Arrays.asList(" ", "-", "_", "∶"));

  private final TvShowRenamerProfileContainer      renamerProfileContainer;

  /*
   * UI components
   */
  private JCheckBox                                chckbxEnableTvShowFolderRename;
  private JCheckBox                                chckbxEnableSeasonFolderRename;
  private JCheckBox                                chckbxEnableEpisodeFileRename;
  private LinkLabel                                lblExampleDatasource;
  private JLabel                                   lblExampleFoldername;
  private JLabel                                   lblExampleFilename;
  private JComboBox<TvShowPreviewContainer>        cbTvShowForPreview;
  private JTextArea                                tfSeasonFolderName;
  private JHintCheckBox                            chckbxAsciiReplacement;
  private JHintCheckBox                            chckbxUnicodeReplacement;
  private JCheckBox                                chckbxShowFoldernameSpaceReplacement;
  private JComboBox                                cbShowFoldernameSpaceReplacement;
  private JCheckBox                                chckbxSeasonFoldernameSpaceReplacement;
  private JComboBox                                cbSeasonFoldernameSpaceReplacement;
  private JCheckBox                                chckbxFilenameSpaceReplacement;
  private JComboBox                                cbFilenameSpaceReplacement;
  private JComboBox<TvShowMultiEpisodeStyle>       cbMultiEpisodeStyle;
  private JComboBox<TvShowEpisodePreviewContainer> cbEpisodeForPreview;
  private JTextArea                                tfTvShowFolder;
  private JTextArea                                tfEpisodeFilename;
  private JComboBox                                cbColonReplacement;
  private JTextField                               tfFirstCharacter;
  private JCheckBox                                chckbxCleanupUnwanted;
  private JComboBox<String>                        cbProfile;
  private JButton                                  btnDeleteProfile;
  private JButton                                  btnResetTvShowPattern;
  private JButton                                  btnResetSeasonPattern;
  private JButton                                  btnResetFilePattern;
  private JCheckBox                                chckbxSpecialSeason;

  public TvShowRenamerSettingsPanel() {
    renamerProfileContainer = new TvShowRenamerProfileContainer();
    renamerProfileContainer.setProfile(settings.getRenamerProfile(TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE));

    // UI initializations
    initComponents();
    initDataBindings();

    // data init
    ActionListener profileActionListener = evt -> {
      String item = (String) cbProfile.getSelectedItem();
      renamerProfileContainer.setProfile(settings.getRenamerProfile(item));

      if (TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE.equals(item)) {
        btnDeleteProfile.setEnabled(false);
      }
      else {
        btnDeleteProfile.setEnabled(true);
      }

      createRenamerExample();
    };

    cbProfile.addActionListener(profileActionListener);
    for (String profileName : settings.getRenamerProfiles().keySet()) {
      cbProfile.addItem(profileName);
    }
    cbProfile.setSelectedItem(TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE);

    // the panel renamer
    DocumentListener documentListener = new DocumentListener() {
      @Override
      public void removeUpdate(DocumentEvent arg0) {
        createRenamerExample();
      }

      @Override
      public void insertUpdate(DocumentEvent arg0) {
        createRenamerExample();
      }

      @Override
      public void changedUpdate(DocumentEvent arg0) {
        createRenamerExample();
      }
    };

    tfTvShowFolder.getDocument().addDocumentListener(documentListener);
    tfSeasonFolderName.getDocument().addDocumentListener(documentListener);
    tfEpisodeFilename.getDocument().addDocumentListener(documentListener);
    tfFirstCharacter.getDocument().addDocumentListener(documentListener);

    cbTvShowForPreview.addActionListener(arg0 -> {
      buildAndInstallEpisodeArray();
      createRenamerExample();
    });

    // show folder name space replacement
    String replacement = renamerProfileContainer.getProfile().getRenamerShowPathnameSpaceReplacement();
    int index = spaceReplacements.indexOf(replacement);
    if (index >= 0) {
      cbShowFoldernameSpaceReplacement.setSelectedIndex(index);
    }

    // season folder name space replacement
    replacement = renamerProfileContainer.getProfile().getRenamerSeasonPathnameSpaceReplacement();
    index = spaceReplacements.indexOf(replacement);
    if (index >= 0) {
      cbSeasonFoldernameSpaceReplacement.setSelectedIndex(index);
    }

    // filename space replacement
    replacement = renamerProfileContainer.getProfile().getRenamerFilenameSpaceReplacement();
    index = spaceReplacements.indexOf(replacement);
    if (index >= 0) {
      cbFilenameSpaceReplacement.setSelectedIndex(index);
    }

    // colon replacement
    String colonReplacement = renamerProfileContainer.getProfile().getRenamerColonReplacement();
    index = this.colonReplacements.indexOf(colonReplacement);
    if (index >= 0) {
      cbColonReplacement.setSelectedIndex(index);
    }

    if (renamerProfileContainer.getProfile().isAsciiReplacement()) {
      chckbxUnicodeReplacement.setEnabled(false);
      cbColonReplacement.removeItem(colonReplacements.get(colonReplacements.size() - 1));
    }

    // event listener must be at the end
    ActionListener renamerActionListener = arg0 -> {
      createRenamerExample();
    };

    chckbxEnableTvShowFolderRename.addActionListener(renamerActionListener);
    chckbxEnableSeasonFolderRename.addActionListener(renamerActionListener);
    chckbxEnableEpisodeFileRename.addActionListener(renamerActionListener);
    chckbxShowFoldernameSpaceReplacement.addActionListener(renamerActionListener);
    chckbxSeasonFoldernameSpaceReplacement.addActionListener(renamerActionListener);
    chckbxFilenameSpaceReplacement.addActionListener(renamerActionListener);
    chckbxAsciiReplacement.addActionListener(renamerActionListener);
    cbEpisodeForPreview.addActionListener(arg0 -> createRenamerExample());
    cbMultiEpisodeStyle.addActionListener(renamerActionListener);
    cbShowFoldernameSpaceReplacement.addActionListener(renamerActionListener);
    cbSeasonFoldernameSpaceReplacement.addActionListener(renamerActionListener);
    cbFilenameSpaceReplacement.addActionListener(renamerActionListener);
    cbColonReplacement.addActionListener(renamerActionListener);

    chckbxAsciiReplacement.addActionListener(arg0 -> {
      if (chckbxAsciiReplacement.isSelected()) {
        cbColonReplacement.removeItem(ListUtils.getLast(colonReplacements));
        chckbxUnicodeReplacement.setSelected(false);
        chckbxUnicodeReplacement.setEnabled(false);
      }
      else {
        cbColonReplacement.addItem(ListUtils.getLast(colonReplacements));
        chckbxUnicodeReplacement.setEnabled(true);
      }

      createRenamerExample();
    });
    chckbxUnicodeReplacement.addActionListener(renamerActionListener);
  }

  private void initComponents() {
    setLayout(new MigLayout("hidemode 1", "[grow]", "[][15lp!][][15lp!][][15lp!][][15lp!][]"));
    {
      // profile panel
      JPanel panelProfile = new JPanel(new MigLayout("insets 0, hidemode 1", "[15lp][16lp!][200lp:350lp,grow]", "[][grow]"));

      JLabel lblProfileTitle = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.profile"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelProfile, lblProfileTitle, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#renamer-profiles"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");
      {
        JLabel lblProfileT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.profile"));
        panelProfile.add(lblProfileT, "flowx,cell 1 0");

        cbProfile = new JComboBox<>();
        panelProfile.add(cbProfile, "cell 1 0");

        JButton btnAddNewProfile = new FlatButton(IconManager.ADD_GRAY);
        btnAddNewProfile.setToolTipText(TmmResourceBundle.getString("Settings.renamer.profile.add"));
        btnAddNewProfile.addActionListener(e -> {
          String name = JOptionPane.showInputDialog(this, TmmResourceBundle.getString("Settings.renamer.profile.enter.name"),
              TmmResourceBundle.getString("Settings.renamer.profile.savedialog"), JOptionPane.PLAIN_MESSAGE);
          if (StringUtils.isNotBlank(name)) {
            name = name.trim();
            if (settings.getRenamerProfiles().containsKey(name)) {
              JOptionPane.showMessageDialog(this, TmmResourceBundle.getString("Settings.renamer.profile.duplicate.name"),
                  TmmResourceBundle.getString("Settings.renamer.profile.savedialog"), JOptionPane.WARNING_MESSAGE);
              return;
            }
            TvShowRenamerProfile profile = new TvShowRenamerProfile(name);
            settings.addRenamerProfile(profile);
            cbProfile.addItem(name);
            cbProfile.setSelectedItem(name);
          }
        });
        panelProfile.add(btnAddNewProfile, "cell 1 0");

        JButton btnCopyProfile = new FlatButton(IconManager.COPY_GRAY);
        btnCopyProfile.setToolTipText(TmmResourceBundle.getString("Settings.renamer.profile.copy"));
        btnCopyProfile.addActionListener(e -> {
          String name = JOptionPane.showInputDialog(this, TmmResourceBundle.getString("Settings.renamer.profile.enter.name"),
              TmmResourceBundle.getString("Settings.renamer.profile.savedialog"), JOptionPane.PLAIN_MESSAGE);
          if (StringUtils.isNotBlank(name)) {
            name = name.trim();
            if (settings.getRenamerProfiles().containsKey(name)) {
              JOptionPane.showMessageDialog(this, TmmResourceBundle.getString("Settings.renamer.profile.duplicate.name"),
                  TmmResourceBundle.getString("Settings.renamer.profile.savedialog"), JOptionPane.WARNING_MESSAGE);
              return;
            }
            TvShowRenamerProfile profile = new TvShowRenamerProfile(name, renamerProfileContainer.getProfile());
            settings.addRenamerProfile(profile);
            cbProfile.addItem(name);
            cbProfile.setSelectedItem(name);
          }
        });
        panelProfile.add(btnCopyProfile, "cell 1 0");

        btnDeleteProfile = new FlatButton(IconManager.DELETE_GRAY);
        btnDeleteProfile.setToolTipText(
            TmmResourceBundle.getString("Settings.renamer.profile.delete") + "\n" + TmmResourceBundle.getString("Settings.renamer.profile.hint"));
        btnDeleteProfile.addActionListener(e -> {
          String profileName = (String) cbProfile.getSelectedItem();

          if (profileName != null && !MovieRenamerProfile.DEFAULT_RENAMER_PROFILE.equals(profileName)) {
            String message = TmmResourceBundle.getString("Settings.renamer.profile.confirm.delete");
            int result = JOptionPane.showConfirmDialog(this, message.replace("{0}", profileName),
                TmmResourceBundle.getString("Settings.renamer.profile.delete"), JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
              settings.deleteRenamerProfile(profileName);
              cbProfile.setSelectedItem(TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE);
              cbProfile.removeItem(profileName);

              createRenamerExample();
            }
          }
        });

        panelProfile.add(btnDeleteProfile, "cell 1 0");
      }
      {
        JTextArea taProfileHint = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.renamer.profile.desc"));
        panelProfile.add(taProfileHint, "cell 2 1,wmin 0,grow");
      }
    }
    {
      JPanel panelPatterns = new JPanel(new MigLayout("insets 0, hidemode 1", "[20lp!][15lp][][400lp,grow][grow]", "[][][][][][]"));

      JLabel lblPatternsT = new TmmLabel(TmmResourceBundle.getString("Settings.tvshow.renamer.title"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelPatterns, lblPatternsT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("tvshows/renamer#renamer-pattern"));
      add(collapsiblePanel, "cell 0 2,growx,wmin 0");

      {
        chckbxEnableTvShowFolderRename = new JCheckBox(TmmResourceBundle.getString("Settings.tvshowfoldername"));
        chckbxEnableTvShowFolderRename.setToolTipText(TmmResourceBundle.getString("Settings.renamer.enabletvshowfolderrename"));
        panelPatterns.add(chckbxEnableTvShowFolderRename, "cell 1 0 2 1");

        tfTvShowFolder = new TmmRoundTextArea();
        panelPatterns.add(tfTvShowFolder, "cell 3 0, growx, wmin 0");

        btnResetTvShowPattern = new FlatButton(IconManager.UNDO_GRAY);
        btnResetTvShowPattern.setToolTipText(TmmResourceBundle.getString("Settings.renamer.reverttodefault"));
        btnResetTvShowPattern.addActionListener(l -> tfTvShowFolder.setText(TvShowSettings.DEFAULT_RENAMER_FOLDER_PATTERN));
        panelPatterns.add(btnResetTvShowPattern, "cell 3 0, aligny top");

        JLabel lblDefault = new JLabel(TmmResourceBundle.getString("Settings.default"));
        panelPatterns.add(lblDefault, "cell 1 1 2 1,alignx right");
        TmmFontHelper.changeFont(lblDefault, L2);

        JTextArea tpDefaultFolderPattern = new ReadOnlyTextArea(TvShowSettings.DEFAULT_RENAMER_FOLDER_PATTERN);
        panelPatterns.add(tpDefaultFolderPattern, "cell 3 1,growx,wmin 0");
        TmmFontHelper.changeFont(tpDefaultFolderPattern, L2);
      }
      {
        chckbxEnableSeasonFolderRename = new JCheckBox(TmmResourceBundle.getString("Settings.tvshowseasonfoldername"));
        chckbxEnableSeasonFolderRename.setToolTipText(TmmResourceBundle.getString("Settings.renamer.enableseasonfolderrename"));
        panelPatterns.add(chckbxEnableSeasonFolderRename, "cell 1 2 2 1");

        tfSeasonFolderName = new TmmRoundTextArea();
        panelPatterns.add(tfSeasonFolderName, "cell 3 2, growx, wmin 0");

        btnResetSeasonPattern = new FlatButton(IconManager.UNDO_GRAY);
        btnResetSeasonPattern.setToolTipText(TmmResourceBundle.getString("Settings.renamer.reverttodefault"));
        btnResetSeasonPattern.addActionListener(l -> tfSeasonFolderName.setText(TvShowSettings.DEFAULT_RENAMER_SEASON_PATTERN));
        panelPatterns.add(btnResetSeasonPattern, "cell 3 2, aligny top");

        JLabel lblDefault = new JLabel(TmmResourceBundle.getString("Settings.default"));
        panelPatterns.add(lblDefault, "cell 1 3 2 1,alignx right");
        TmmFontHelper.changeFont(lblDefault, L2);

        JTextArea tpDefaultSeasonPattern = new ReadOnlyTextArea(TvShowSettings.DEFAULT_RENAMER_SEASON_PATTERN);
        panelPatterns.add(tpDefaultSeasonPattern, "cell 3 3,growx,wmin 0");
        TmmFontHelper.changeFont(tpDefaultSeasonPattern, L2);
      }
      {
        chckbxEnableEpisodeFileRename = new JCheckBox(TmmResourceBundle.getString("Settings.tvshowfilename"));
        chckbxEnableEpisodeFileRename.setToolTipText(TmmResourceBundle.getString("Settings.renamer.enableepisodefilerename"));
        panelPatterns.add(chckbxEnableEpisodeFileRename, "cell 1 4 2 1");

        tfEpisodeFilename = new TmmRoundTextArea();
        panelPatterns.add(tfEpisodeFilename, "cell 3 4, growx, wmin 0");

        btnResetFilePattern = new FlatButton(IconManager.UNDO_GRAY);
        btnResetFilePattern.setToolTipText(TmmResourceBundle.getString("Settings.renamer.reverttodefault"));
        btnResetFilePattern.addActionListener(l -> tfEpisodeFilename.setText(TvShowSettings.DEFAULT_RENAMER_FILE_PATTERN));
        panelPatterns.add(btnResetFilePattern, "cell 3 4, aligny top");

        JLabel lblDefault = new JLabel(TmmResourceBundle.getString("Settings.default"));
        panelPatterns.add(lblDefault, "cell 1 5 2 1,alignx right");
        TmmFontHelper.changeFont(lblDefault, L2);

        JTextArea tpDefaultFilePattern = new ReadOnlyTextArea(TvShowSettings.DEFAULT_RENAMER_FILE_PATTERN);
        panelPatterns.add(tpDefaultFilePattern, "cell 3 5,growx,wmin 0");
        TmmFontHelper.changeFont(tpDefaultFilePattern, L2);
      }
      {
        // hint row removed - checkboxes now provide enable/disable functionality
      }
      {
        JButton btnJmteExplorer = new JButton(TmmResourceBundle.getString("jmteexplorer.title"));
        btnJmteExplorer.addActionListener(e -> {
          TvShowJmteExplorerDialog dialog = new TvShowJmteExplorerDialog((Window) this.getTopLevelAncestor());
          dialog.setVisible(true);
        });
        panelPatterns.add(btnJmteExplorer, "cell 4 0");
      }
    }
    {
      JPanel panelAdvancedOptions = new JPanel();
      panelAdvancedOptions.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][]"));

      JLabel lblAdvancedOptions = new TmmLabel(TmmResourceBundle.getString("Settings.advancedoptions"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelAdvancedOptions, lblAdvancedOptions, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/renamer#advanced-options"));
      add(collapsiblePanel, "cell 0 4,growx");

      chckbxSpecialSeason = new JCheckBox(TmmResourceBundle.getString("tvshow.renamer.specialseason"));
      panelAdvancedOptions.add(chckbxSpecialSeason, "cell 1 0 2 1");

      {
        chckbxCleanupUnwanted = new JCheckBox(TmmResourceBundle.getString("Settings.cleanupfiles"));
        panelAdvancedOptions.add(chckbxCleanupUnwanted, "cell 1 1 2 1");
      }
      JLabel lblMultiEpisodeStyle = new JLabel(TmmResourceBundle.getString("Settings.tvshow.renamer.multiepisodestyle"));
      panelAdvancedOptions.add(lblMultiEpisodeStyle, "flowx,cell 1 2 2 1");
      {
        cbMultiEpisodeStyle = new JComboBox<>();
        panelAdvancedOptions.add(cbMultiEpisodeStyle, "cell 1 2 2 1");
        cbMultiEpisodeStyle.setModel(new DefaultComboBoxModel<>(TvShowMultiEpisodeStyle.values()));
      }
    }
    {
      JPanel panelReplacements = new JPanel();
      panelReplacements.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][][][][]"));

      JLabel lblReplacementsT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.replacements"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelReplacements, lblReplacementsT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/renamer#replacements"));
      add(collapsiblePanel, "cell 0 6,growx");

      {
        chckbxShowFoldernameSpaceReplacement = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.showfolderspacereplacement"));
        panelReplacements.add(chckbxShowFoldernameSpaceReplacement, "flowx,cell 1 0 2 1");
        chckbxShowFoldernameSpaceReplacement.setToolTipText(TmmResourceBundle.getString("Settings.renamer.folderspacereplacement.hint"));

        cbShowFoldernameSpaceReplacement = new JComboBox(spaceReplacements.toArray());
        panelReplacements.add(cbShowFoldernameSpaceReplacement, "cell 1 0 2 1");
      }
      {
        chckbxSeasonFoldernameSpaceReplacement = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.seasonfolderspacereplacement"));
        panelReplacements.add(chckbxSeasonFoldernameSpaceReplacement, "flowx,cell 1 1 2 1");
        chckbxSeasonFoldernameSpaceReplacement.setToolTipText(TmmResourceBundle.getString("Settings.renamer.folderspacereplacement.hint"));

        cbSeasonFoldernameSpaceReplacement = new JComboBox(spaceReplacements.toArray());
        panelReplacements.add(cbSeasonFoldernameSpaceReplacement, "cell 1 1 2 1");
      }
      {
        chckbxFilenameSpaceReplacement = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.spacereplacement"));
        panelReplacements.add(chckbxFilenameSpaceReplacement, "flowx,cell 1 2 2 1");
        chckbxFilenameSpaceReplacement.setToolTipText(TmmResourceBundle.getString("Settings.renamer.spacereplacement.hint"));

        cbFilenameSpaceReplacement = new JComboBox(spaceReplacements.toArray());
        panelReplacements.add(cbFilenameSpaceReplacement, "cell 1 2 2 1");
      }
      {
        JLabel lblFirstCharacterT = new JLabel(TmmResourceBundle.getString("Settings.renamer.firstnumbercharacterreplacement"));
        panelReplacements.add(lblFirstCharacterT, "flowx,cell 1 3 2 1");

        tfFirstCharacter = new JTextField();
        panelReplacements.add(tfFirstCharacter, "cell 1 3 2 1");
        tfFirstCharacter.setColumns(2);
      }
      {
        JLabel lblColonReplacement = new JLabel(TmmResourceBundle.getString("Settings.renamer.colonreplacement"));
        panelReplacements.add(lblColonReplacement, "flowx,cell 1 4 2 1");
        lblColonReplacement.setToolTipText(TmmResourceBundle.getString("Settings.renamer.colonreplacement.hint"));

        cbColonReplacement = new JComboBox(colonReplacements.toArray());
        panelReplacements.add(cbColonReplacement, "cell 1 4 2 1");
      }
      {
        chckbxAsciiReplacement = new JHintCheckBox(TmmResourceBundle.getString("Settings.renamer.asciireplacement"));

        String examples = "<html>" + TmmResourceBundle.getString("Settings.renamer.examples") + "<br>";

        examples += "Ä  →  Ae<br>";
        examples += "Ö  →  Oe<br>";
        examples += "ß  →  ss<br>";
        examples += "Á  →  A<br>";
        examples += "Æ  →  Ae<br>";
        examples += "…</html>";

        chckbxAsciiReplacement.setToolTipText(examples);
        panelReplacements.add(chckbxAsciiReplacement, "cell 1 5 2 1");
      }
      {
        chckbxUnicodeReplacement = new JHintCheckBox(TmmResourceBundle.getString("Settings.renamer.unicodereplacement"));

        String examples = "<html>" + TmmResourceBundle.getString("Settings.renamer.examples") + "<br>";
        examples += ":  →  ∶<br>";
        examples += "/  →  ⁄<br>";
        examples += "&gt;  →  ›<br>";
        examples += "…</html>";

        chckbxUnicodeReplacement.setToolTipText(examples);
        panelReplacements.add(chckbxUnicodeReplacement, "cell 1 6 2 1");
      }
    }
    {
      JPanel panelExample = new JPanel();
      panelExample.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][][300lp,grow]", "[][]10lp![][][]"));

      JLabel lblAdvancedOptions = new TmmLabel(TmmResourceBundle.getString("Settings.example"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelExample, lblAdvancedOptions, true);
      add(collapsiblePanel, "cell 0 8,growx, wmin 0");
      {
        JLabel lblExampleTvShowT = new JLabel(TmmResourceBundle.getString("metatag.tvshow"));
        panelExample.add(lblExampleTvShowT, "cell 1 0");

        cbTvShowForPreview = new JComboBox();
        panelExample.add(cbTvShowForPreview, "cell 2 0,growx,wmin 0");
      }
      {
        JLabel lblExampleEpisodeT = new JLabel(TmmResourceBundle.getString("metatag.episode"));
        panelExample.add(lblExampleEpisodeT, "cell 1 1");

        cbEpisodeForPreview = new JComboBox();
        panelExample.add(cbEpisodeForPreview, "cell 2 1,growx,wmin 0");
      }
      {
        JLabel lblDatasourceT = new TmmLabel(TmmResourceBundle.getString("metatag.datasource"));
        panelExample.add(lblDatasourceT, "cell 1 2");

        lblExampleDatasource = new LinkLabel("");
        panelExample.add(lblExampleDatasource, "cell 2 2, wmin 0");

        JLabel lblFoldernameT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.folder"));
        panelExample.add(lblFoldernameT, "cell 1 3");

        lblExampleFoldername = new JLabel("");
        panelExample.add(lblExampleFoldername, "cell 2 3, wmin 0");

        JLabel lblFilenameT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.file"));
        panelExample.add(lblFilenameT, "cell 1 4");

        lblExampleFilename = new JLabel("");
        panelExample.add(lblExampleFilename, "cell 2 4, wmin 0");
      }
    }
  }

  @Override
  public void hierarchyChanged(HierarchyEvent arg0) {
    if (isShowing()) {
      buildAndInstallTvShowArray();
      buildAndInstallEpisodeArray();
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    addHierarchyListener(this);
  }

  @Override
  public void removeNotify() {
    removeHierarchyListener(this);
    super.removeNotify();
  }

  private void buildAndInstallTvShowArray() {
    cbTvShowForPreview.removeAllItems();
    List<TvShow> allTvShows = new ArrayList<>(TvShowModuleManager.getInstance().getTvShowList().getTvShows());
    allTvShows.sort(new TvShowComparator());
    TvShow sel = TvShowUIModule.getInstance().getSelectionModel().getSelectedTvShow();
    for (TvShow tvShow : allTvShows) {
      TvShowPreviewContainer container = new TvShowPreviewContainer();
      container.tvShow = tvShow;
      cbTvShowForPreview.addItem(container);
      if (sel != null && tvShow.equals(sel)) {
        cbTvShowForPreview.setSelectedItem(container);
      }
    }
  }

  private void buildAndInstallEpisodeArray() {
    cbEpisodeForPreview.removeAllItems();
    Object obj = cbTvShowForPreview.getSelectedItem();
    if (obj instanceof TvShowPreviewContainer) {
      TvShowPreviewContainer c = (TvShowPreviewContainer) cbTvShowForPreview.getSelectedItem();
      List<TvShowEpisode> sel = TvShowUIModule.getInstance().getSelectionModel().getSelectedEpisodes(true);
      for (TvShowEpisode episode : c.tvShow.getEpisodes()) {
        TvShowEpisodePreviewContainer container = new TvShowEpisodePreviewContainer();
        container.episode = episode;
        cbEpisodeForPreview.addItem(container);
        if (sel != null && sel.size() > 0 && episode.equals(sel.get(0))) {
          cbEpisodeForPreview.setSelectedItem(container);
        }
      }
    }
  }

  private void createRenamerExample() {
    // need to start it async, that binding will transfer changes to settings first
    SwingUtilities.invokeLater(() -> {
      TvShow tvShow = null;
      TvShowEpisode episode = null;

      if (cbTvShowForPreview.getSelectedItem() instanceof TvShowPreviewContainer) {
        TvShowPreviewContainer container = (TvShowPreviewContainer) cbTvShowForPreview.getSelectedItem();
        tvShow = container.tvShow;
      }

      if (cbEpisodeForPreview.getSelectedItem() instanceof TvShowEpisodePreviewContainer) {
        TvShowEpisodePreviewContainer container = (TvShowEpisodePreviewContainer) cbEpisodeForPreview.getSelectedItem();
        episode = container.episode;
      }

      if (tvShow != null && episode != null) {
        String tvShowDir = "";
        if (renamerProfileContainer.getProfile().isRenamerTvShowFoldernameEnabled() && StringUtils.isNotBlank(tfTvShowFolder.getText())) {
          tvShowDir = TvShowRenamer.getTvShowFoldername(tfTvShowFolder.getText(), tvShow, renamerProfileContainer.getProfile());
        }

        String newFilenameAndPath = "";
        if (renamerProfileContainer.getProfile().isRenamerFilenameEnabled() && StringUtils.isNotBlank(tfEpisodeFilename.getText())) {
          MediaFile episodeMf = TvShowRenamer
              .generateEpisodeFilenames(tfEpisodeFilename.getText(), tvShow, episode.getMainVideoFile(),
                  FilenameUtils.getBaseName(episode.getMainVideoFile().getFilename()), renamerProfileContainer.getProfile())
              .get(0);

          newFilenameAndPath = episodeMf.getFile().toString().replace(episode.getTvShow().getPath() + File.separator, "");
        }

        lblExampleDatasource.setText(tvShow.getDataSource());
        lblExampleFoldername.setText(tvShowDir.replace(tvShow.getDataSource() + File.separator, ""));
        lblExampleFilename.setText(newFilenameAndPath);
      }
      else {
        lblExampleDatasource.setText("");
        lblExampleFoldername.setText("");
        lblExampleFilename.setText("");
      }
    });
  }

  /*************************************************************
   * helper classes
   *************************************************************/
  private static class TvShowPreviewContainer {
    TvShow tvShow;

    @Override
    public String toString() {
      return tvShow.getTitle();
    }
  }

  private static class TvShowEpisodePreviewContainer {
    TvShowEpisode episode;

    @Override
    public String toString() {
      return episode.getSeason() + "." + episode.getEpisode() + " " + episode.getTitle();
    }
  }

  private static class TvShowComparator implements Comparator<TvShow> {
    @Override
    public int compare(TvShow arg0, TvShow arg1) {
      return arg0.getTitle().compareTo(arg1.getTitle());
    }
  }

  @SuppressWarnings("unused")
  private class TvShowRenamerExample extends AbstractModelObject {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^\\$\\{(.*?)([\\}\\[;\\.]+.*)");

    private final String         token;
    private final String         completeToken;

    private String               longToken     = "";
    private String               description;
    private String               example       = "";

    public TvShowRenamerExample(String token) {
      this.token = token;
      this.completeToken = createCompleteToken();
      try {
        this.description = TmmResourceBundle.getString("Settings.tvshow.renamer." + token);
      }
      catch (Exception e) {
        this.description = "";
      }
    }

    private String createCompleteToken() {
      String result = token;

      Matcher matcher = TOKEN_PATTERN.matcher(token);
      if (matcher.find() && matcher.groupCount() > 1) {
        String alias = matcher.group(1);
        String sourceToken = TvShowRenamer.getTokenMap().get(alias);

        if (StringUtils.isNotBlank(sourceToken)) {
          result = "<html>" + token + "<br>${" + sourceToken + matcher.group(2) + "</html>";
          longToken = "${" + sourceToken + matcher.group(2);
        }
      }
      return result;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getExample() {
      return example;
    }

    public void setExample(String example) {
      this.example = example;
    }

    private void createExample(TvShowEpisode episode) {
      String oldValue = example;
      if (episode == null) {
        example = "";
      }
      else {
        example = TvShowRenamer.createDestination(token, Collections.singletonList(episode), renamerProfileContainer.getProfile());
      }
      firePropertyChange("example", oldValue, example);
    }
  }

  /**
   * A container to hold the profile and fire property change events for the data bindings.
   */
  public static class TvShowRenamerProfileContainer extends AbstractModelObject {
    private TvShowRenamerProfile profile;

    public TvShowRenamerProfile getProfile() {
      return profile;
    }

    public void setProfile(TvShowRenamerProfile profile) {
      TvShowRenamerProfile oldValue = this.profile;
      this.profile = profile;
      firePropertyChange("profile", oldValue, profile);
    }
  }

  protected void initDataBindings() {
    Property containerBeanProperty = BeanProperty.create("profile.asciiReplacement");
    Property jCheckBoxBeanProperty = BeanProperty.create("selected");
    AutoBinding autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, containerBeanProperty,
        chckbxAsciiReplacement, jCheckBoxBeanProperty);
    autoBinding_5.bind();
    //
    Property tvShowSettingsBeanProperty = BeanProperty.create("profile.renamerShowPathnameSpaceSubstitution");
    AutoBinding autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty,
        chckbxShowFoldernameSpaceReplacement, jCheckBoxBeanProperty);
    autoBinding_4.bind();
    //
    Property tvShowSettingsBeanProperty_7 = BeanProperty.create("profile.renamerSeasonPathnameSpaceSubstitution");
    AutoBinding autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_7,
        chckbxSeasonFoldernameSpaceReplacement, jCheckBoxBeanProperty);
    autoBinding_6.bind();
    //
    Property tvShowSettingsBeanProperty_8 = BeanProperty.create("profile.renamerFilenameSpaceSubstitution");
    AutoBinding autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_8,
        chckbxFilenameSpaceReplacement, jCheckBoxBeanProperty);
    autoBinding_7.bind();
    //
    Property tvShowSettingsBeanProperty_1 = BeanProperty.create("profile.renamerTvShowFoldername");
    Property jTextFieldBeanProperty_1 = BeanProperty.create("text");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_1,
        tfTvShowFolder, jTextFieldBeanProperty_1);
    autoBinding.bind();
    //
    Property tvShowSettingsBeanProperty_10 = BeanProperty.create("profile.renamerMultiEpisodeStyle");
    Property jComboBoxBeanProperty_2 = BeanProperty.create("selectedItem");
    AutoBinding autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_10,
        cbMultiEpisodeStyle, jComboBoxBeanProperty_2);
    autoBinding_11.bind();
    //
    Property tvShowSettingsBeanProperty_2 = BeanProperty.create("profile.renamerFilename");
    Property jTextFieldBeanProperty_2 = BeanProperty.create("text");
    AutoBinding autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_2,
        tfEpisodeFilename, jTextFieldBeanProperty_2);
    autoBinding_1.bind();
    //
    Property tvShowSettingsBeanProperty_3 = BeanProperty.create("profile.renamerSeasonFoldername");
    Property jTextFieldBeanProperty = BeanProperty.create("text");
    AutoBinding autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_3,
        tfSeasonFolderName, jTextFieldBeanProperty);
    autoBinding_2.bind();
    //
    Property tvShowSettingsBeanProperty_4 = BeanProperty.create("profile.renamerFirstCharacterNumberReplacement");
    Property jTextFieldBeanProperty_3 = BeanProperty.create("text");
    AutoBinding autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_4,
        tfFirstCharacter, jTextFieldBeanProperty_3);
    autoBinding_3.bind();
    //
    Property tvShowSettingsBeanProperty_6 = BeanProperty.create("profile.renamerCleanupUnwanted");
    AutoBinding autoBinding_9 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_6,
        chckbxCleanupUnwanted, jCheckBoxBeanProperty);
    autoBinding_9.bind();
    //
    Property tvShowSettingsBeanProperty_9 = BeanProperty.create("profile.unicodeReplacement");
    AutoBinding autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer, tvShowSettingsBeanProperty_9,
        chckbxUnicodeReplacement, jCheckBoxBeanProperty);
    autoBinding_10.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty_1 = BeanProperty.create("profile.renamerShowPathnameSpaceReplacement");
    AutoBinding autoBinding_12 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty_1, cbShowFoldernameSpaceReplacement, jComboBoxBeanProperty_2);
    autoBinding_12.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty = BeanProperty.create("profile.renamerSeasonPathnameSpaceReplacement");
    AutoBinding autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty, cbSeasonFoldernameSpaceReplacement, jComboBoxBeanProperty_2);
    autoBinding_8.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty_2 = BeanProperty.create("profile.renamerFilenameSpaceReplacement");
    AutoBinding autoBinding_13 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty_2, cbFilenameSpaceReplacement, jComboBoxBeanProperty_2);
    autoBinding_13.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty_3 = BeanProperty.create("profile.renamerColonReplacement");
    AutoBinding autoBinding_14 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty_3, cbColonReplacement, jComboBoxBeanProperty_2);
    autoBinding_14.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty_4 = BeanProperty.create("profile.renamerTvShowFoldernameEnabled");
    AutoBinding autoBinding_15 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty_4, chckbxEnableTvShowFolderRename, jCheckBoxBeanProperty);
    autoBinding_15.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty_5 = BeanProperty.create("profile.renamerSeasonFoldernameEnabled");
    AutoBinding autoBinding_16 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty_5, chckbxEnableSeasonFolderRename, jCheckBoxBeanProperty);
    autoBinding_16.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty_6 = BeanProperty.create("profile.renamerFilenameEnabled");
    AutoBinding autoBinding_17 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty_6, chckbxEnableEpisodeFileRename, jCheckBoxBeanProperty);
    autoBinding_17.bind();
    //
    Property tmmRoundTextAreaBeanProperty = BeanProperty.create("enabled");
    AutoBinding autoBinding_18 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableTvShowFolderRename, jCheckBoxBeanProperty,
        tfTvShowFolder, tmmRoundTextAreaBeanProperty);
    autoBinding_18.bind();
    //
    AutoBinding autoBinding_19 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableSeasonFolderRename, jCheckBoxBeanProperty,
        tfSeasonFolderName, tmmRoundTextAreaBeanProperty);
    autoBinding_19.bind();
    //
    AutoBinding autoBinding_20 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableEpisodeFileRename, jCheckBoxBeanProperty,
        tfEpisodeFilename, tmmRoundTextAreaBeanProperty);
    autoBinding_20.bind();
    //
    AutoBinding autoBinding_21 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableTvShowFolderRename, jCheckBoxBeanProperty,
        btnResetTvShowPattern, tmmRoundTextAreaBeanProperty);
    autoBinding_21.bind();
    //
    AutoBinding autoBinding_22 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableSeasonFolderRename, jCheckBoxBeanProperty,
        btnResetSeasonPattern, tmmRoundTextAreaBeanProperty);
    autoBinding_22.bind();
    //
    AutoBinding autoBinding_23 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableEpisodeFileRename, jCheckBoxBeanProperty,
        btnResetFilePattern, tmmRoundTextAreaBeanProperty);
    autoBinding_23.bind();
    //
    Property tvShowRenamerProfileContainerBeanProperty_7 = BeanProperty.create("profile.specialSeason");
    AutoBinding autoBinding_24 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        tvShowRenamerProfileContainerBeanProperty_7, chckbxSpecialSeason, jCheckBoxBeanProperty);
    autoBinding_24.bind();
  }
}

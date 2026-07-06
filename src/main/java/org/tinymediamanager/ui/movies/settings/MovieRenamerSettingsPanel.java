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
import static org.tinymediamanager.ui.TmmFontHelper.L2;

import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
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
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieRenamer;
import org.tinymediamanager.core.movie.MovieRenamerProfile;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tvshow.TvShowRenamerProfile;
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
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.movies.dialogs.MovieJmteExplorerDialog;

import net.miginfocom.swing.MigLayout;

/**
 * The class MovieRenamerSettingsPanel.
 */
public class MovieRenamerSettingsPanel extends JPanel implements HierarchyListener {
  private final MovieSettings                settings         = MovieModuleManager.getInstance().getSettings();
  private final List<String>                 spaceReplacement = new ArrayList<>(Arrays.asList("_", ".", "-"));
  private final List<String>                 colonReplacement = new ArrayList<>(Arrays.asList(" ", "-", "_", "∶"));

  private final MovieRenamerProfileContainer renamerProfileContainer;
  private final ActionListener               profileActionListener;

  /**
   * UI components
   */
  private JTextArea                          tfMoviePath;
  private JTextArea                          tfMovieFilename;
  private LinkLabel                          lblExampleDatasource;
  private JLabel                             lblExampleFoldername;
  private JLabel                             lblExampleFilename;

  private JCheckBox                          chckbxEnableFolderRename;
  private JCheckBox                          chckbxEnableFileRename;
  private JCheckBox                          chckbxFoldernameSpaceReplacement;
  private JComboBox                          cbFoldernameSpaceReplacement;
  private JCheckBox                          chckbxFilenameSpaceReplacement;
  private JComboBox                          cbFilenameSpaceReplacement;
  private JComboBox                          cbMovieForPreview;
  private JCheckBox                          chckbxRemoveOtherNfos;
  private JCheckBox                          chckbxCleanupUnwanted;
  private JCheckBox                          chckbxMoviesetSingleMovie;

  private ReadOnlyTextArea                   taWarning;
  private JButton                            btnDeleteProfile;
  private JComboBox<String>                  cbProfile;

  private JComboBox                          cbColonReplacement;
  private JTextField                         tfFirstCharacter;
  private JCheckBox                          chckbxAllowMerge;
  private JHintCheckBox                      chckbxAsciiReplacement;
  private JHintCheckBox                      chckbxUnicodeReplacement;
  private JButton                            btnResetFolderPattern;
  private JButton                            btnResetFilenamePattern;

  public MovieRenamerSettingsPanel() {

    renamerProfileContainer = new MovieRenamerProfileContainer();
    renamerProfileContainer.setProfile(settings.getRenamerProfile(MovieRenamerProfile.DEFAULT_RENAMER_PROFILE));

    // UI initializations
    initComponents();
    initDataBindings();

    // data init
    profileActionListener = evt -> {
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
    cbProfile.setSelectedItem(MovieRenamerProfile.DEFAULT_RENAMER_PROFILE);

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

    tfFirstCharacter.getDocument().addDocumentListener(documentListener);
    tfMoviePath.getDocument().addDocumentListener(documentListener);
    tfMovieFilename.getDocument().addDocumentListener(documentListener);

    settings.addPropertyChangeListener(e -> {
      switch (e.getPropertyName()) {
        case "renamerPathname", "renamerFilename" -> createRenamerExample();
      }
    });

    // foldername space replacement
    String replacement = renamerProfileContainer.getProfile().getRenamerPathnameSpaceReplacement();
    cbFoldernameSpaceReplacement.setSelectedItem(replacement);

    // filename space replacement
    replacement = renamerProfileContainer.getProfile().getRenamerFilenameSpaceReplacement();
    cbFilenameSpaceReplacement.setSelectedItem(replacement);

    // colon replacement
    replacement = renamerProfileContainer.getProfile().getRenamerColonReplacement();
    cbColonReplacement.setSelectedItem(replacement);

    if (renamerProfileContainer.getProfile().isAsciiReplacement()) {
      chckbxUnicodeReplacement.setEnabled(false);
      cbColonReplacement.removeItem(colonReplacement.get(colonReplacement.size() - 1));
    }

    // event listener must be at the end
    ActionListener renamerActionListener = arg0 -> createRenamerExample();

    cbFoldernameSpaceReplacement.addActionListener(renamerActionListener);
    cbFilenameSpaceReplacement.addActionListener(renamerActionListener);
    cbColonReplacement.addActionListener(renamerActionListener);
    chckbxAsciiReplacement.addActionListener(arg0 -> {
      if (chckbxAsciiReplacement.isSelected()) {
        cbColonReplacement.removeItem(ListUtils.getLast(colonReplacement));
        chckbxUnicodeReplacement.setSelected(false);
        chckbxUnicodeReplacement.setEnabled(false);
      }
      else {
        cbColonReplacement.addItem(ListUtils.getLast(colonReplacement));
        chckbxUnicodeReplacement.setEnabled(true);
      }

      createRenamerExample();
    });
    chckbxUnicodeReplacement.addActionListener(renamerActionListener);

    lblExampleFilename.putClientProperty("clipPosition", SwingConstants.LEFT);

    // event listener must be at the end
    chckbxEnableFolderRename.addActionListener(renamerActionListener);
    chckbxEnableFileRename.addActionListener(renamerActionListener);
    cbMovieForPreview.addActionListener(renamerActionListener);
    chckbxMoviesetSingleMovie.addActionListener(renamerActionListener);
    chckbxAsciiReplacement.addActionListener(renamerActionListener);
    chckbxFilenameSpaceReplacement.addActionListener(renamerActionListener);
    chckbxFoldernameSpaceReplacement.addActionListener(renamerActionListener);
  }

  private void initComponents() {
    setLayout(new MigLayout("hidemode 1", "[grow]", "[][15lp!][][15lp!][][15lp!][][15lp!][]"));
    {
      JPanel panelProfile = new JPanel(new MigLayout("insets 0, hidemode 1", "[15lp][16lp!][200lp:350lp,grow]", "[][grow]"));

      JLabel lblProfileTitle = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.profile"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelProfile, lblProfileTitle, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#renamer-profile"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");
      {
        JLabel lblProfileT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.profile"));
        panelProfile.add(lblProfileT, "flowx,cell 1 0 2 1");

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

            MovieRenamerProfile newProfile = new MovieRenamerProfile(name);
            settings.addRenamerProfile(newProfile);
            cbProfile.addItem(name);
            cbProfile.setSelectedItem(name);

            createRenamerExample();
          }
        });

        panelProfile.add(btnAddNewProfile, "cell 1 0");

        FlatButton btnCopyProfile = new FlatButton(IconManager.COPY_GRAY);
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

            MovieRenamerProfile newProfile = new MovieRenamerProfile(name, renamerProfileContainer.getProfile());
            settings.addRenamerProfile(newProfile);
            cbProfile.addItem(name);
            cbProfile.setSelectedItem(name);

            createRenamerExample();
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
              cbProfile.setSelectedItem(MovieRenamerProfile.DEFAULT_RENAMER_PROFILE);
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
      JPanel panelPatterns = new JPanel(new MigLayout("insets 0, hidemode 1", "[20lp!][15lp][][400lp,grow][grow]", "[][][][][10lp!][]"));

      JLabel lblPatternsT = new TmmLabel(TmmResourceBundle.getString("Settings.movie.renamer.title"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelPatterns, lblPatternsT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#renamer-pattern"));
      add(collapsiblePanel, "cell 0 2,growx, wmin 0");
      {
        chckbxEnableFolderRename = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.folder"));
        chckbxEnableFolderRename.setToolTipText(TmmResourceBundle.getString("Settings.renamer.enablefolderrename"));
        panelPatterns.add(chckbxEnableFolderRename, "cell 1 0 2 1");

        tfMoviePath = new TmmRoundTextArea();
        panelPatterns.add(tfMoviePath, "cell 3 0, growx, wmin 0");

        btnResetFolderPattern = new FlatButton(IconManager.UNDO_GRAY);
        btnResetFolderPattern.setToolTipText(TmmResourceBundle.getString("Settings.renamer.reverttodefault"));
        btnResetFolderPattern.addActionListener(l -> tfMoviePath.setText(MovieSettings.DEFAULT_RENAMER_FOLDER_PATTERN));
        panelPatterns.add(btnResetFolderPattern, "cell 3 0, aligny top");

        JLabel lblDefault = new JLabel(TmmResourceBundle.getString("Settings.default"));
        panelPatterns.add(lblDefault, "cell 1 1 2 1,alignx right");
        TmmFontHelper.changeFont(lblDefault, L2);

        JTextArea tpDefaultFolderPattern = new ReadOnlyTextArea(MovieSettings.DEFAULT_RENAMER_FOLDER_PATTERN);
        panelPatterns.add(tpDefaultFolderPattern, "cell 3 1,growx,wmin 0");
        TmmFontHelper.changeFont(tpDefaultFolderPattern, L2);
      }
      {
        chckbxEnableFileRename = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.file"));
        chckbxEnableFileRename.setToolTipText(TmmResourceBundle.getString("Settings.renamer.enablefilerename"));
        panelPatterns.add(chckbxEnableFileRename, "cell 1 2 2 1, gapright 10");

        tfMovieFilename = new TmmRoundTextArea();
        panelPatterns.add(tfMovieFilename, "cell 3 2, growx, wmin 0");

        btnResetFilenamePattern = new FlatButton(IconManager.UNDO_GRAY);
        btnResetFilenamePattern.setToolTipText(TmmResourceBundle.getString("Settings.renamer.reverttodefault"));
        btnResetFilenamePattern.addActionListener(l -> tfMovieFilename.setText(MovieSettings.DEFAULT_RENAMER_FILE_PATTERN));
        panelPatterns.add(btnResetFilenamePattern, "cell 3 2, aligny top");

        JLabel lblDefault = new JLabel(TmmResourceBundle.getString("Settings.default"));
        panelPatterns.add(lblDefault, "cell 1 3 2 1,alignx right");
        TmmFontHelper.changeFont(lblDefault, L2);

        JTextArea tpDefaultFilePattern = new ReadOnlyTextArea(MovieSettings.DEFAULT_RENAMER_FILE_PATTERN);
        panelPatterns.add(tpDefaultFilePattern, "cell 3 3,growx,wmin 0");
        TmmFontHelper.changeFont(tpDefaultFilePattern, L2);
      }
      {
        JButton btnJmteExplorer = new JButton(TmmResourceBundle.getString("jmteexplorer.title"));
        btnJmteExplorer.addActionListener(e -> {
          MovieJmteExplorerDialog dialog = new MovieJmteExplorerDialog((JDialog) this.getTopLevelAncestor());
          dialog.setVisible(true);
        });
        panelPatterns.add(btnJmteExplorer, "cell 4 0");
      }
      {
        taWarning = new ReadOnlyTextArea();
        taWarning.setForeground(Color.red);
        panelPatterns.add(taWarning, "cell 3 5,growx,wmin 0");
      }
    }
    {
      JPanel panelAdvancedOptions = new JPanel();
      panelAdvancedOptions.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][]")); // 16lp ~ width of the

      JLabel lblAdvancedOptions = new TmmLabel(TmmResourceBundle.getString("Settings.advancedoptions"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelAdvancedOptions, lblAdvancedOptions, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#advanced-options-4"));
      add(collapsiblePanel, "cell 0 4,growx, wmin 0");
      {
        chckbxMoviesetSingleMovie = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.moviesetsinglemovie"));
        panelAdvancedOptions.add(chckbxMoviesetSingleMovie, "cell 1 0 2 1");
      }
      {
        chckbxRemoveOtherNfos = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.removenfo"));
        panelAdvancedOptions.add(chckbxRemoveOtherNfos, "cell 1 1 2 1");
      }
      {
        chckbxCleanupUnwanted = new JCheckBox(TmmResourceBundle.getString("Settings.cleanupfiles"));
        panelAdvancedOptions.add(chckbxCleanupUnwanted, "cell 1 2 2 1");
      }
      {
        chckbxAllowMerge = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.movie.allowmerge"));
        panelAdvancedOptions.add(chckbxAllowMerge, "cell 1 3 2 1");
      }
    }
    {
      JPanel panelReplacements = new JPanel();
      panelReplacements.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][][][]")); // 16lp ~ width of the

      JLabel lblReplacementsT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.replacements"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelReplacements, lblReplacementsT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#advanced-options-4"));
      add(collapsiblePanel, "cell 0 6,growx, wmin 0");
      {
        chckbxFoldernameSpaceReplacement = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.folderspacereplacement"));
        chckbxFoldernameSpaceReplacement.setToolTipText(TmmResourceBundle.getString("Settings.renamer.folderspacereplacement.hint"));
        panelReplacements.add(chckbxFoldernameSpaceReplacement, "cell 1 0 2 1");

        cbFoldernameSpaceReplacement = new JComboBox<>(spaceReplacement.toArray());
        panelReplacements.add(cbFoldernameSpaceReplacement, "cell 1 0 2 1");
      }
      {
        chckbxFilenameSpaceReplacement = new JCheckBox(TmmResourceBundle.getString("Settings.renamer.spacereplacement"));
        chckbxFilenameSpaceReplacement.setToolTipText(TmmResourceBundle.getString("Settings.renamer.spacereplacement.hint"));
        panelReplacements.add(chckbxFilenameSpaceReplacement, "cell 1 1 2 1");

        cbFilenameSpaceReplacement = new JComboBox<>(spaceReplacement.toArray());
        panelReplacements.add(cbFilenameSpaceReplacement, "cell 1 1 2 1");
      }
      {
        JLabel lblFirstCharacterT = new JLabel(TmmResourceBundle.getString("Settings.renamer.firstnumbercharacterreplacement"));
        panelReplacements.add(lblFirstCharacterT, "flowx,cell 1 2 2 1");

        tfFirstCharacter = new JTextField();
        panelReplacements.add(tfFirstCharacter, "cell 1 2 2 1");
        tfFirstCharacter.setColumns(2);
      }
      {
        JLabel lblColonReplacement = new JLabel(TmmResourceBundle.getString("Settings.renamer.colonreplacement"));
        panelReplacements.add(lblColonReplacement, "flowx,cell 1 3 2 1");
        lblColonReplacement.setToolTipText(TmmResourceBundle.getString("Settings.renamer.colonreplacement.hint"));

        cbColonReplacement = new JComboBox<>(colonReplacement.toArray());
        panelReplacements.add(cbColonReplacement, "cell 1 3 2 1");
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
        panelReplacements.add(chckbxAsciiReplacement, "cell 1 4 2 1");
      }
      {
        chckbxUnicodeReplacement = new JHintCheckBox(TmmResourceBundle.getString("Settings.renamer.unicodereplacement"));

        String examples = "<html>" + TmmResourceBundle.getString("Settings.renamer.examples") + "<br>";
        examples += ":  →  ∶<br>";
        examples += "/  →  ⁄<br>";
        examples += "&gt;  →  ›<br>";
        examples += "…</html>";

        chckbxUnicodeReplacement.setToolTipText(examples);
        panelReplacements.add(chckbxUnicodeReplacement, "cell 1 5 2 1");
      }
    }
    {
      JPanel panelExample = new JPanel();
      panelExample.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][][300lp,grow]", "[]10lp![][][]"));

      JLabel lblExampleHeader = new TmmLabel(TmmResourceBundle.getString("Settings.example"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelExample, lblExampleHeader, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#example"));
      add(collapsiblePanel, "cell 0 8, growx, wmin 0");
      {
        JLabel lblExampleT = new TmmLabel(TmmResourceBundle.getString("tmm.movie"));
        panelExample.add(lblExampleT, "cell 1 0");

        cbMovieForPreview = new JComboBox();
        panelExample.add(cbMovieForPreview, "cell 2 0, wmin 0");

        JLabel lblDatasourceT = new TmmLabel(TmmResourceBundle.getString("metatag.datasource"));
        panelExample.add(lblDatasourceT, "cell 1 1");

        lblExampleDatasource = new LinkLabel("");
        panelExample.add(lblExampleDatasource, "cell 2 1, wmin 0");

        JLabel lblFoldernameT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.folder"));
        panelExample.add(lblFoldernameT, "cell 1 2");

        lblExampleFoldername = new JLabel("");
        panelExample.add(lblExampleFoldername, "cell 2 2, wmin 0");

        JLabel lblFilenameT = new TmmLabel(TmmResourceBundle.getString("Settings.renamer.file"));
        panelExample.add(lblFilenameT, "cell 1 3");

        lblExampleFilename = new JLabel("");
        panelExample.add(lblExampleFilename, "cell 2 3, wmin 0");
      }
    }
  }

  private void buildAndInstallMovieArray() {
    cbMovieForPreview.removeAllItems();
    List<Movie> allMovies = new ArrayList<>(MovieModuleManager.getInstance().getMovieList().getMovies());
    Movie sel = MovieUIModule.getInstance().getSelectionModel().getSelectedMovie();
    allMovies.sort(new MovieComparator());
    for (Movie movie : allMovies) {
      MoviePreviewContainer container = new MoviePreviewContainer();
      container.movie = movie;
      cbMovieForPreview.addItem(container);
      if (sel != null && movie.equals(sel)) {
        cbMovieForPreview.setSelectedItem(container);
      }
    }
  }

  private void createRenamerExample() {
    Movie movie = null;

    String warning = "";

    if (chckbxEnableFolderRename.isSelected() && !MovieRenamer.isFolderPatternUnique(tfMoviePath.getText())) {
      warning = TmmResourceBundle.getString("Settings.renamer.folder.warning");
    }
    if (!warning.isEmpty()) {
      taWarning.setVisible(true);
      taWarning.setText(warning);
    }
    else {
      taWarning.setVisible(false);
    }

    if (cbMovieForPreview.getSelectedItem() instanceof MoviePreviewContainer container) {
      movie = container.movie;
    }

    if (movie != null) {
      String path = "";
      String filename = "";
      if (renamerProfileContainer.getProfile().isRenamerPathnameEnabled() && StringUtils.isNotBlank(tfMoviePath.getText())) {
        path = MovieRenamer.createDestinationForFoldername(renamerProfileContainer.getProfile(), movie);
        try {
          path = Paths.get(movie.getDataSource(), path).toString();
        }
        catch (Exception e) {
          // catch invalid paths (e.g. illegal characters in the pathname)
        }
      }
      else {
        // the old folder name
        path = movie.getPathNIO().toString();
      }

      if (renamerProfileContainer.getProfile().isRenamerFilenameEnabled() && StringUtils.isNotBlank(tfMovieFilename.getText())) {
        List<MediaFile> mediaFiles = movie.getMediaFiles(MediaFileType.VIDEO);
        if (!mediaFiles.isEmpty()) {
          String extension = FilenameUtils.getExtension(mediaFiles.get(0).getFilename());
          filename = MovieRenamer.createDestinationForFilename(renamerProfileContainer.getProfile(), movie);
          // patterns are always w/o extension, but when having the originalFilename, it will be there.
          if (!filename.endsWith(extension)) {
            filename += "." + extension;
          }
        }
      }
      else {
        filename = movie.getMediaFiles(MediaFileType.VIDEO).get(0).getFilename();
      }

      lblExampleDatasource.setText(movie.getDataSource());
      lblExampleFoldername.setText(path.replace(movie.getDataSource() + File.separator, ""));
      lblExampleFilename.setText(filename);
    }
    else {
      lblExampleDatasource.setText(TmmResourceBundle.getString("Settings.movie.renamer.nomovie"));
      lblExampleFoldername.setText(TmmResourceBundle.getString("Settings.movie.renamer.nomovie"));
      lblExampleFilename.setText(TmmResourceBundle.getString("Settings.movie.renamer.nomovie"));
    }
  }

  @Override
  public void hierarchyChanged(HierarchyEvent arg0) {
    if (isShowing()) {
      buildAndInstallMovieArray();
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

  /*****************************************************************************
   * helper classes
   *****************************************************************************/
  private static class MoviePreviewContainer {
    Movie movie;

    @Override
    public String toString() {
      return movie.getTitle();
    }
  }

  private static class MovieComparator implements Comparator<Movie> {
    @Override
    public int compare(Movie arg0, Movie arg1) {
      return arg0.getTitle().compareTo(arg1.getTitle());
    }
  }

  /*
   * Helper classes
   */
  public static class MovieRenamerProfileContainer extends AbstractModelObject {
    private MovieRenamerProfile profile;

    public MovieRenamerProfile getProfile() {
      return profile;
    }

    public void setProfile(MovieRenamerProfile newValue) {
      MovieRenamerProfile oldValue = this.profile;
      this.profile = newValue;
      firePropertyChange("profile", oldValue, newValue);
    }
  }

  protected void initDataBindings() {
    Property movieRenamerProfileContainerBeanProperty = BeanProperty.create("profile.renamerNfoCleanup");
    Property jCheckBoxBeanProperty = BeanProperty.create("selected");
    AutoBinding autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty, chckbxRemoveOtherNfos, jCheckBoxBeanProperty);
    autoBinding_1.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_1 = BeanProperty.create("profile.renamerPathname");
    Property tmmRoundTextAreaBeanProperty = BeanProperty.create("text");
    AutoBinding autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_1, tfMoviePath, tmmRoundTextAreaBeanProperty);
    autoBinding_7.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_2 = BeanProperty.create("profile.renamerFilename");
    Property tmmRoundTextAreaBeanProperty_1 = BeanProperty.create("text");
    AutoBinding autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_2, tfMovieFilename, tmmRoundTextAreaBeanProperty_1);
    autoBinding_10.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_5 = BeanProperty.create("profile.renamerCreateMoviesetForSingleMovie");
    AutoBinding autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_5, chckbxMoviesetSingleMovie, jCheckBoxBeanProperty);
    autoBinding_4.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_6 = BeanProperty.create("profile.asciiReplacement");
    AutoBinding autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_6, chckbxAsciiReplacement, jCheckBoxBeanProperty);
    autoBinding_5.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_7 = BeanProperty.create("profile.renamerFirstCharacterNumberReplacement");
    Property jTextFieldBeanProperty = BeanProperty.create("text");
    AutoBinding autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_7, tfFirstCharacter, jTextFieldBeanProperty);
    autoBinding_3.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_8 = BeanProperty.create("profile.allowMultipleMoviesInSameDir");
    AutoBinding autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_8, chckbxAllowMerge, jCheckBoxBeanProperty);
    autoBinding_6.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_9 = BeanProperty.create("profile.renamerCleanupUnwanted");
    AutoBinding autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_9, chckbxCleanupUnwanted, jCheckBoxBeanProperty);
    autoBinding_8.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_10 = BeanProperty.create("profile.unicodeReplacement");
    AutoBinding autoBinding_9 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_10, chckbxUnicodeReplacement, jCheckBoxBeanProperty);
    autoBinding_9.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_3 = BeanProperty.create("profile.renamerPathnameSpaceSubstitution");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_3, chckbxFoldernameSpaceReplacement, jCheckBoxBeanProperty);
    autoBinding.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_4 = BeanProperty.create("profile.renamerFilenameSpaceSubstitution");
    AutoBinding autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_4, chckbxFilenameSpaceReplacement, jCheckBoxBeanProperty);
    autoBinding_2.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_11 = BeanProperty.create("profile.renamerPathnameSpaceReplacement");
    Property jComboBoxBeanProperty = BeanProperty.create("selectedItem");
    AutoBinding autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_11, cbFoldernameSpaceReplacement, jComboBoxBeanProperty);
    autoBinding_11.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_12 = BeanProperty.create("profile.renamerFilenameSpaceReplacement");
    AutoBinding autoBinding_12 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_12, cbFilenameSpaceReplacement, jComboBoxBeanProperty);
    autoBinding_12.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_13 = BeanProperty.create("profile.renamerColonReplacement");
    AutoBinding autoBinding_13 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_13, cbColonReplacement, jComboBoxBeanProperty);
    autoBinding_13.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_14 = BeanProperty.create("profile.renamerPathnameEnabled");
    AutoBinding autoBinding_14 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_14, chckbxEnableFolderRename, jCheckBoxBeanProperty);
    autoBinding_14.bind();
    //
    Property movieRenamerProfileContainerBeanProperty_15 = BeanProperty.create("profile.renamerFilenameEnabled");
    AutoBinding autoBinding_15 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, renamerProfileContainer,
        movieRenamerProfileContainerBeanProperty_15, chckbxEnableFileRename, jCheckBoxBeanProperty);
    autoBinding_15.bind();
    //
    Property tmmRoundTextAreaBeanProperty_2 = BeanProperty.create("enabled");
    AutoBinding autoBinding_16 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableFolderRename, jCheckBoxBeanProperty, tfMoviePath,
        tmmRoundTextAreaBeanProperty_2);
    autoBinding_16.bind();
    //
    AutoBinding autoBinding_17 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableFileRename, jCheckBoxBeanProperty, tfMovieFilename,
        tmmRoundTextAreaBeanProperty_2);
    autoBinding_17.bind();
    //
    AutoBinding autoBinding_18 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableFolderRename, jCheckBoxBeanProperty,
        btnResetFolderPattern, tmmRoundTextAreaBeanProperty_2);
    autoBinding_18.bind();
    //
    AutoBinding autoBinding_19 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxEnableFileRename, jCheckBoxBeanProperty,
        btnResetFilenamePattern, tmmRoundTextAreaBeanProperty_2);
    autoBinding_19.bind();
  }
}

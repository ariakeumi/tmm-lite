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

import java.awt.GridLayout;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.core.movie.filenaming.MovieBannerNaming;
import org.tinymediamanager.core.movie.filenaming.MovieClearartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieClearlogoNaming;
import org.tinymediamanager.core.movie.filenaming.MovieDiscartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieFanartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieKeyartNaming;
import org.tinymediamanager.core.movie.filenaming.MoviePosterNaming;
import org.tinymediamanager.core.movie.filenaming.MovieSquareartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieThumbNaming;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextArea;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link MovieImageSettingsPanel} is used to display image file name settings.
 * 
 * @author Manuel Laggner
 */
class MovieImageTypeSettingsPanel extends JPanel {
  private final MovieSettings settings = MovieModuleManager.getInstance().getSettings();

  private final ItemListener  checkBoxListener;

  private JCheckBox           chckbxMoviePosterFilename2;
  private JCheckBox           chckbxMoviePosterFilename4;
  private JCheckBox           chckbxMoviePosterFilename6;
  private JCheckBox           chckbxMoviePosterFilename7;
  private JCheckBox           chckbxMoviePosterFilename8;
  private JCheckBox           chckbxMoviePosterFilename9;
  private JCheckBox           chckbxMovieFanartFilename1;
  private JCheckBox           chckbxMovieFanartFilename2;
  private JCheckBox           chckbxMovieFanartFilename3;
  private JCheckBox           chckbxMovieFanartFilename4;
  private JCheckBox           chckbxMovieFanartFilename5;
  private JCheckBox           chckbxMovieFanartFilename6;
  private JCheckBox           chckbxMovieFanartFilename7;
  private JCheckBox           chckbxMovieFanartFilename8;
  private JCheckBox           chckbxMovieFanartFilename9;
  private JCheckBox           chckbxBanner1;
  private JCheckBox           chckbxBanner2;
  private JCheckBox           chckbxClearart1;
  private JCheckBox           chckbxClearart2;
  private JCheckBox           chckbxThumb1;
  private JCheckBox           chckbxThumb2;
  private JCheckBox           chckbxThumb3;
  private JCheckBox           chckbxThumb4;
  private JCheckBox           chckbxLogo1;
  private JCheckBox           chckbxLogo2;
  private JCheckBox           chckbxClearlogo1;
  private JCheckBox           chckbxClearlogo2;
  private JCheckBox           chckbxDiscart1;
  private JCheckBox           chckbxDiscart2;
  private JCheckBox           chckbxDiscart4;
  private JCheckBox           chckbxDiscart3;
  private JCheckBox           chckbxKeyart1;
  private JCheckBox           chckbxKeyart2;
  private JCheckBox           chckbxSquareart1;
  private JCheckBox           chckbxSquareart2;
  private JCheckBox           chckbxSquareart3;
  private JCheckBox           chckbxSquareart4;
  private JCheckBox           chckbxSquareart5;

  /**
   * Instantiates a new movie image settings panel.
   */
  MovieImageTypeSettingsPanel() {
    checkBoxListener = e -> checkChanges();

    // UI init
    initComponents();

    // implement checkBoxListener for preset events
    settings.addPropertyChangeListener(evt -> {
      if ("preset".equals(evt.getPropertyName())) {
        buildCheckBoxes();
      }
    });

    buildCheckBoxes();
  }

  private void buildCheckBoxes() {
    // initialize
    chckbxMovieFanartFilename1.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename2.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename3.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename4.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename5.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename6.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename7.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename8.removeItemListener(checkBoxListener);
    chckbxMovieFanartFilename9.removeItemListener(checkBoxListener);
    clearSelection(chckbxMovieFanartFilename1, chckbxMovieFanartFilename2, chckbxMovieFanartFilename3, chckbxMovieFanartFilename4,
        chckbxMovieFanartFilename5, chckbxMovieFanartFilename6, chckbxMovieFanartFilename7, chckbxMovieFanartFilename8, chckbxMovieFanartFilename9);

    chckbxMoviePosterFilename2.removeItemListener(checkBoxListener);
    chckbxMoviePosterFilename4.removeItemListener(checkBoxListener);
    chckbxMoviePosterFilename7.removeItemListener(checkBoxListener);
    chckbxMoviePosterFilename8.removeItemListener(checkBoxListener);
    chckbxMoviePosterFilename6.removeItemListener(checkBoxListener);
    chckbxMoviePosterFilename9.removeItemListener(checkBoxListener);
    clearSelection(chckbxMoviePosterFilename2, chckbxMoviePosterFilename4, chckbxMoviePosterFilename6, chckbxMoviePosterFilename7,
        chckbxMoviePosterFilename8, chckbxMoviePosterFilename9);

    chckbxBanner1.removeItemListener(checkBoxListener);
    chckbxBanner2.removeItemListener(checkBoxListener);
    clearSelection(chckbxBanner1, chckbxBanner2);

    chckbxClearart1.removeItemListener(checkBoxListener);
    chckbxClearart2.removeItemListener(checkBoxListener);
    clearSelection(chckbxClearart1, chckbxClearart2);

    chckbxClearlogo1.removeItemListener(checkBoxListener);
    chckbxClearlogo2.removeItemListener(checkBoxListener);
    clearSelection(chckbxClearlogo1, chckbxClearlogo2);

    chckbxLogo1.removeItemListener(checkBoxListener);
    chckbxLogo2.removeItemListener(checkBoxListener);
    clearSelection(chckbxLogo1, chckbxLogo2);

    chckbxThumb1.removeItemListener(checkBoxListener);
    chckbxThumb2.removeItemListener(checkBoxListener);
    chckbxThumb3.removeItemListener(checkBoxListener);
    chckbxThumb4.removeItemListener(checkBoxListener);
    clearSelection(chckbxThumb1, chckbxThumb2, chckbxThumb3, chckbxThumb4);

    chckbxDiscart1.removeItemListener(checkBoxListener);
    chckbxDiscart2.removeItemListener(checkBoxListener);
    chckbxDiscart3.removeItemListener(checkBoxListener);
    chckbxDiscart4.removeItemListener(checkBoxListener);
    clearSelection(chckbxDiscart1, chckbxDiscart2, chckbxDiscart3, chckbxDiscart4);

    chckbxKeyart1.removeItemListener(checkBoxListener);
    chckbxKeyart2.removeItemListener(checkBoxListener);
    clearSelection(chckbxKeyart1, chckbxKeyart2);

    chckbxSquareart1.removeItemListener(checkBoxListener);
    chckbxSquareart2.removeItemListener(checkBoxListener);
    chckbxSquareart3.removeItemListener(checkBoxListener);
    chckbxSquareart4.removeItemListener(checkBoxListener);
    chckbxSquareart5.removeItemListener(checkBoxListener);
    clearSelection(chckbxSquareart1, chckbxSquareart2, chckbxSquareart3, chckbxSquareart4, chckbxSquareart5);

    // poster filenames
    for (MoviePosterNaming poster : settings.getPosterFilenames()) {
      switch (poster) {
        case FILENAME:
          chckbxMoviePosterFilename7.setSelected(true);
          break;

        case FILENAME_POSTER:
          chckbxMoviePosterFilename8.setSelected(true);
          break;

        case FOLDER:
          chckbxMoviePosterFilename6.setSelected(true);
          break;

        case MOVIE:
          chckbxMoviePosterFilename2.setSelected(true);
          break;

        case POSTER:
          chckbxMoviePosterFilename4.setSelected(true);
          break;

        case COVER:
          chckbxMoviePosterFilename9.setSelected(true);
          break;
      }
    }

    // fanart filenames
    for (MovieFanartNaming fanart : settings.getFanartFilenames()) {
      switch (fanart) {
        case FANART:
          chckbxMovieFanartFilename2.setSelected(true);
          break;

        case FILENAME_FANART:
          chckbxMovieFanartFilename1.setSelected(true);
          break;

        case FILENAME_FANART2:
          chckbxMovieFanartFilename3.setSelected(true);
          break;

        case BACKDROP:
          chckbxMovieFanartFilename4.setSelected(true);
          break;

        case FILENAME_BACKDROP:
          chckbxMovieFanartFilename5.setSelected(true);
          break;

        case FILENAME_BACKDROP2:
          chckbxMovieFanartFilename6.setSelected(true);
          break;

        case BACKGROUND:
          chckbxMovieFanartFilename7.setSelected(true);
          break;

        case FILENAME_BACKGROUND:
          chckbxMovieFanartFilename8.setSelected(true);
          break;

        case FILENAME_BACKGROUND2:
          chckbxMovieFanartFilename9.setSelected(true);
          break;
      }
    }

    // banner filenames
    for (MovieBannerNaming banner : settings.getBannerFilenames()) {
      switch (banner) {
        case BANNER:
          chckbxBanner2.setSelected(true);
          break;

        case FILENAME_BANNER:
          chckbxBanner1.setSelected(true);
          break;
      }
    }

    // clearart filenames
    for (MovieClearartNaming clearart : settings.getClearartFilenames()) {
      switch (clearart) {
        case CLEARART:
          chckbxClearart2.setSelected(true);
          break;

        case FILENAME_CLEARART:
          chckbxClearart1.setSelected(true);
          break;
      }
    }

    // thumb filenames
    for (MovieThumbNaming thumb : settings.getThumbFilenames()) {
      switch (thumb) {
        case THUMB:
          chckbxThumb2.setSelected(true);
          break;

        case FILENAME_THUMB:
          chckbxThumb1.setSelected(true);
          break;

        case LANDSCAPE:
          chckbxThumb4.setSelected(true);
          break;

        case FILENAME_LANDSCAPE:
          chckbxThumb3.setSelected(true);
          break;
      }
    }

    // clearlogo filenames
    for (MovieClearlogoNaming clearlogo : settings.getClearlogoFilenames()) {
      switch (clearlogo) {
        case CLEARLOGO:
          chckbxClearlogo2.setSelected(true);
          break;

        case FILENAME_CLEARLOGO:
          chckbxClearlogo1.setSelected(true);
          break;

        case LOGO:
          chckbxLogo2.setSelected(true);
          break;

        case FILENAME_LOGO:
          chckbxLogo1.setSelected(true);
          break;
      }
    }

    // discart filenames
    for (MovieDiscartNaming discart : settings.getDiscartFilenames()) {
      switch (discart) {
        case DISC:
          chckbxDiscart2.setSelected(true);
          break;

        case FILENAME_DISC:
          chckbxDiscart1.setSelected(true);
          break;

        case DISCART:
          chckbxDiscart4.setSelected(true);
          break;

        case FILENAME_DISCART:
          chckbxDiscart3.setSelected(true);
          break;
      }
    }

    // keyart filenames
    for (MovieKeyartNaming keyart : settings.getKeyartFilenames()) {
      switch (keyart) {
        case KEYART:
          chckbxKeyart1.setSelected(true);
          break;

        case FILENAME_KEYART:
          chckbxKeyart2.setSelected(true);
          break;
      }
    }

    // squareart filenames
    for (MovieSquareartNaming squareart : settings.getSquareartFilenames()) {
      switch (squareart) {
        case SQUAREART:
          chckbxSquareart2.setSelected(true);
          break;

        case FILENAME_SQUAREART:
          chckbxSquareart1.setSelected(true);
          break;

        case SQUARE:
          chckbxSquareart3.setSelected(true);
          break;

        case FILENAME_SQUARE:
          chckbxSquareart5.setSelected(true);
          break;

        case BACKGROUND_SQUARE:
          chckbxSquareart4.setSelected(true);
          break;
      }
    }

    // listen to changes of the checkboxes
    chckbxMovieFanartFilename1.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename2.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename3.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename4.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename5.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename6.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename7.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename8.addItemListener(checkBoxListener);
    chckbxMovieFanartFilename9.addItemListener(checkBoxListener);

    chckbxMoviePosterFilename2.addItemListener(checkBoxListener);
    chckbxMoviePosterFilename4.addItemListener(checkBoxListener);
    chckbxMoviePosterFilename7.addItemListener(checkBoxListener);
    chckbxMoviePosterFilename8.addItemListener(checkBoxListener);
    chckbxMoviePosterFilename6.addItemListener(checkBoxListener);
    chckbxMoviePosterFilename9.addItemListener(checkBoxListener);

    chckbxBanner1.addItemListener(checkBoxListener);
    chckbxBanner2.addItemListener(checkBoxListener);

    chckbxClearart1.addItemListener(checkBoxListener);
    chckbxClearart2.addItemListener(checkBoxListener);

    chckbxClearlogo1.addItemListener(checkBoxListener);
    chckbxClearlogo2.addItemListener(checkBoxListener);

    chckbxLogo1.addItemListener(checkBoxListener);
    chckbxLogo2.addItemListener(checkBoxListener);

    chckbxThumb1.addItemListener(checkBoxListener);
    chckbxThumb2.addItemListener(checkBoxListener);
    chckbxThumb3.addItemListener(checkBoxListener);
    chckbxThumb4.addItemListener(checkBoxListener);

    chckbxDiscart1.addItemListener(checkBoxListener);
    chckbxDiscart2.addItemListener(checkBoxListener);
    chckbxDiscart3.addItemListener(checkBoxListener);
    chckbxDiscart4.addItemListener(checkBoxListener);

    chckbxKeyart1.addItemListener(checkBoxListener);
    chckbxKeyart2.addItemListener(checkBoxListener);

    chckbxSquareart1.addItemListener(checkBoxListener);
    chckbxSquareart2.addItemListener(checkBoxListener);
    chckbxSquareart3.addItemListener(checkBoxListener);
    chckbxSquareart4.addItemListener(checkBoxListener);
    chckbxSquareart5.addItemListener(checkBoxListener);
  }

  private void clearSelection(JCheckBox... checkBoxes) {
    for (JCheckBox checkBox : checkBoxes) {
      checkBox.setSelected(false);
    }
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[grow]", "[]"));
    {
      JPanel panelFileNaming = new JPanel(new MigLayout("insets 0", "[20lp!][][50lp,grow]", "[][15lp!]"));

      JLabel lblFiletypes = new TmmLabel(TmmResourceBundle.getString("Settings.artwork.naming"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelFileNaming, lblFiletypes, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#artwork-filenames"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");

      JPanel panelRow = new JPanel();
      panelFileNaming.add(panelRow, "cell 1 0,grow");
      panelRow.setLayout(new GridLayout(1, 2, 0, 0));

      JPanel panelColumn1 = new JPanel();
      panelRow.add(panelColumn1);
      panelColumn1.setLayout(new MigLayout("", "[300lp,grow][25lp!]", "[][][][][][]"));

      JPanel panelColumn2 = new JPanel();
      panelRow.add(panelColumn2);
      panelColumn2.setLayout(new MigLayout("", "[grow]", "[][][][]"));

      {
        JPanel panelPoster = new JPanel();
        panelPoster.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.poster")));
        panelPoster.setLayout(new MigLayout("", "[]", "[][][][][][]"));

        chckbxMoviePosterFilename4 = new JCheckBox("poster.*");
        panelPoster.add(chckbxMoviePosterFilename4, "cell 0 0");

        chckbxMoviePosterFilename8 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-poster.*");
        panelPoster.add(chckbxMoviePosterFilename8, "cell 0 1");

        chckbxMoviePosterFilename2 = new JCheckBox("movie.*");
        panelPoster.add(chckbxMoviePosterFilename2, "cell 0 2");

        chckbxMoviePosterFilename6 = new JCheckBox("folder.*");
        panelPoster.add(chckbxMoviePosterFilename6, "cell 0 3");

        chckbxMoviePosterFilename7 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + ".*");
        panelPoster.add(chckbxMoviePosterFilename7, "cell 0 4");

        chckbxMoviePosterFilename9 = new JCheckBox("cover.*");
        panelPoster.add(chckbxMoviePosterFilename9, "cell 0 5");

        panelColumn1.add(panelPoster, "cell 0 0,growx");
      }
      {
        JPanel panelBanner = new JPanel();
        panelBanner.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.banner")));
        panelBanner.setLayout(new MigLayout("", "[grow]", "[][]"));

        chckbxBanner2 = new JCheckBox("banner.*");
        panelBanner.add(chckbxBanner2, "cell 0 0");

        chckbxBanner1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-banner.*");
        panelBanner.add(chckbxBanner1, "cell 0 1");

        panelColumn1.add(panelBanner, "cell 0 1,growx");
      }
      {
        JPanel panelClearart = new JPanel();
        panelColumn1.add(panelClearart, "cell 0 2,growx");
        panelClearart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.clearart")));
        panelClearart.setLayout(new MigLayout("", "[grow]", "[][]"));

        chckbxClearart2 = new JCheckBox("clearart.*");
        panelClearart.add(chckbxClearart2, "cell 0 0");

        chckbxClearart1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-clearart.*");
        panelClearart.add(chckbxClearart1, "cell 0 1");
      }
      {
        JPanel panelThumb = new JPanel();
        panelThumb.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.thumb")));
        panelThumb.setLayout(new MigLayout("", "[grow]", "[][][][]"));

        chckbxThumb2 = new JCheckBox("thumb.*");
        panelThumb.add(chckbxThumb2, "cell 0 0");

        chckbxThumb1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-thumb.*");
        panelThumb.add(chckbxThumb1, "cell 0 1");

        chckbxThumb4 = new JCheckBox("landscape.*");
        panelThumb.add(chckbxThumb4, "cell 0 2");

        chckbxThumb3 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-landscape.*");
        panelThumb.add(chckbxThumb3, "cell 0 3");

        panelColumn1.add(panelThumb, "cell 0 3,growx");
      }
      {
        JPanel panelSquareart = new JPanel();
        panelColumn1.add(panelSquareart, "cell 0 5,growx");
        panelSquareart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.squareart")));
        panelSquareart.setLayout(new MigLayout("", "[grow]", "[][][][][]"));

        chckbxSquareart2 = new JCheckBox("squareart.*");
        panelSquareart.add(chckbxSquareart2, "cell 0 0");

        chckbxSquareart1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-squareart.*");
        panelSquareart.add(chckbxSquareart1, "cell 0 1");

        chckbxSquareart3 = new JCheckBox("square.*");
        panelSquareart.add(chckbxSquareart3, "cell 0 2");

        chckbxSquareart5 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-square.*");
        panelSquareart.add(chckbxSquareart5, "cell 0 3");

        chckbxSquareart4 = new JCheckBox("backgroundSquare.*");
        panelSquareart.add(chckbxSquareart4, "cell 0 4");
      }

      {
        JPanel panelFanart = new JPanel();
        panelFanart.setBorder(new TitledBorder(null, TmmResourceBundle.getString("mediafiletype.fanart")));
        panelFanart.setLayout(new MigLayout("", "[]", "[][][][][][][]"));

        chckbxMovieFanartFilename2 = new JCheckBox("fanart.*");
        panelFanart.add(chckbxMovieFanartFilename2, "cell 0 0");

        chckbxMovieFanartFilename1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-fanart.*");
        panelFanart.add(chckbxMovieFanartFilename1, "cell 0 1");

        chckbxMovieFanartFilename3 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + ".fanart.*");
        panelFanart.add(chckbxMovieFanartFilename3, "cell 0 2");

        chckbxMovieFanartFilename4 = new JCheckBox("backdrop.*");
        panelFanart.add(chckbxMovieFanartFilename4, "cell 0 3");

        chckbxMovieFanartFilename5 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-backdrop.*");
        panelFanart.add(chckbxMovieFanartFilename5, "cell 0 4");

        chckbxMovieFanartFilename6 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + ".backdrop.*");
        panelFanart.add(chckbxMovieFanartFilename6, "cell 0 5");

        chckbxMovieFanartFilename7 = new JCheckBox("background.*");
        panelFanart.add(chckbxMovieFanartFilename7, "cell 0 6");

        chckbxMovieFanartFilename8 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-background.*");
        panelFanart.add(chckbxMovieFanartFilename8, "cell 0 7");

        chckbxMovieFanartFilename9 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + ".background.*");
        panelFanart.add(chckbxMovieFanartFilename9, "cell 0 8");

        panelColumn2.add(panelFanart, "cell 0 0,growx");
      }
      {
        JPanel panelDiscart = new JPanel();
        panelDiscart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.disc")));
        panelDiscart.setLayout(new MigLayout("", "[grow]", "[][][][]"));

        chckbxDiscart2 = new JCheckBox("disc.*");
        panelDiscart.add(chckbxDiscart2, "cell 0 0");

        chckbxDiscart1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-disc.*");
        panelDiscart.add(chckbxDiscart1, "cell 0 1");

        chckbxDiscart4 = new JCheckBox("discart.*");
        panelDiscart.add(chckbxDiscart4, "cell 0 2");

        chckbxDiscart3 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-discart.*");
        panelDiscart.add(chckbxDiscart3, "cell 0 3");

        panelColumn2.add(panelDiscart, "cell 0 1,growx");
      }
      {
        JPanel panelClearlogo = new JPanel();
        panelClearlogo.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.clearlogo")));
        panelClearlogo.setLayout(new MigLayout("", "[grow]", "[][][][]"));

        chckbxClearlogo2 = new JCheckBox("clearlogo.*");
        panelClearlogo.add(chckbxClearlogo2, "cell 0 0");

        chckbxClearlogo1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-clearlogo.*");
        panelClearlogo.add(chckbxClearlogo1, "cell 0 1");

        chckbxLogo2 = new JCheckBox("logo.*");
        panelClearlogo.add(chckbxLogo2, "cell 0 2");

        chckbxLogo1 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-logo.*");
        panelClearlogo.add(chckbxLogo1, "cell 0 3");

        panelColumn2.add(panelClearlogo, "cell 0 2,growx");
      }
      {
        JPanel panelKeyart = new JPanel();
        panelColumn2.add(panelKeyart, "cell 0 3,growx");
        panelKeyart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.keyart")));
        panelKeyart.setLayout(new MigLayout("", "[grow]", "[]"));

        chckbxKeyart1 = new JCheckBox("keyart.*");
        panelKeyart.add(chckbxKeyart1, "cell 0 0");

        chckbxKeyart2 = new JCheckBox(TmmResourceBundle.getString("Settings.moviefilename") + "-keyart.*");
        panelKeyart.add(chckbxKeyart2, "cell 0 1");
      }

      JTextArea tpFileNamingHint = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.naming.info"));
      panelFileNaming.add(tpFileNamingHint, "cell 1 1 2 1,growx,wmin 0");
      TmmFontHelper.changeFont(tpFileNamingHint, 0.833);
    }
  }

  /**
   * Check changes.
   */
  private void checkChanges() {
    // set poster filenames
    settings.clearPosterFilenames();

    if (chckbxMoviePosterFilename2.isSelected()) {
      settings.addPosterFilename(MoviePosterNaming.MOVIE);
    }
    if (chckbxMoviePosterFilename4.isSelected()) {
      settings.addPosterFilename(MoviePosterNaming.POSTER);
    }
    if (chckbxMoviePosterFilename6.isSelected()) {
      settings.addPosterFilename(MoviePosterNaming.FOLDER);
    }
    if (chckbxMoviePosterFilename7.isSelected()) {
      settings.addPosterFilename(MoviePosterNaming.FILENAME);
    }
    if (chckbxMoviePosterFilename8.isSelected()) {
      settings.addPosterFilename(MoviePosterNaming.FILENAME_POSTER);
    }
    if (chckbxMoviePosterFilename9.isSelected()) {
      settings.addPosterFilename(MoviePosterNaming.COVER);
    }

    // set fanart filenames
    settings.clearFanartFilenames();
    if (chckbxMovieFanartFilename1.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.FILENAME_FANART);
    }
    if (chckbxMovieFanartFilename2.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.FANART);
    }
    if (chckbxMovieFanartFilename3.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.FILENAME_FANART2);
    }
    if (chckbxMovieFanartFilename4.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.BACKDROP);
    }
    if (chckbxMovieFanartFilename5.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.FILENAME_BACKDROP);
    }
    if (chckbxMovieFanartFilename6.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.FILENAME_BACKDROP2);
    }
    if (chckbxMovieFanartFilename7.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.BACKGROUND);
    }
    if (chckbxMovieFanartFilename8.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.FILENAME_BACKGROUND);
    }
    if (chckbxMovieFanartFilename9.isSelected()) {
      settings.addFanartFilename(MovieFanartNaming.FILENAME_BACKGROUND2);
    }

    // set banner filenames
    settings.clearBannerFilenames();
    if (chckbxBanner1.isSelected()) {
      settings.addBannerFilename(MovieBannerNaming.FILENAME_BANNER);
    }
    if (chckbxBanner2.isSelected()) {
      settings.addBannerFilename(MovieBannerNaming.BANNER);
    }

    // set clearart filenames
    settings.clearClearartFilenames();
    if (chckbxClearart1.isSelected()) {
      settings.addClearartFilename(MovieClearartNaming.FILENAME_CLEARART);
    }
    if (chckbxClearart2.isSelected()) {
      settings.addClearartFilename(MovieClearartNaming.CLEARART);
    }

    // set thumb filenames
    settings.clearThumbFilenames();
    if (chckbxThumb1.isSelected()) {
      settings.addThumbFilename(MovieThumbNaming.FILENAME_THUMB);
    }
    if (chckbxThumb2.isSelected()) {
      settings.addThumbFilename(MovieThumbNaming.THUMB);
    }
    if (chckbxThumb3.isSelected()) {
      settings.addThumbFilename(MovieThumbNaming.FILENAME_LANDSCAPE);
    }
    if (chckbxThumb4.isSelected()) {
      settings.addThumbFilename(MovieThumbNaming.LANDSCAPE);
    }

    // set clearlogo filenames
    settings.clearClearlogoFilenames();
    if (chckbxClearlogo1.isSelected()) {
      settings.addClearlogoFilename(MovieClearlogoNaming.FILENAME_CLEARLOGO);
    }
    if (chckbxClearlogo2.isSelected()) {
      settings.addClearlogoFilename(MovieClearlogoNaming.CLEARLOGO);
    }
    if (chckbxLogo1.isSelected()) {
      settings.addClearlogoFilename(MovieClearlogoNaming.FILENAME_LOGO);
    }
    if (chckbxLogo2.isSelected()) {
      settings.addClearlogoFilename(MovieClearlogoNaming.LOGO);
    }

    // set discart filenames
    settings.clearDiscartFilenames();
    if (chckbxDiscart1.isSelected()) {
      settings.addDiscartFilename(MovieDiscartNaming.FILENAME_DISC);
    }
    if (chckbxDiscart2.isSelected()) {
      settings.addDiscartFilename(MovieDiscartNaming.DISC);
    }
    if (chckbxDiscart3.isSelected()) {
      settings.addDiscartFilename(MovieDiscartNaming.FILENAME_DISCART);
    }
    if (chckbxDiscart4.isSelected()) {
      settings.addDiscartFilename(MovieDiscartNaming.DISCART);
    }

    // set keyart filenames
    settings.clearKeyartFilenames();
    if (chckbxKeyart1.isSelected()) {
      settings.addKeyartFilename(MovieKeyartNaming.KEYART);
    }
    if (chckbxKeyart2.isSelected()) {
      settings.addKeyartFilename(MovieKeyartNaming.FILENAME_KEYART);
    }

    // set squareart filenames
    settings.clearSquareartFilenames();
    if (chckbxSquareart1.isSelected()) {
      settings.addSquareartFilename(MovieSquareartNaming.FILENAME_SQUAREART);
    }
    if (chckbxSquareart2.isSelected()) {
      settings.addSquareartFilename(MovieSquareartNaming.SQUAREART);
    }
    if (chckbxSquareart3.isSelected()) {
      settings.addSquareartFilename(MovieSquareartNaming.SQUARE);
    }
    if (chckbxSquareart5.isSelected()) {
      settings.addSquareartFilename(MovieSquareartNaming.FILENAME_SQUARE);
    }
    if (chckbxSquareart4.isSelected()) {
      settings.addSquareartFilename(MovieSquareartNaming.BACKGROUND_SQUARE);
    }
  }
}

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

import java.awt.GridLayout;
import java.awt.event.ItemListener;
import java.io.File;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.core.tvshow.filenaming.TvShowBannerNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowCharacterartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowClearartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowClearlogoNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowDiscartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowEpisodeThumbNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowFanartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowKeyartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowPosterNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonBannerNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonFanartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonPosterNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonThumbNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSquareartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowThumbNaming;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextArea;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link TvShowImageSettingsPanel} is used to display image file name settings.
 *
 * @author Manuel Laggner
 */
class TvShowImageTypeSettingsPanel extends JPanel {
  private final TvShowSettings settings = TvShowModuleManager.getInstance().getSettings();
  private final ItemListener   checkBoxListener;

  private JCheckBox            chckbxEpisodeThumb1;
  private JCheckBox            chckbxEpisodeThumb3;
  private JCheckBox            chckbxEpisodeThumb4;

  private JCheckBox            chckbxPoster1;
  private JCheckBox            chckbxPoster2;
  private JCheckBox            chckbxPoster3;
  private JCheckBox            chckbxFanart1;
  private JCheckBox            chckbxFanart2;
  private JCheckBox            chckbxFanart3;
  private JCheckBox            chckbxBanner1;
  private JCheckBox            chckbxClearart1;
  private JCheckBox            chckbxThumb1;
  private JCheckBox            chckbxThumb2;
  private JCheckBox            chckbxLogo1;
  private JCheckBox            chckbxClearlogo1;
  private JCheckBox            chckbxCharacterart1;
  private JCheckBox            chckbxSeasonPoster1;
  private JCheckBox            chckbxSeasonPoster2;
  private JCheckBox            chckbxSeasonPoster3;
  private JCheckBox            chckbxSeasonPoster4;
  private JCheckBox            chckbxSeasonPoster5;
  private JCheckBox            chckbxSeasonPoster6;
  private JCheckBox            chckbxSeasonFanart1;
  private JCheckBox            chckbxSeasonFanart4;
  private JCheckBox            chckbxSeasonBanner1;
  private JCheckBox            chckbxSeasonBanner2;
  private JCheckBox            chckbxSeasonThumb1;
  private JCheckBox            chckbxSeasonThumb2;
  private JCheckBox            chckbxSeasonThumb3;
  private JCheckBox            chckbxSeasonThumb4;
  private JCheckBox            chckbxKeyart1;
  private JCheckBox            chckbxSquareart1;
  private JCheckBox            chckbxSquareart2;
  private JCheckBox            chckbxSquareart3;
  private JCheckBox            chckbxDiscart1;
  private JCheckBox            chckbxDiscart2;
  private JCheckBox            chckbxSeasonFanart7;
  private JCheckBox            chckbxSeasonFanart8;
  private JCheckBox            chckbxSeasonFanart9;
  private JCheckBox            chckbxSeasonBanner3;
  private JCheckBox            chckbxSeasonThumb5;
  private JCheckBox            chckbxSeasonThumb6;
  private JCheckBox            chckbxSeasonFanart2;
  private JCheckBox            chckbxSeasonFanart3;
  private JCheckBox            chckbxSeasonFanart5;
  private JCheckBox            chckbxSeasonFanart6;

  /**
   * Instantiates a new movie scraper settings panel.
   */
  TvShowImageTypeSettingsPanel() {
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

  private void initComponents() {
    setLayout(new MigLayout("", "[grow]", "[][15lp!][][15lp!][]"));
    {
      JPanel panelFileNamingTvShow = new JPanel(new MigLayout("insets 0", "[20lp!][][50lp,grow]", "[grow][]"));

      JLabel lblFiletypes = new TmmLabel(
          TmmResourceBundle.getString("Settings.artwork.naming") + " - " + TmmResourceBundle.getString("metatag.tvshow"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelFileNamingTvShow, lblFiletypes, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#artwork-filenames"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");

      JPanel panelRow = new JPanel();
      panelFileNamingTvShow.add(panelRow, "cell 1 0,grow");
      panelRow.setLayout(new GridLayout(1, 2, 0, 0));

      JPanel panelColumn1 = new JPanel();
      panelRow.add(panelColumn1);
      panelColumn1.setLayout(new MigLayout("", "[300lp,grow][25lp!]", "[][][][][]"));

      JPanel panelColumn2 = new JPanel();
      panelRow.add(panelColumn2);
      panelColumn2.setLayout(new MigLayout("", "[grow]", "[][]"));

      {
        JPanel panelPoster = new JPanel();
        panelPoster.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.poster")));
        panelPoster.setLayout(new MigLayout("", "[]", "[][][]"));

        chckbxPoster1 = new JCheckBox("poster.*");
        panelPoster.add(chckbxPoster1, "cell 0 0");

        chckbxPoster2 = new JCheckBox("folder.*");
        panelPoster.add(chckbxPoster2, "cell 0 1");

        chckbxPoster3 = new JCheckBox("<tv_show_folder_name>.*");
        panelPoster.add(chckbxPoster3, "cell 0 2");

        panelColumn1.add(panelPoster, "cell 0 0,growx");
      }
      {
        JPanel panelBanner = new JPanel();
        panelBanner.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.banner")));
        panelBanner.setLayout(new MigLayout("flowy", "[grow]", "[]"));

        chckbxBanner1 = new JCheckBox("banner.*");
        panelBanner.add(chckbxBanner1, "cell 0 0");

        panelColumn1.add(panelBanner, "cell 0 1,growx");
      }
      {
        JPanel panelLogo = new JPanel();
        panelLogo.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.clearlogo")));
        panelLogo.setLayout(new MigLayout("", "[]", "[][]"));

        chckbxClearlogo1 = new JCheckBox("clearlogo.*");
        panelLogo.add(chckbxClearlogo1, "cell 0 0");

        chckbxLogo1 = new JCheckBox("logo.*");
        panelLogo.add(chckbxLogo1, "cell 0 1");

        panelColumn1.add(panelLogo, "cell 0 2,growx");
      }
      {
        JPanel panelThumb = new JPanel();
        panelThumb.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.thumb")));
        panelThumb.setLayout(new MigLayout("", "[grow]", "[][]"));

        chckbxThumb1 = new JCheckBox("thumb.*");
        panelThumb.add(chckbxThumb1, "cell 0 0");

        chckbxThumb2 = new JCheckBox("landscape.*");
        panelThumb.add(chckbxThumb2, "cell 0 1");

        panelColumn1.add(panelThumb, "cell 0 3,growx");
      }
      {
        JPanel panelSquareart = new JPanel();
        panelColumn1.add(panelSquareart, "cell 0 4,growx");
        panelSquareart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.squareart")));
        panelSquareart.setLayout(new MigLayout("", "[grow]", "[][][]"));

        chckbxSquareart1 = new JCheckBox("squareart.*");
        panelSquareart.add(chckbxSquareart1, "cell 0 0");

        chckbxSquareart2 = new JCheckBox("square.*");
        panelSquareart.add(chckbxSquareart2, "cell 0 1");

        chckbxSquareart3 = new JCheckBox("backgroundSquare.*");
        panelSquareart.add(chckbxSquareart3, "cell 0 2");
      }

      {
        JPanel panelFanart = new JPanel();
        panelColumn2.add(panelFanart, "cell 0 0,growx");
        panelFanart.setBorder(new TitledBorder(null, TmmResourceBundle.getString("mediafiletype.fanart")));
        panelFanart.setLayout(new MigLayout("", "[]", "[][][]"));

        chckbxFanart1 = new JCheckBox("fanart.*");
        panelFanart.add(chckbxFanart1, "cell 0 0");

        chckbxFanart2 = new JCheckBox("backdrop.*");
        panelFanart.add(chckbxFanart2, "cell 0 1");

        chckbxFanart3 = new JCheckBox("background.*");
        panelFanart.add(chckbxFanart3, "cell 0 2");
      }
      {
        JPanel panelClearart = new JPanel();
        panelClearart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.clearart")));
        panelClearart.setLayout(new MigLayout("", "[]", "[]"));

        chckbxClearart1 = new JCheckBox("clearart.*");
        panelClearart.add(chckbxClearart1, "cell 0 0");

        panelColumn2.add(panelClearart, "cell 0 1,growx");
      }
      {
        JPanel panelDiscart = new JPanel();
        panelDiscart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.disc")));
        panelDiscart.setLayout(new MigLayout("", "[grow]", "[][]"));

        chckbxDiscart1 = new JCheckBox("discart.*");
        panelDiscart.add(chckbxDiscart1, "cell 0 0");

        chckbxDiscart2 = new JCheckBox("disc.*");
        panelDiscart.add(chckbxDiscart2, "cell 0 1");

        panelColumn2.add(panelDiscart, "cell 0 2,growx");
      }
      {
        JPanel panelCharacterArt = new JPanel();
        panelCharacterArt.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.characterart")));
        panelCharacterArt.setLayout(new MigLayout("", "[grow]", "[]"));

        chckbxCharacterart1 = new JCheckBox("characterart.*");
        panelCharacterArt.add(chckbxCharacterart1, "cell 0 0");

        panelColumn2.add(panelCharacterArt, "cell 0 3,growx");
      }
      {
        JPanel panelKeyart = new JPanel();
        panelKeyart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.keyart")));
        panelKeyart.setLayout(new MigLayout("", "[]", "[]"));

        chckbxKeyart1 = new JCheckBox("keyart.*");
        panelKeyart.add(chckbxKeyart1, "cell 0 0");

        panelColumn2.add(panelKeyart, "cell 0 4,growx");
      }
      {
        JTextArea tpFileNamingHint = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.naming.info"));
        TmmFontHelper.changeFont(tpFileNamingHint, 0.833);
        panelFileNamingTvShow.add(tpFileNamingHint, "cell 1 1 2 1,growx,wmin 0");
      }
    }
    {
      JPanel panelFileNamingSeason = new JPanel(new MigLayout("insets 0, flowy", "[20lp!][][50lp,grow]", "[grow][grow]"));

      JLabel lblFiletypes = new TmmLabel(
          TmmResourceBundle.getString("Settings.artwork.naming") + " - " + TmmResourceBundle.getString("metatag.season"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelFileNamingSeason, lblFiletypes, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#artwork-filenames"));
      add(collapsiblePanel, "cell 0 2,growx,wmin 0");
      {
        JPanel panelRow = new JPanel();
        panelFileNamingSeason.add(panelRow, "cell 1 0,grow");
        panelRow.setLayout(new GridLayout(1, 2, 0, 0));

        JPanel panelColumn1 = new JPanel();
        panelRow.add(panelColumn1);
        panelColumn1.setLayout(new MigLayout("", "[300lp,grow][25lp!]", "[][]"));

        JPanel panelColumn2 = new JPanel();
        panelRow.add(panelColumn2);
        panelColumn2.setLayout(new MigLayout("", "[grow]", "[][]"));

        {
          JPanel panelSeasonPoster = new JPanel();
          panelSeasonPoster.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.season_poster")));
          panelSeasonPoster.setLayout(new MigLayout("", "[]", "[][][][][][]"));

          chckbxSeasonPoster1 = new JCheckBox("seasonXX-poster.*");
          panelSeasonPoster.add(chckbxSeasonPoster1, "cell 0 0");

          chckbxSeasonPoster2 = new JCheckBox("<season_folder>" + File.separator + "seasonXX-poster.*");
          panelSeasonPoster.add(chckbxSeasonPoster2, "cell 0 1");

          chckbxSeasonPoster4 = new JCheckBox("<season_folder>" + File.separator + "seasonXX.*");
          panelSeasonPoster.add(chckbxSeasonPoster4, "cell 0 2");

          chckbxSeasonPoster3 = new JCheckBox("<season_folder>" + File.separator + "folder.*");
          panelSeasonPoster.add(chckbxSeasonPoster3, "cell 0 3");

          chckbxSeasonPoster5 = new JCheckBox("<season_folder>" + File.separator + "poster.*");
          panelSeasonPoster.add(chckbxSeasonPoster5, "cell 0 4");

          chckbxSeasonPoster6 = new JCheckBox("<season_folder>" + File.separator + "cover.*");
          panelSeasonPoster.add(chckbxSeasonPoster6, "cell 0 5");

          panelColumn1.add(panelSeasonPoster, "cell 0 0,growx");
        }

        {
          JPanel panelSeasonThumb = new JPanel();
          panelColumn1.add(panelSeasonThumb, "cell 0 1,growx");
          panelSeasonThumb.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.season_thumb")));
          panelSeasonThumb.setLayout(new MigLayout("", "[]", "[][][][][][]"));

          chckbxSeasonThumb1 = new JCheckBox("seasonXX-thumb.*");
          panelSeasonThumb.add(chckbxSeasonThumb1, "cell 0 0");

          chckbxSeasonThumb3 = new JCheckBox("seasonXX-landscape.*");
          panelSeasonThumb.add(chckbxSeasonThumb3, "cell 0 1");

          chckbxSeasonThumb2 = new JCheckBox("<season_folder>/seasonXX-thumb.*");
          panelSeasonThumb.add(chckbxSeasonThumb2, "cell 0 2");

          chckbxSeasonThumb4 = new JCheckBox("<season_folder>/seasonXX-landscape.*");
          panelSeasonThumb.add(chckbxSeasonThumb4, "cell 0 3");

          chckbxSeasonThumb5 = new JCheckBox("<season_folder>/thumb.*");
          panelSeasonThumb.add(chckbxSeasonThumb5, "cell 0 4");

          chckbxSeasonThumb6 = new JCheckBox("<season_folder>/landscape.*");
          panelSeasonThumb.add(chckbxSeasonThumb6, "cell 0 5");
        }

        {
          JPanel panelSeasonFanart = new JPanel();
          panelSeasonFanart.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.season_fanart")));
          panelSeasonFanart.setLayout(new MigLayout("", "[grow]", "[][][][][][][][][]"));

          chckbxSeasonFanart1 = new JCheckBox("seasonXX-fanart.*");
          panelSeasonFanart.add(chckbxSeasonFanart1, "cell 0 0");

          chckbxSeasonFanart2 = new JCheckBox("seasonXX-backdrop.*");
          panelSeasonFanart.add(chckbxSeasonFanart2, "cell 0 1");

          chckbxSeasonFanart3 = new JCheckBox("seasonXX-background.*");
          panelSeasonFanart.add(chckbxSeasonFanart3, "cell 0 2");

          chckbxSeasonFanart4 = new JCheckBox("<season_folder>" + File.separator + "seasonXX-fanart.*");
          panelSeasonFanart.add(chckbxSeasonFanart4, "cell 0 3");

          chckbxSeasonFanart5 = new JCheckBox("<season_folder>" + File.separator + "seasonXX-backdrop.*");
          panelSeasonFanart.add(chckbxSeasonFanart5, "cell 0 4");

          chckbxSeasonFanart6 = new JCheckBox("<season_folder>" + File.separator + "seasonXX-background.*");
          panelSeasonFanart.add(chckbxSeasonFanart6, "cell 0 5");

          chckbxSeasonFanart7 = new JCheckBox("<season_folder>" + File.separator + "fanart.*");
          panelSeasonFanart.add(chckbxSeasonFanart7, "cell 0 6");

          chckbxSeasonFanart8 = new JCheckBox("<season_folder>" + File.separator + "backdrop.*");
          panelSeasonFanart.add(chckbxSeasonFanart8, "cell 0 7");

          chckbxSeasonFanart9 = new JCheckBox("<season_folder>" + File.separator + "background.*");
          panelSeasonFanart.add(chckbxSeasonFanart9, "cell 0 8");

          panelColumn2.add(panelSeasonFanart, "cell 0 0,growx");
        }
        {
          JPanel panelSeasonBanner = new JPanel();
          panelColumn2.add(panelSeasonBanner, "cell 0 1,growx");
          panelSeasonBanner.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.season_banner")));
          panelSeasonBanner.setLayout(new MigLayout("", "[grow]", "[][][]"));

          chckbxSeasonBanner1 = new JCheckBox("seasonXX-banner.*");
          panelSeasonBanner.add(chckbxSeasonBanner1, "cell 0 0");

          chckbxSeasonBanner2 = new JCheckBox("<season_folder>/seasonXX-banner.*");
          panelSeasonBanner.add(chckbxSeasonBanner2, "cell 0 1");

          chckbxSeasonBanner3 = new JCheckBox("<season_folder>/banner.*");
          panelSeasonBanner.add(chckbxSeasonBanner3, "cell 0 2");
        }
      }
      {
        JTextArea tpFileNamingHint = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.naming.info"));
        TmmFontHelper.changeFont(tpFileNamingHint, 0.833);
        panelFileNamingSeason.add(tpFileNamingHint, "cell 1 1 2 1,growx,wmin 0");
      }
    }
    {
      JPanel panelFileNamingEpisode = new JPanel(new MigLayout("insets 0, flowy", "[20lp!][][50lp,grow]", "[grow][grow]"));

      JLabel lblFiletypes = new TmmLabel(
          TmmResourceBundle.getString("Settings.artwork.naming") + " - " + TmmResourceBundle.getString("metatag.episode"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelFileNamingEpisode, lblFiletypes, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#artwork-filenames"));
      add(collapsiblePanel, "cell 0 4,growx,wmin 0");
      {
        JPanel panelRow = new JPanel();
        panelFileNamingEpisode.add(panelRow, "cell 1 0,grow");
        panelRow.setLayout(new GridLayout(1, 2, 0, 0));

        JPanel panelColumn1 = new JPanel();
        panelRow.add(panelColumn1);
        panelColumn1.setLayout(new MigLayout("", "[300lp,grow]", "[]"));

        {
          JPanel panelEpisodeThumb = new JPanel();
          panelEpisodeThumb.setBorder(new TitledBorder(TmmResourceBundle.getString("mediafiletype.episode_thumb")));
          panelEpisodeThumb.setLayout(new MigLayout("", "[]", "[][][]"));

          chckbxEpisodeThumb1 = new JCheckBox("<dynamic>-thumb.*");
          panelEpisodeThumb.add(chckbxEpisodeThumb1, "cell 0 0");

          chckbxEpisodeThumb3 = new JCheckBox("<dynamic>.*");
          panelEpisodeThumb.add(chckbxEpisodeThumb3, "cell 0 1");

          chckbxEpisodeThumb4 = new JCheckBox("<dynamic>.tbn");
          panelEpisodeThumb.add(chckbxEpisodeThumb4, "cell 0 2");

          panelColumn1.add(panelEpisodeThumb, "cell 0 0,growx");
        }
      }
      {
        JTextArea tpFileNamingHint = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.naming.info"));
        panelFileNamingEpisode.add(tpFileNamingHint, "cell 1 1 2 1,growx,wmin 0");
        TmmFontHelper.changeFont(tpFileNamingHint, 0.833);
      }
    }
  }

  private void buildCheckBoxes() {
    chckbxPoster1.removeItemListener(checkBoxListener);
    chckbxPoster2.removeItemListener(checkBoxListener);
    chckbxPoster3.removeItemListener(checkBoxListener);
    clearSelection(chckbxPoster1, chckbxPoster2, chckbxPoster3);

    chckbxFanart1.removeItemListener(checkBoxListener);
    chckbxFanart2.removeItemListener(checkBoxListener);
    chckbxFanart3.removeItemListener(checkBoxListener);
    clearSelection(chckbxFanart1, chckbxFanart2, chckbxFanart3);

    chckbxBanner1.removeItemListener(checkBoxListener);
    clearSelection(chckbxBanner1);

    chckbxClearart1.removeItemListener(checkBoxListener);
    clearSelection(chckbxClearart1);

    chckbxThumb1.removeItemListener(checkBoxListener);
    chckbxThumb2.removeItemListener(checkBoxListener);
    clearSelection(chckbxThumb1, chckbxThumb2);

    chckbxLogo1.removeItemListener(checkBoxListener);
    clearSelection(chckbxLogo1);

    chckbxClearlogo1.removeItemListener(checkBoxListener);
    clearSelection(chckbxClearlogo1);

    chckbxDiscart1.removeItemListener(checkBoxListener);
    chckbxDiscart2.removeItemListener(checkBoxListener);
    clearSelection(chckbxDiscart1, chckbxDiscart2);

    chckbxCharacterart1.removeItemListener(checkBoxListener);
    clearSelection(chckbxCharacterart1);

    chckbxKeyart1.removeItemListener(checkBoxListener);
    clearSelection(chckbxKeyart1);

    chckbxSquareart1.removeItemListener(checkBoxListener);
    chckbxSquareart2.removeItemListener(checkBoxListener);
    chckbxSquareart3.removeItemListener(checkBoxListener);
    clearSelection(chckbxSquareart1, chckbxSquareart2, chckbxSquareart3);

    chckbxSeasonPoster1.removeItemListener(checkBoxListener);
    chckbxSeasonPoster2.removeItemListener(checkBoxListener);
    chckbxSeasonPoster3.removeItemListener(checkBoxListener);
    chckbxSeasonPoster4.removeItemListener(checkBoxListener);
    chckbxSeasonPoster5.removeItemListener(checkBoxListener);
    chckbxSeasonPoster6.removeItemListener(checkBoxListener);
    clearSelection(chckbxSeasonPoster1, chckbxSeasonPoster2, chckbxSeasonPoster3, chckbxSeasonPoster4, chckbxSeasonPoster5, chckbxSeasonPoster6);

    chckbxSeasonFanart1.removeItemListener(checkBoxListener);
    chckbxSeasonFanart2.removeItemListener(checkBoxListener);
    chckbxSeasonFanart3.removeItemListener(checkBoxListener);
    chckbxSeasonFanart4.removeItemListener(checkBoxListener);
    chckbxSeasonFanart5.removeItemListener(checkBoxListener);
    chckbxSeasonFanart6.removeItemListener(checkBoxListener);
    chckbxSeasonFanart7.removeItemListener(checkBoxListener);
    chckbxSeasonFanart8.removeItemListener(checkBoxListener);
    chckbxSeasonFanart9.removeItemListener(checkBoxListener);
    clearSelection(chckbxSeasonFanart1, chckbxSeasonFanart2, chckbxSeasonFanart3, chckbxSeasonFanart4, chckbxSeasonFanart5, chckbxSeasonFanart6,
        chckbxSeasonFanart7, chckbxSeasonFanart8, chckbxSeasonFanart9);

    chckbxSeasonBanner1.removeItemListener(checkBoxListener);
    chckbxSeasonBanner2.removeItemListener(checkBoxListener);
    chckbxSeasonBanner3.removeItemListener(checkBoxListener);
    clearSelection(chckbxSeasonBanner1, chckbxSeasonBanner2, chckbxSeasonBanner3);

    chckbxSeasonThumb1.removeItemListener(checkBoxListener);
    chckbxSeasonThumb2.removeItemListener(checkBoxListener);
    chckbxSeasonThumb3.removeItemListener(checkBoxListener);
    chckbxSeasonThumb4.removeItemListener(checkBoxListener);
    chckbxSeasonThumb5.removeItemListener(checkBoxListener);
    chckbxSeasonThumb6.removeItemListener(checkBoxListener);
    clearSelection(chckbxSeasonThumb1, chckbxSeasonThumb2, chckbxSeasonThumb3, chckbxSeasonThumb4, chckbxSeasonThumb5, chckbxSeasonThumb6);

    chckbxEpisodeThumb1.removeItemListener(checkBoxListener);
    chckbxEpisodeThumb3.removeItemListener(checkBoxListener);
    chckbxEpisodeThumb4.removeItemListener(checkBoxListener);
    clearSelection(chckbxEpisodeThumb1, chckbxEpisodeThumb3, chckbxEpisodeThumb4);

    for (TvShowPosterNaming posterNaming : settings.getPosterFilenames()) {
      switch (posterNaming) {
        case POSTER:
          chckbxPoster1.setSelected(true);
          break;

        case FOLDER:
          chckbxPoster2.setSelected(true);
          break;

        case FOLDER_NAME:
          chckbxPoster3.setSelected(true);
          break;
      }
    }

    for (TvShowFanartNaming fanartNaming : settings.getFanartFilenames()) {
      switch (fanartNaming) {
        case FANART:
          chckbxFanart1.setSelected(true);
          break;

        case BACKDROP:
          chckbxFanart2.setSelected(true);
          break;

        case BACKGROUND:
          chckbxFanart3.setSelected(true);
          break;
      }
    }

    for (TvShowBannerNaming bannerNaming : settings.getBannerFilenames()) {
      switch (bannerNaming) {
        case BANNER:
          chckbxBanner1.setSelected(true);
          break;
      }
    }

    for (TvShowClearartNaming clearartNaming : settings.getClearartFilenames()) {
      switch (clearartNaming) {
        case CLEARART:
          chckbxClearart1.setSelected(true);
          break;
      }
    }

    for (TvShowDiscartNaming discartNaming : settings.getDiscartFilenames()) {
      switch (discartNaming) {
        case DISCART:
          chckbxDiscart1.setSelected(true);
          break;

        case DISC:
          chckbxDiscart2.setSelected(true);
          break;
      }
    }

    for (TvShowThumbNaming thumbNaming : settings.getThumbFilenames()) {
      switch (thumbNaming) {
        case THUMB:
          chckbxThumb1.setSelected(true);
          break;

        case LANDSCAPE:
          chckbxThumb2.setSelected(true);
          break;
      }
    }

    for (TvShowClearlogoNaming clearlogoNaming : settings.getClearlogoFilenames()) {
      switch (clearlogoNaming) {
        case CLEARLOGO:
          chckbxClearlogo1.setSelected(true);
          break;

        case LOGO:
          chckbxLogo1.setSelected(true);
          break;
      }
    }

    for (TvShowCharacterartNaming characterartNaming : settings.getCharacterartFilenames()) {
      switch (characterartNaming) {
        case CHARACTERART:
          chckbxCharacterart1.setSelected(true);
          break;
      }
    }

    for (TvShowKeyartNaming keyartNaming : settings.getKeyartFilenames()) {
      switch (keyartNaming) {
        case KEYART:
          chckbxKeyart1.setSelected(true);
          break;
      }
    }

    for (TvShowSquareartNaming squareartNaming : settings.getSquareartFilenames()) {
      switch (squareartNaming) {
        case SQUAREART:
          chckbxSquareart1.setSelected(true);
          break;

        case SQUARE:
          chckbxSquareart2.setSelected(true);
          break;

        case BACKGROUND_SQUARE:
          chckbxSquareart3.setSelected(true);
          break;
      }
    }

    for (TvShowSeasonPosterNaming seasonPosterNaming : settings.getSeasonPosterFilenames()) {
      switch (seasonPosterNaming) {
        case SEASON_POSTER:
          chckbxSeasonPoster1.setSelected(true);
          break;

        case SEASON_FOLDER:
          chckbxSeasonPoster2.setSelected(true);
          break;

        case SEASON_FOLDER2:
          chckbxSeasonPoster4.setSelected(true);
          break;

        case FOLDER:
          chckbxSeasonPoster3.setSelected(true);
          break;

        case POSTER:
          chckbxSeasonPoster5.setSelected(true);
          break;

        case COVER:
          chckbxSeasonPoster6.setSelected(true);
          break;
      }
    }

    for (TvShowSeasonBannerNaming seasonBannerNaming : settings.getSeasonBannerFilenames()) {
      switch (seasonBannerNaming) {
        case SEASON_BANNER:
          chckbxSeasonBanner1.setSelected(true);
          break;

        case SEASON_FOLDER:
          chckbxSeasonBanner2.setSelected(true);
          break;

        case BANNER:
          chckbxSeasonBanner3.setSelected(true);
          break;
      }
    }

    for (TvShowSeasonFanartNaming seasonFanartNaming : settings.getSeasonFanartFilenames()) {
      switch (seasonFanartNaming) {
        case SEASON_FANART:
          chckbxSeasonFanart1.setSelected(true);
          break;

        case SEASON_BACKDROP:
          chckbxSeasonFanart2.setSelected(true);
          break;

        case SEASON_BACKGROUND:
          chckbxSeasonFanart3.setSelected(true);
          break;

        case SEASON_FOLDER:
          chckbxSeasonFanart4.setSelected(true);
          break;

        case SEASON_FOLDER_BACKDROP:
          chckbxSeasonFanart5.setSelected(true);
          break;

        case SEASON_FOLDER_BACKGROUND:
          chckbxSeasonFanart6.setSelected(true);
          break;

        case FANART:
          chckbxSeasonFanart7.setSelected(true);
          break;

        case BACKDROP:
          chckbxSeasonFanart8.setSelected(true);
          break;

        case BACKGROUND:
          chckbxSeasonFanart9.setSelected(true);
          break;
      }
    }

    for (TvShowSeasonThumbNaming seasonThumbNaming : settings.getSeasonThumbFilenames()) {
      switch (seasonThumbNaming) {
        case SEASON_THUMB:
          chckbxSeasonThumb1.setSelected(true);
          break;

        case SEASON_FOLDER:
          chckbxSeasonThumb2.setSelected(true);
          break;

        case SEASON_LANDSCAPE:
          chckbxSeasonThumb3.setSelected(true);
          break;

        case SEASON_FOLDER_LANDSCAPE:
          chckbxSeasonThumb4.setSelected(true);
          break;

        case THUMB:
          chckbxSeasonThumb5.setSelected(true);
          break;

        case LANDSCAPE:
          chckbxSeasonThumb6.setSelected(true);
          break;
      }
    }

    for (TvShowEpisodeThumbNaming thumbNaming : settings.getEpisodeThumbFilenames()) {
      switch (thumbNaming) {
        case FILENAME_THUMB:
          chckbxEpisodeThumb1.setSelected(true);
          break;

        case FILENAME:
          chckbxEpisodeThumb3.setSelected(true);
          break;

        case FILENAME_TBN:
          chckbxEpisodeThumb4.setSelected(true);
          break;
      }
    }

    chckbxPoster1.addItemListener(checkBoxListener);
    chckbxPoster2.addItemListener(checkBoxListener);
    chckbxPoster3.addItemListener(checkBoxListener);

    chckbxFanart1.addItemListener(checkBoxListener);
    chckbxFanart2.addItemListener(checkBoxListener);
    chckbxFanart3.addItemListener(checkBoxListener);

    chckbxBanner1.addItemListener(checkBoxListener);

    chckbxClearart1.addItemListener(checkBoxListener);

    chckbxThumb1.addItemListener(checkBoxListener);
    chckbxThumb2.addItemListener(checkBoxListener);

    chckbxLogo1.addItemListener(checkBoxListener);

    chckbxClearlogo1.addItemListener(checkBoxListener);

    chckbxDiscart1.addItemListener(checkBoxListener);
    chckbxDiscart2.addItemListener(checkBoxListener);

    chckbxCharacterart1.addItemListener(checkBoxListener);

    chckbxKeyart1.addItemListener(checkBoxListener);

    chckbxSquareart1.addItemListener(checkBoxListener);
    chckbxSquareart2.addItemListener(checkBoxListener);
    chckbxSquareart3.addItemListener(checkBoxListener);

    chckbxSeasonPoster1.addItemListener(checkBoxListener);
    chckbxSeasonPoster2.addItemListener(checkBoxListener);
    chckbxSeasonPoster3.addItemListener(checkBoxListener);
    chckbxSeasonPoster4.addItemListener(checkBoxListener);
    chckbxSeasonPoster5.addItemListener(checkBoxListener);
    chckbxSeasonPoster6.addItemListener(checkBoxListener);

    chckbxSeasonBanner1.addItemListener(checkBoxListener);
    chckbxSeasonBanner2.addItemListener(checkBoxListener);
    chckbxSeasonBanner3.addItemListener(checkBoxListener);

    chckbxSeasonFanart1.addItemListener(checkBoxListener);
    chckbxSeasonFanart2.addItemListener(checkBoxListener);
    chckbxSeasonFanart3.addItemListener(checkBoxListener);
    chckbxSeasonFanart4.addItemListener(checkBoxListener);
    chckbxSeasonFanart5.addItemListener(checkBoxListener);
    chckbxSeasonFanart6.addItemListener(checkBoxListener);
    chckbxSeasonFanart7.addItemListener(checkBoxListener);
    chckbxSeasonFanart8.addItemListener(checkBoxListener);
    chckbxSeasonFanart9.addItemListener(checkBoxListener);

    chckbxSeasonThumb1.addItemListener(checkBoxListener);
    chckbxSeasonThumb2.addItemListener(checkBoxListener);
    chckbxSeasonThumb3.addItemListener(checkBoxListener);
    chckbxSeasonThumb4.addItemListener(checkBoxListener);
    chckbxSeasonThumb5.addItemListener(checkBoxListener);
    chckbxSeasonThumb6.addItemListener(checkBoxListener);

    chckbxEpisodeThumb1.addItemListener(checkBoxListener);
    chckbxEpisodeThumb3.addItemListener(checkBoxListener);
    chckbxEpisodeThumb4.addItemListener(checkBoxListener);
  }

  private void clearSelection(JCheckBox... checkBoxes) {
    for (JCheckBox checkBox : checkBoxes) {
      checkBox.setSelected(false);
    }
  }

  /**
   * Check changes.
   */
  private void checkChanges() {
    settings.clearPosterFilenames();
    if (chckbxPoster1.isSelected()) {
      settings.addPosterFilename(TvShowPosterNaming.POSTER);
    }
    if (chckbxPoster2.isSelected()) {
      settings.addPosterFilename(TvShowPosterNaming.FOLDER);
    }
    if (chckbxPoster3.isSelected()) {
      settings.addPosterFilename(TvShowPosterNaming.FOLDER_NAME);
    }

    settings.clearFanartFilenames();
    if (chckbxFanart1.isSelected()) {
      settings.addFanartFilename(TvShowFanartNaming.FANART);
    }
    if (chckbxFanart2.isSelected()) {
      settings.addFanartFilename(TvShowFanartNaming.BACKDROP);
    }
    if (chckbxFanart3.isSelected()) {
      settings.addFanartFilename(TvShowFanartNaming.BACKGROUND);
    }

    settings.clearBannerFilenames();
    if (chckbxBanner1.isSelected()) {
      settings.addBannerFilename(TvShowBannerNaming.BANNER);
    }

    settings.clearClearartFilenames();
    if (chckbxClearart1.isSelected()) {
      settings.addClearartFilename(TvShowClearartNaming.CLEARART);
    }

    settings.clearThumbFilenames();
    if (chckbxThumb1.isSelected()) {
      settings.addThumbFilename(TvShowThumbNaming.THUMB);
    }
    if (chckbxThumb2.isSelected()) {
      settings.addThumbFilename(TvShowThumbNaming.LANDSCAPE);
    }

    settings.clearClearlogoFilenames();
    if (chckbxClearlogo1.isSelected()) {
      settings.addClearlogoFilename(TvShowClearlogoNaming.CLEARLOGO);
    }
    if (chckbxLogo1.isSelected()) {
      settings.addClearlogoFilename(TvShowClearlogoNaming.LOGO);
    }

    settings.clearDiscartFilenames();
    if (chckbxDiscart1.isSelected()) {
      settings.addDiscartFilename(TvShowDiscartNaming.DISCART);
    }
    if (chckbxDiscart2.isSelected()) {
      settings.addDiscartFilename(TvShowDiscartNaming.DISC);
    }

    settings.clearCharacterartFilenames();
    if (chckbxCharacterart1.isSelected()) {
      settings.addCharacterartFilename(TvShowCharacterartNaming.CHARACTERART);
    }

    settings.clearKeyartFilenames();
    if (chckbxKeyart1.isSelected()) {
      settings.addKeyartFilename(TvShowKeyartNaming.KEYART);
    }

    settings.clearSquareartFilenames();
    if (chckbxSquareart1.isSelected()) {
      settings.addSquareartFilename(TvShowSquareartNaming.SQUAREART);
    }
    if (chckbxSquareart2.isSelected()) {
      settings.addSquareartFilename(TvShowSquareartNaming.SQUARE);
    }
    if (chckbxSquareart3.isSelected()) {
      settings.addSquareartFilename(TvShowSquareartNaming.BACKGROUND_SQUARE);
    }

    settings.clearSeasonPosterFilenames();
    if (chckbxSeasonPoster1.isSelected()) {
      settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.SEASON_POSTER);
    }
    if (chckbxSeasonPoster2.isSelected()) {
      settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.SEASON_FOLDER);
    }
    if (chckbxSeasonPoster3.isSelected()) {
      settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.FOLDER);
    }
    if (chckbxSeasonPoster4.isSelected()) {
      settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.SEASON_FOLDER2);
    }
    if (chckbxSeasonPoster5.isSelected()) {
      settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.POSTER);
    }
    if (chckbxSeasonPoster6.isSelected()) {
      settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.COVER);
    }

    settings.clearSeasonBannerFilenames();
    if (chckbxSeasonBanner1.isSelected()) {
      settings.addSeasonBannerFilename(TvShowSeasonBannerNaming.SEASON_BANNER);
    }
    if (chckbxSeasonBanner2.isSelected()) {
      settings.addSeasonBannerFilename(TvShowSeasonBannerNaming.SEASON_FOLDER);
    }
    if (chckbxSeasonBanner3.isSelected()) {
      settings.addSeasonBannerFilename(TvShowSeasonBannerNaming.BANNER);
    }

    settings.clearSeasonFanartFilenames();
    if (chckbxSeasonFanart1.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_FANART);
    }
    if (chckbxSeasonFanart2.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_BACKDROP);
    }
    if (chckbxSeasonFanart3.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_BACKGROUND);
    }
    if (chckbxSeasonFanart4.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_FOLDER);
    }
    if (chckbxSeasonFanart5.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_FOLDER_BACKDROP);
    }
    if (chckbxSeasonFanart6.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_FOLDER_BACKGROUND);
    }
    if (chckbxSeasonFanart7.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.FANART);
    }
    if (chckbxSeasonFanart8.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.BACKDROP);
    }
    if (chckbxSeasonFanart9.isSelected()) {
      settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.BACKGROUND);
    }

    settings.clearSeasonThumbFilenames();
    if (chckbxSeasonThumb1.isSelected()) {
      settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_THUMB);
    }
    if (chckbxSeasonThumb2.isSelected()) {
      settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_FOLDER);
    }
    if (chckbxSeasonThumb3.isSelected()) {
      settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_LANDSCAPE);
    }
    if (chckbxSeasonThumb4.isSelected()) {
      settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_FOLDER_LANDSCAPE);
    }
    if (chckbxSeasonThumb5.isSelected()) {
      settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.THUMB);
    }
    if (chckbxSeasonThumb6.isSelected()) {
      settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.LANDSCAPE);
    }

    settings.clearEpisodeThumbFilenames();
    if (chckbxEpisodeThumb1.isSelected()) {
      settings.addEpisodeThumbFilename(TvShowEpisodeThumbNaming.FILENAME_THUMB);
    }
    if (chckbxEpisodeThumb3.isSelected()) {
      settings.addEpisodeThumbFilename(TvShowEpisodeThumbNaming.FILENAME);
    }
    if (chckbxEpisodeThumb4.isSelected()) {
      settings.addEpisodeThumbFilename(TvShowEpisodeThumbNaming.FILENAME_TBN);
    }
  }
}

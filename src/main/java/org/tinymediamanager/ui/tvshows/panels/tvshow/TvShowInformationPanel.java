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
package org.tinymediamanager.ui.tvshows.panels.tvshow;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.ui.ColumnLayout;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.WrapLayout;
import org.tinymediamanager.ui.components.NoBorderScrollPane;
import org.tinymediamanager.ui.components.label.LinkLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.IdLinkPanel;
import org.tinymediamanager.ui.components.textfield.LinkTextArea;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPane;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPaneHTML;
import org.tinymediamanager.ui.panels.InformationPanel;
import org.tinymediamanager.ui.panels.MediaInformationLogosPanel;
import org.tinymediamanager.ui.panels.RatingPanel;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel;

import net.miginfocom.swing.MigLayout;

/**
 * The Class TvShowInformationPanel.
 *
 * @author Manuel Laggner
 */
public class TvShowInformationPanel extends InformationPanel {
  private static final Logger        LOGGER                 = LoggerFactory.getLogger(TvShowInformationPanel.class);

  private static final String        LAYOUT_ARTWORK_VISIBLE = "[n:100lp:20%, grow][300lp:300lp,grow 350]";
  private static final String        LAYOUT_ARTWORK_HIDDEN  = "[][300lp:300lp,grow 350]";

  private JTextPane                  taGenres;
  private JLabel                     lblCertification;
  private LinkLabel                  lblThetvdbId;
  private LinkLabel                  lblImdbId;
  private LinkTextArea               lblPath;
  private JLabel                     lblPremiered;
  private JTextPane                  taStudio;
  private JLabel                     lblStatus;
  private JLabel                     lblYear;
  private JLabel                     lblEpisodeGroup;
  private JTextPane                  taTags;
  private JPanel                     panelOtherIds;
  private JLabel                     lblCountry;
  private JLabel                     lblRuntime;
  private JTextPane                  taNote;
  private JLabel                     lblTvShowName;
  private JTextPane                  taOverview;
  private JLabel                     lblTagline;
  private MediaInformationLogosPanel panelLogos;
  private JLabel                     lblOriginalTitle;
  private JScrollPane                scrollPane;
  private JLabel                     lblCertificationLogo;
  private RatingPanel                ratingPanel;

  /**
   * Instantiates a new tv show information panel.
   *
   * @param tvShowSelectionModel
   *          the {@link TvShowSelectionModel} to bind to
   */
  public TvShowInformationPanel(TvShowSelectionModel tvShowSelectionModel) {

    initComponents();

    // action listeners
    lblImdbId.addActionListener(arg0 -> {
      String url = "https://www.imdb.com/title/" + lblImdbId.getText();
      try {
        TmmUIHelper.browseUrl(url);
      }
      catch (Exception e) {
        LOGGER.error("Could not open '{}' in browser - '{}'", url, e.getMessage());
        MessageManager.getInstance()
            .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() }));
      }
    });

    lblThetvdbId.addActionListener(arg0 -> {
      String url = "https://thetvdb.com/?tab=series&id=" + lblThetvdbId.getText();
      try {
        TmmUIHelper.browseUrl(url);
      }
      catch (Exception e) {
        LOGGER.error("Could not open '{}' in browser - '{}'", url, e.getMessage());
        MessageManager.getInstance()
            .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() }));
      }
    });

    lblPath.addActionListener(e -> {
      if (StringUtils.isNotBlank(lblPath.getText())) {
        // get the location from the label
        Path path = Paths.get(lblPath.getText());
        TmmUIHelper.openFolder(path);
      }
    });

    // UI binding
    PropertyChangeListener propertyChangeListener = propertyChangeEvent -> {
      String property = propertyChangeEvent.getPropertyName();
      Object source = propertyChangeEvent.getSource();

      if (source.getClass() != TvShowSelectionModel.class) {
        return;
      }

      TvShowSelectionModel selectionModel = (TvShowSelectionModel) source;
      TvShow tvShow = selectionModel.getSelectedTvShow();

      if ("selectedTvShow".equals(property)) {
        changeTvShow(tvShow);
      }
    };

    tvShowSelectionModel.addPropertyChangeListener(propertyChangeListener);
  }

  private void changeTvShow(TvShow tvShow) {
    lblTvShowName.setText(tvShow.getTitle());
    lblTvShowName.setIcon(tvShow.isLocked() ? IconManager.LOCK_BLUE : null);
    lblOriginalTitle.setText(tvShow.getOriginalTitle());
    lblYear.setText(getIntegerAsStringWoZero(tvShow.getYear()));
    lblPremiered.setText(tvShow.getFirstAiredAsString());
    lblCertification.setText(tvShow.getCertification().getLocalizedName());
    lblRuntime.setText(convertRuntime(tvShow.getRuntime()));
    taGenres.setText(tvShow.getGenresAsString());
    lblStatus.setText(tvShow.getStatus().getLocalizedName());
    taStudio.setText(tvShow.getProductionCompany());
    lblCountry.setText(tvShow.getCountry());
    lblEpisodeGroup.setText(tvShow.getEpisodeGroup().toString());
    lblTagline.setText(tvShow.getTagline());
    taOverview.setText(tvShow.getPlot());
    taTags.setText(tvShow.getTagsAsString());
    lblPath.setText(tvShow.getPath());
    taNote.setText(tvShow.getNote());

    lblCertificationLogo.setIcon(getCertificationIcon(tvShow.getCertification()));
    lblImdbId.setText(tvShow.getImdbId());
    lblThetvdbId.setText(tvShow.getTvdbId());

    // other IDs
    panelOtherIds.removeAll();
    for (String key : tvShow.getIds().keySet()) {
      // we want at max the TMDB id here, since we're collecting too many ids now...
      if (MediaMetadata.TMDB.equals(key)) {
        panelOtherIds.add(new IdLinkPanel(key, tvShow));
      }
      else if (MediaMetadata.SIMKL.equals(key)) {
        panelOtherIds.add(new IdLinkPanel(key, tvShow));
      }
    }
    panelOtherIds.invalidate();
    panelOtherIds.repaint();

    setArtwork(tvShow, MediaFileType.POSTER);
    setArtwork(tvShow, MediaFileType.FANART);
    setArtwork(tvShow, MediaFileType.BANNER);
    setArtwork(tvShow, MediaFileType.THUMB);
    setArtwork(tvShow, MediaFileType.CLEARLOGO);

    panelLogos.setMediaInformationSource(tvShow);

    setRating(tvShow);

    // scroll everything up
    SwingUtilities.invokeLater(() -> {
      scrollPane.getVerticalScrollBar().setValue(0);
      scrollPane.getHorizontalScrollBar().setValue(0);
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("", LAYOUT_ARTWORK_VISIBLE, "[][grow]"));
    {
      JPanel panelLeft = new JPanel();
      panelLeft.setLayout(new ColumnLayout());
      add(panelLeft, "cell 0 0 1 2,grow");

      for (Component component : generateArtworkComponents(MediaFileType.POSTER)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.FANART)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.BANNER)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.THUMB)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.CLEARLOGO)) {
        panelLeft.add(component);
      }
    }
    {
      JPanel panelTitle = new JPanel();
      add(panelTitle, "cell 1 0,grow");
      panelTitle.setLayout(new MigLayout("insets 0 0 n n", "[grow]", "[][][shrink 0]"));
      {
        lblTvShowName = new TmmLabel("", 1.33);
        panelTitle.add(lblTvShowName, "cell 0 0,growx,wmin 0");
      }
      {
        lblOriginalTitle = new JLabel("");
        panelTitle.add(lblOriginalTitle, "cell 0 1,growx,wmin 0");
      }
      {
        panelTitle.add(new JSeparator(), "cell 0 2,growx");
      }
    }
    {
      JPanel panelRight = new JPanel();
      panelRight
          .setLayout(new MigLayout("insets n 0 n n, hidemode 2", "[100lp,grow]", "[][shrink 0][][shrink 0][][shrink 0][][]15lp[][grow,top][][][][]"));

      scrollPane = new NoBorderScrollPane(panelRight);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.getVerticalScrollBar().setUnitIncrement(8);
      add(scrollPane, "cell 1 1,grow, wmin 0");
      {
        JPanel panelTopDetails = new JPanel();
        panelRight.add(panelTopDetails, "cell 0 0,growx");
        panelTopDetails.setLayout(new MigLayout("insets 0", "[][][40lp!][][grow][]", "[]2lp[]2lp[]2lp[]2lp[]2lp[]2lp[]2lp[]"));
        {
          JLabel lblYearT = new TmmLabel(TmmResourceBundle.getString("metatag.year"));
          panelTopDetails.add(lblYearT, "flowy,cell 0 0");

          lblYear = new JLabel("");
          panelTopDetails.add(lblYear, "cell 1 0");
        }
        {
          JLabel lblImdbIdT = new TmmLabel("IMDb:");
          panelTopDetails.add(lblImdbIdT, "cell 3 0");

          lblImdbId = new LinkLabel("");
          panelTopDetails.add(lblImdbId, "cell 3 0");
        }
        {
          lblCertificationLogo = new JLabel("");
          panelTopDetails.add(lblCertificationLogo, "cell 5 0 1 3, top");
        }
        {
          JLabel lblPremieredT = new TmmLabel(TmmResourceBundle.getString("metatag.premiered"));
          panelTopDetails.add(lblPremieredT, "cell 0 1");

          lblPremiered = new JLabel("");
          panelTopDetails.add(lblPremiered, "cell 1 1");
        }
        {
          JLabel lblThetvdbIdT = new TmmLabel("TheTVDB:");
          panelTopDetails.add(lblThetvdbIdT, "cell 3 1");

          lblThetvdbId = new LinkLabel("");
          panelTopDetails.add(lblThetvdbId, "cell 3 1");
        }
        {
          JLabel lblCertificationT = new TmmLabel(TmmResourceBundle.getString("metatag.certification"));
          panelTopDetails.add(lblCertificationT, "cell 0 2");

          lblCertification = new JLabel("");
          panelTopDetails.add(lblCertification, "cell 1 2");
        }
        {
          panelOtherIds = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 0));
          panelTopDetails.add(panelOtherIds, "cell 3 2 3 2,growx,top,wmin 0");
        }
        {
          JLabel lblRuntimeT = new TmmLabel(TmmResourceBundle.getString("metatag.runtime"));
          panelTopDetails.add(lblRuntimeT, "cell 0 3,aligny top");

          lblRuntime = new JLabel("");
          panelTopDetails.add(lblRuntime, "cell 1 3,aligny top");
        }
        {
          JLabel lblGenresT = new TmmLabel(TmmResourceBundle.getString("metatag.genre"));
          panelTopDetails.add(lblGenresT, "cell 0 4");

          taGenres = new ReadOnlyTextPane();
          panelTopDetails.add(taGenres, "cell 1 4 5 1,growx,wmin 0");
        }
        {
          JLabel lblStatusT = new TmmLabel(TmmResourceBundle.getString("metatag.status"));
          panelTopDetails.add(lblStatusT, "cell 0 5");

          lblStatus = new JLabel("");
          panelTopDetails.add(lblStatus, "cell 1 5 4 1");
        }
        {
          JLabel lblStudioT = new TmmLabel(TmmResourceBundle.getString("metatag.studio"));
          panelTopDetails.add(lblStudioT, "cell 0 6,wmin 0");

          taStudio = new ReadOnlyTextPane();
          panelTopDetails.add(taStudio, "cell 1 6 5 1,growx, wmin 0");
        }
        {
          JLabel lblCountryT = new TmmLabel(TmmResourceBundle.getString("metatag.country"));
          panelTopDetails.add(lblCountryT, "cell 0 7");

          lblCountry = new JLabel("");
          panelTopDetails.add(lblCountry, "cell 1 7 5 1, wmin 0");
        }
      }
      {
        panelRight.add(new JSeparator(), "cell 0 1,growx");
      }
      {
        ratingPanel = new RatingPanel();
        panelRight.add(ratingPanel, "flowx,cell 0 2,aligny center");
      }
      {
        JSeparator sepLogos = new JSeparator();
        panelRight.add(sepLogos, "cell 0 3,growx");
      }
      {
        panelLogos = new MediaInformationLogosPanel();
        panelRight.add(panelLogos, "cell 0 4,growx");
      }
      {
        panelRight.add(new JSeparator(), "cell 0 5,growx");
      }
      {
        JLabel lblTaglineT = new TmmLabel(TmmResourceBundle.getString("metatag.tagline"));
        panelRight.add(lblTaglineT, "cell 0 6,alignx left,aligny top");

        lblTagline = new JLabel();
        panelRight.add(lblTagline, "cell 0 7,growx,wmin 0,aligny top");
      }
      {
        JLabel lblPlot = new TmmLabel(TmmResourceBundle.getString("metatag.plot"));
        panelRight.add(lblPlot, "cell 0 8");
        TmmFontHelper.changeFont(lblPlot, Font.BOLD);

        taOverview = new ReadOnlyTextPaneHTML();
        panelRight.add(taOverview, "cell 0 9,growx,wmin 0,aligny top");
      }
      {
        panelRight.add(new JSeparator(), "cell 0 10,growx");
      }
      {
        JPanel panelBottomDetails = new JPanel();
        panelBottomDetails.setLayout(new MigLayout("insets 0", "[][grow]", "[]2lp[]2lp[]2lp[]"));
        panelRight.add(panelBottomDetails, "cell 0 11,grow");

        {
          JLabel lblEpisodegroupT = new TmmLabel(TmmResourceBundle.getString("metatag.episode.group"));
          panelBottomDetails.add(lblEpisodegroupT, "cell 0 0");

          lblEpisodeGroup = new JLabel();
          panelBottomDetails.add(lblEpisodeGroup, "cell 1 0,growx,wmin 0");
        }
        {
          JLabel lblTagsT = new TmmLabel(TmmResourceBundle.getString("metatag.tags"));
          panelBottomDetails.add(lblTagsT, "cell 0 1");

          taTags = new ReadOnlyTextPane();
          panelBottomDetails.add(taTags, "cell 1 1,growx,wmin 0");
        }
        {
          JLabel lblPathT = new TmmLabel(TmmResourceBundle.getString("metatag.path"));
          panelBottomDetails.add(lblPathT, "cell 0 2");

          lblPath = new LinkTextArea("");
          panelBottomDetails.add(lblPath, "cell 1 2,growx,wmin 0");
        }
        {
          JLabel lblNoteT = new TmmLabel(TmmResourceBundle.getString("metatag.note"));
          panelBottomDetails.add(lblNoteT, "cell 0 3");

          taNote = new ReadOnlyTextPaneHTML();
          panelBottomDetails.add(taNote, "cell 1 3,growx,wmin 0");
        }
      }
    }
  }

  @Override
  protected List<MediaFileType> getShowArtworkFromSettings() {
    return TvShowModuleManager.getInstance().getSettings().getShowTvShowArtworkTypes();
  }

  @Override
  protected void setColumnLayout(boolean artworkVisible) {
    if (artworkVisible) {
      ((MigLayout) getLayout()).setColumnConstraints(LAYOUT_ARTWORK_VISIBLE);
    }
    else {
      ((MigLayout) getLayout()).setColumnConstraints(LAYOUT_ARTWORK_HIDDEN);
    }
  }

  private void setRating(TvShow tvShow) {
    Map<String, MediaRating> ratings = new HashMap<>(tvShow.getRatings());
    MediaRating customRating = tvShow.getRating();
    if (customRating != MediaMetadata.EMPTY_RATING) {
      ratings.put("custom", customRating);
    }

    ratingPanel.setRatings(ratings);
  }
}

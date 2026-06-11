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
package org.tinymediamanager.ui.tvshows.panels.episode;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JButton;
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
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.ui.ColumnLayout;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.WrapLayout;
import org.tinymediamanager.ui.components.NoBorderScrollPane;
import org.tinymediamanager.ui.components.button.UpnpPlayButton;
import org.tinymediamanager.ui.components.label.ImageLabel;
import org.tinymediamanager.ui.components.label.LinkLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.IdLinkPanel;
import org.tinymediamanager.ui.components.textfield.LinkTextArea;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPane;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPaneHTML;
import org.tinymediamanager.ui.panels.InformationPanel;
import org.tinymediamanager.ui.panels.MediaInformationLogosPanel;
import org.tinymediamanager.ui.panels.RatingPanel;
import org.tinymediamanager.ui.tvshows.TvShowEpisodeSelectionModel;

import net.miginfocom.swing.MigLayout;

/**
 * The Class TvShowEpisodeInformationPanel.
 *
 * @author Manuel Laggner
 */
public class TvShowEpisodeInformationPanel extends InformationPanel {
  private static final Logger               LOGGER                 = LoggerFactory.getLogger(TvShowEpisodeInformationPanel.class);

  private static final String               LAYOUT_ARTWORK_VISIBLE = "[n:100lp:20%, grow][300lp:300lp,grow 350]";
  private static final String               LAYOUT_ARTWORK_HIDDEN  = "[][300lp:300lp,grow 350]";

  private final TvShowSettings              settings               = TvShowModuleManager.getInstance().getSettings();
  private final TvShowEpisodeSelectionModel tvShowEpisodeSelectionModel;

  /** UI components */
  private JLabel                            lblTvShowName;
  private JLabel                            lblEpisodeTitle;
  private JTextPane                         taOverview;
  private MediaInformationLogosPanel        panelLogos;
  private JSeparator                        sepLogos;
  private JLabel                            lblOriginalTitle;
  private JButton                           btnPlay;
  private JScrollPane                       scrollPane;
  private JLabel                            lblSeason;
  private JLabel                            lblEpisode;
  private JLabel                            lblAired;
  private JTextPane                         taTags;
  private LinkTextArea                      lblPath;
  private JTextPane                         taNote;
  private LinkLabel                         lblTvdbId;
  private LinkLabel                         lblImdbId;
  private JPanel                            panelOtherIds;
  private RatingPanel                       ratingPanel;
  private JLabel                            lblEdition;

  /**
   * Instantiates a new tv show information panel.
   *
   * @param tvShowEpisodeSelectionModel
   *          the tv show selection model
   */
  public TvShowEpisodeInformationPanel(TvShowEpisodeSelectionModel tvShowEpisodeSelectionModel) {
    this.tvShowEpisodeSelectionModel = tvShowEpisodeSelectionModel;

    initComponents();

    // UI binding
    PropertyChangeListener propertyChangeListener = propertyChangeEvent -> {
      String property = propertyChangeEvent.getPropertyName();
      Object source = propertyChangeEvent.getSource();

      if (source.getClass() != TvShowEpisodeSelectionModel.class) {
        return;
      }

      TvShowEpisodeSelectionModel selectionModel = (TvShowEpisodeSelectionModel) source;
      TvShowEpisode episode = selectionModel.getSelectedTvShowEpisode();

      if ("selectedTvShowEpisode".equals(property)) {
        changeEpisode(episode);
      }
    };

    tvShowEpisodeSelectionModel.addPropertyChangeListener(propertyChangeListener);

    // action listeners
    lblPath.addActionListener(arg0 -> {
      if (!StringUtils.isEmpty(lblPath.getText())) {
        // get the location from the label
        Path path = Paths.get(lblPath.getText());
        TmmUIHelper.openFolder(path);
      }
    });

    // Imdb
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

    // TheTvDB
    lblTvdbId.addActionListener(arg0 -> {
      String showId = tvShowEpisodeSelectionModel.getSelectedTvShowEpisode().getTvShow().getTvdbId();
      String url = "https://thetvdb.com/?tab=series&id=" + showId + "&tab=episode&id=" + lblTvdbId.getText();
      try {
        TmmUIHelper.browseUrl(url);
      }
      catch (Exception e) {
        LOGGER.error("Could not open '{}' in browser - '{}'", url, e.getMessage());
        MessageManager.getInstance()
            .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() }));
      }
    });
  }

  private void changeEpisode(TvShowEpisode episode) {
    lblTvShowName.setText(episode.getTvShow().getTitle());
    lblTvShowName.setIcon(episode.getTvShow().isLocked() ? IconManager.LOCK_BLUE : null);
    lblEpisodeTitle.setText(episode.getTitleForUi());
    lblOriginalTitle.setText(episode.getOriginalTitle());
    lblSeason.setText(getIntegerAsStringWoZero(episode.getSeason()));
    lblEpisode.setText(getIntegerAsStringWoZero(episode.getEpisode()));
    lblImdbId.setText(episode.getImdbId());
    lblTvdbId.setText(episode.getTvdbId());
    taOverview.setText(episode.getPlot());
    lblAired.setText(episode.getFirstAiredAsString());
    taTags.setText(episode.getTagsAsString());
    lblPath.setText(episode.getPath());
    taNote.setText(episode.getNote());
    lblEdition.setText(episode.getEdition().getTitle());

    // Set logos
    panelLogos.setMediaInformationSource(episode);

    // Set rating
    setRating(episode);

    // Set season poster
    setSeasonPoster(episode);

    // Set thumb artwork
    setArtwork(episode, MediaFileType.THUMB);

    // other IDs
    panelOtherIds.removeAll();
    for (String key : episode.getIds().keySet()) {
      // all but IMDB and TVDB
      if (MediaMetadata.IMDB.equals(key) || MediaMetadata.TVDB.equals(key)) {
        continue;
      }

      panelOtherIds.add(new IdLinkPanel(key, episode));
    }
    panelOtherIds.invalidate();
    panelOtherIds.repaint();

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

      for (Component component : generateArtworkComponents(MediaFileType.SEASON_POSTER)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.THUMB)) {
        panelLeft.add(component);
      }
    }
    {
      JPanel panelTitle = new JPanel();
      add(panelTitle, "cell 1 0,growx");
      panelTitle.setLayout(new MigLayout("insets 0 0 n n", "[grow][]", "[][][][shrink 0]"));

      {
        lblTvShowName = new TmmLabel("", 1.33);
        panelTitle.add(lblTvShowName, "flowx,cell 0 0,growx,wmin 0");
      }
      {
        btnPlay = new UpnpPlayButton() {
          @Override
          public MediaFile getMediaFile() {
            return tvShowEpisodeSelectionModel.getSelectedTvShowEpisode().getMainFile();
          }

          @Override
          public MediaEntity getMediaEntity() {
            return tvShowEpisodeSelectionModel.getSelectedTvShowEpisode();
          }
        };
        panelTitle.add(btnPlay, "cell 1 0 1 4,aligny top");
      }
      {
        lblEpisodeTitle = new TmmLabel("", 1.16);
        panelTitle.add(lblEpisodeTitle, "cell 0 1,growx,wmin 0");
      }
      {
        lblOriginalTitle = new JLabel("");
        panelTitle.add(lblOriginalTitle, "cell 0 2,growx,wmin 0");
      }
      {
        panelTitle.add(new JSeparator(), "cell 0 3 2 1,growx");
      }
    }
    {
      JPanel panelRight = new JPanel();
      panelRight.setLayout(new MigLayout("insets 0 0 n n, hidemode 2", "[100lp,grow]", "[][shrink 0][][shrink 0][][shrink 0][][][][]"));

      scrollPane = new NoBorderScrollPane(panelRight);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.getVerticalScrollBar().setUnitIncrement(8);
      add(scrollPane, "cell 1 1,grow, wmin 0");
      {
        JPanel panelTopDetails = new JPanel();
        panelTopDetails.setLayout(new MigLayout("insets 0", "[][][40lp!][][grow]", "[]2lp[][][][]"));

        panelRight.add(panelTopDetails, "cell 0 0,grow");
        {
          JLabel lblSeasonT = new JLabel(TmmResourceBundle.getString("metatag.season"));
          TmmFontHelper.changeFont(lblSeasonT, 1.166, Font.BOLD);
          panelTopDetails.add(lblSeasonT, "cell 0 0");

          lblSeason = new JLabel("");
          TmmFontHelper.changeFont(lblSeason, 1.166);
          panelTopDetails.add(lblSeason, "cell 1 0");
        }
        {
          JLabel lblEpisodeT = new JLabel(TmmResourceBundle.getString("metatag.episode"));
          TmmFontHelper.changeFont(lblEpisodeT, 1.166, Font.BOLD);
          panelTopDetails.add(lblEpisodeT, "cell 0 1");

          lblEpisode = new JLabel("");
          TmmFontHelper.changeFont(lblEpisode, 1.166);
          panelTopDetails.add(lblEpisode, "cell 1 1");
        }
        JLabel lblAiredT = new TmmLabel(TmmResourceBundle.getString("metatag.aired"));
        panelTopDetails.add(lblAiredT, "cell 0 2");
        {

          lblAired = new JLabel("");
          panelTopDetails.add(lblAired, "cell 1 2");
        }
        {
          JLabel lblImdbIdT = new TmmLabel("IMDb:");
          panelTopDetails.add(lblImdbIdT, "cell 3 0");

          lblImdbId = new LinkLabel();
          panelTopDetails.add(lblImdbId, "cell 3 0");
        }
        {
          JLabel lblTvdbIdT = new TmmLabel("TheTVDB:");
          panelTopDetails.add(lblTvdbIdT, "cell 3 1");

          lblTvdbId = new LinkLabel();
          panelTopDetails.add(lblTvdbId, "cell 3 1");
        }
        {
          panelOtherIds = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 0));
          panelTopDetails.add(panelOtherIds, "cell 3 2 3 2,growx,top,wmin 0");
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
        sepLogos = new JSeparator();
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
        JLabel lblPlot = new TmmLabel(TmmResourceBundle.getString("metatag.plot"));
        panelRight.add(lblPlot, "cell 0 6");

        taOverview = new ReadOnlyTextPaneHTML();
        panelRight.add(taOverview, "cell 0 7,growx,wmin 0,aligny top");
      }
      {
        panelRight.add(new JSeparator(), "cell 0 8,growx");
      }
      {
        JPanel panelBottomDetails = new JPanel();
        panelRight.add(panelBottomDetails, "cell 0 9,grow");
        panelBottomDetails.setLayout(new MigLayout("insets 0", "[][10lp][200lp,grow]", "[]2lp[]2lp[]2lp[]"));

        JLabel lblEditionT = new TmmLabel(TmmResourceBundle.getString("metatag.edition"));
        panelBottomDetails.add(lblEditionT, "cell 0 0");

        lblEdition = new JLabel("");
        panelBottomDetails.add(lblEdition, "cell 2 0");
        {
          {
            JLabel lblTagsT = new TmmLabel(TmmResourceBundle.getString("metatag.tags"));
            panelBottomDetails.add(lblTagsT, "cell 0 1");

            taTags = new ReadOnlyTextPane();
            panelBottomDetails.add(taTags, "cell 2 1,growx,wmin 0");
          }
          {
            JLabel lblPathT = new TmmLabel(TmmResourceBundle.getString("metatag.path"));
            panelBottomDetails.add(lblPathT, "cell 0 2");

            lblPath = new LinkTextArea("");
            panelBottomDetails.add(lblPath, "cell 2 2,growx,wmin 0");
          }
          {
            JLabel lblNoteT = new TmmLabel(TmmResourceBundle.getString("metatag.note"));
            panelBottomDetails.add(lblNoteT, "cell 0 3");

            taNote = new ReadOnlyTextPaneHTML();
            panelBottomDetails.add(taNote, "cell 2 3,growx,wmin 0");
          }
        }
      }
    }
  }

  private void setSeasonPoster(TvShowEpisode episode) {
    if (episode == null || episode.getTvShowSeason() == null) {
      // eg. happens when you highlight a season/episode node, but remove the show on right-click
      return;
    }
    String posterPath = episode.getTvShowSeason().getArtworkFilename(MediaFileType.SEASON_POSTER);
    Dimension posterSize = episode.getTvShowSeason().getArtworkDimension(MediaFileType.SEASON_POSTER);

    if (StringUtils.isBlank(posterPath) && settings.isSeasonArtworkFallback()) {
      // fall back to the show
      posterPath = episode.getTvShowSeason().getTvShow().getArtworkFilename(MediaFileType.POSTER);
      posterSize = episode.getTvShowSeason().getTvShow().getArtworkDimension(MediaFileType.POSTER);
    }

    setArtwork(MediaFileType.SEASON_POSTER, posterPath, posterSize);
  }

  private void setArtwork(MediaFileType type, String artworkPath, Dimension artworkDimension) {
    List<Component> components = artworkComponents.get(type);
    if (ListUtils.isEmpty(components)) {
      return;
    }

    boolean visible = getShowArtworkFromSettings().contains(type);

    for (Component component : components) {
      component.setVisible(visible);

      if (component instanceof ImageLabel imageLabel) {
        imageLabel.clearImage();
        imageLabel.setImagePath(artworkPath);
      }
      else if (component instanceof JLabel sizeLabel) {
        if (artworkDimension.width > 0 && artworkDimension.height > 0) {
          sizeLabel.setText(TmmResourceBundle.getString("mediafiletype." + type.name().toLowerCase(Locale.ROOT)) + " - " + artworkDimension.width
              + "x" + artworkDimension.height);
        }
        else {
          sizeLabel.setText(TmmResourceBundle.getString("mediafiletype." + type.name().toLowerCase(Locale.ROOT)));
        }
      }
    }

    updateArtwork();
  }

  @Override
  protected List<MediaFileType> getShowArtworkFromSettings() {
    return settings.getShowEpisodeArtworkTypes();
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

  private void setRating(TvShowEpisode episode) {
    Map<String, MediaRating> ratings = new HashMap<>(episode.getRatings());
    MediaRating customRating = episode.getRating();
    if (customRating != MediaMetadata.EMPTY_RATING) {
      ratings.put("custom", customRating);
    }

    ratingPanel.setRatings(ratings);
  }
}

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
package org.tinymediamanager.ui.tvshows.panels.season;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.ui.ColumnLayout;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.TmmUILayoutStore;
import org.tinymediamanager.ui.components.NoBorderScrollPane;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.components.table.TmmTableFormat;
import org.tinymediamanager.ui.components.table.TmmTableModel;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPane;
import org.tinymediamanager.ui.panels.InformationPanel;
import org.tinymediamanager.ui.tvshows.TvShowSeasonSelectionModel;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.ObservableElementList;
import ca.odell.glazedlists.swing.GlazedListsSwing;
import net.miginfocom.swing.MigLayout;

/**
 * The Class TvShowSeasonInformationPanel.
 * 
 * @author Manuel Laggner
 */
public class TvShowSeasonInformationPanel extends InformationPanel {
  private static final String                LAYOUT_ARTWORK_VISIBLE = "[n:100lp:20%, grow][300lp:300lp,grow 350]";
  private static final String                LAYOUT_ARTWORK_HIDDEN  = "[][300lp:300lp,grow 350]";

  private Color                              defaultColor;
  private Color                              dummyColor;

  private final EventList<TvShowEpisode>     episodeEventList;
  private final TmmTableModel<TvShowEpisode> episodeTableModel;
  private JLabel                             lblTvshowTitle;
  private JLabel                             lblSeason;
  private JTextPane                          taOverview;
  private TmmTable                           tableEpisodes;

  /**
   * Instantiates a new tv show information panel.
   * 
   * @param tvShowSeasonSelectionModel
   *          the tv show selection model
   */
  public TvShowSeasonInformationPanel(TvShowSeasonSelectionModel tvShowSeasonSelectionModel) {
    episodeEventList = new ObservableElementList<>(GlazedLists.threadSafeList(new BasicEventList<>()),
        GlazedLists.beanConnector(TvShowEpisode.class));
    episodeTableModel = new TmmTableModel<>(GlazedListsSwing.swingThreadProxyList(episodeEventList), new EpisodeTableFormat());

    initComponents();

    // UI binding
    PropertyChangeListener propertyChangeListener = propertyChangeEvent -> {
      String property = propertyChangeEvent.getPropertyName();
      Object source = propertyChangeEvent.getSource();

      if (source.getClass() != TvShowSeasonSelectionModel.class) {
        return;
      }

      TvShowSeasonSelectionModel selectionModel = (TvShowSeasonSelectionModel) source;
      TvShowSeason season = selectionModel.getSelectedTvShowSeason();

      if ("selectedTvShowSeason".equals(property)) {
        changeSeason(season);
      }
    };

    tvShowSeasonSelectionModel.addPropertyChangeListener(propertyChangeListener);

    tableEpisodes.setDefaultRenderer(String.class, new EpisodeTableCellRenderer());
  }

  @Override
  public void updateUI() {
    super.updateUI();

    defaultColor = UIManager.getColor("Table.foreground");
    dummyColor = UIManager.getColor("Component.linkColor");
  }

  private void changeSeason(TvShowSeason season) {
    if (StringUtils.isNotBlank(season.getTitle())) {
      lblSeason.setText(season.getTitle() + " (" + TmmResourceBundle.getString("metatag.season") + " " + season.getSeason() + ")");
    }
    else {
      lblSeason.setText(TmmResourceBundle.getString("metatag.season") + " " + season.getSeason());
    }

    // Season poster
    setSeasonArtwork(season, MediaFileType.SEASON_POSTER, MediaFileType.POSTER);

    // Season fanart
    setSeasonArtwork(season, MediaFileType.SEASON_FANART, MediaFileType.FANART);

    // Season banner
    setSeasonArtwork(season, MediaFileType.SEASON_BANNER, MediaFileType.BANNER);

    // Season thumb
    setSeasonArtwork(season, MediaFileType.SEASON_THUMB, MediaFileType.THUMB);

    // Update episode table
    try {
      episodeEventList.getReadWriteLock().writeLock().lock();
      episodeEventList.clear();
      episodeEventList.addAll(season.getEpisodesForDisplay());
    }
    catch (Exception ignored) {
      // nothing to do here
    }
    finally {
      episodeEventList.getReadWriteLock().writeLock().unlock();
      tableEpisodes.adjustColumnPreferredWidths(6);
    }
  }

  private void setSeasonArtwork(TvShowSeason season, MediaFileType type, MediaFileType fallbackType) {
    MediaFile mediaFile = ListUtils.getFirst(season.getMediaFiles(type));

    if (mediaFile == null && TvShowModuleManager.getInstance().getSettings().isSeasonArtworkFallback()) {
      // fall back to TV show
      mediaFile = ListUtils.getFirst(season.getTvShow().getMediaFiles(fallbackType));
    }

    setArtwork(mediaFile, type);
  }

  private void initComponents() {
    setLayout(new MigLayout("", LAYOUT_ARTWORK_VISIBLE, "[300lp,grow]"));

    {
      JPanel panelLeft = new JPanel();
      add(panelLeft, "cell 0 0,grow");
      panelLeft.setLayout(new ColumnLayout());
      for (Component component : generateArtworkComponents(MediaFileType.SEASON_POSTER)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.SEASON_FANART)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.SEASON_BANNER)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.SEASON_THUMB)) {
        panelLeft.add(component);
      }
    }
    {
      JPanel panelRight = new JPanel();
      add(panelRight, "cell 1 0,grow");
      panelRight.setLayout(new MigLayout("insets 0 n n n, hidemode 2", "[100lp,grow]", "[][][shrink 0][][75lp:n][shrink 0][][25%:50%,grow]"));
      {
        lblTvshowTitle = new TmmLabel("", 1.33);
        panelRight.add(lblTvshowTitle, "cell 0 0");
      }
      {
        lblSeason = new JLabel("");
        panelRight.add(lblSeason, "cell 0 1");
        TmmFontHelper.changeFont(lblSeason, 1.166, Font.BOLD);
      }
      {
        panelRight.add(new JSeparator(), "cell 0 2,growx");
      }
      {
        JLabel lblPlotT = new TmmLabel(TmmResourceBundle.getString("metatag.plot"));
        panelRight.add(lblPlotT, "cell 0 3");

        JScrollPane scrollPane = new NoBorderScrollPane();
        taOverview = new ReadOnlyTextPane();
        scrollPane.setViewportView(taOverview);
        panelRight.add(scrollPane, "cell 0 4,growx,wmin 0,aligny top");
      }
      {
        panelRight.add(new JSeparator(), "cell 0 5,growx");
      }
      {
        JLabel lblEpisodelistT = new TmmLabel(TmmResourceBundle.getString("metatag.episodes"));
        panelRight.add(lblEpisodelistT, "cell 0 6");

        tableEpisodes = new TmmTable(episodeTableModel);
        tableEpisodes.setName("tvshows.seaon.episodeTable");
        TmmUILayoutStore.getInstance().install(tableEpisodes);
        JScrollPane scrollPaneEpisodes = new JScrollPane();
        tableEpisodes.configureScrollPane(scrollPaneEpisodes);
        panelRight.add(scrollPaneEpisodes, "cell 0 7,grow");
        scrollPaneEpisodes.setViewportView(tableEpisodes);
      }
    }
  }

  @Override
  protected List<MediaFileType> getShowArtworkFromSettings() {
    return TvShowModuleManager.getInstance().getSettings().getShowSeasonArtworkTypes();
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

  private static class EpisodeTableFormat extends TmmTableFormat<TvShowEpisode> {

    public EpisodeTableFormat() {
      /*
       * episode number
       */
      Column col = new Column(TmmResourceBundle.getString("metatag.episode"), "episode", TvShowEpisode::getEpisode, String.class);
      col.setColumnResizeable(false);
      addColumn(col);

      /*
       * episode title
       */
      col = new Column(TmmResourceBundle.getString("metatag.title"), "title", TvShowEpisode::getTitle, String.class);
      col.setCellTooltip(TvShowEpisode::getTitle);
      addColumn(col);

      /*
       * aired date
       */
      col = new Column(TmmResourceBundle.getString("metatag.aired"), "aired", TvShowEpisode::getFirstAiredAsString, String.class);
      col.setColumnResizeable(false);
      addColumn(col);
    }
  }

  private class EpisodeTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

      Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      // Check if the episode is a dummy one
      int dataRow = table.convertRowIndexToModel(row);
      if (episodeEventList.get(dataRow).isDummy()) {
        c.setForeground(dummyColor);
      }
      else {
        c.setForeground(defaultColor);
      }
      return c;
    }
  }
}

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
package org.tinymediamanager.ui.tvshows;

import java.awt.Color;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.components.treetable.TmmTreeTableCellRenderer;
import org.tinymediamanager.ui.images.MultiIcon;

/**
 * The class TvShowTreeCellRenderer. Just for modifying the color of dummy seasons/episodes
 * 
 * @author Manuel Laggner
 */
public class TvShowTreeCellRenderer extends TmmTreeTableCellRenderer {
  private Color colorDummy;

  @Override
  public void updateUI() {
    super.updateUI();
    colorDummy = UIManager.getColor("Component.linkColor");
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    DefaultTableCellRenderer renderer = (DefaultTableCellRenderer) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
        column);

    if (value instanceof TvShowTreeDataProvider.TvShowTreeNode node) {
      if (node.getUserObject() instanceof TvShow tvShow) {
        Icon lockIcon = tvShow.isLocked() ? IconManager.LOCK_BLUE : null;
        boolean showNewIcon = TvShowModuleManager.getInstance().getSettings().isShowNewTvShowIcon();

        Icon newIcon = (showNewIcon && tvShow.isNewlyAdded()) ? IconManager.NEW_GREEN2 : null;
        setIcon(MultiIcon.of(newIcon, lockIcon));
      }
    }

    if (value instanceof TvShowTreeDataProvider.TvShowSeasonTreeNode node) {
      TvShowSeason season = (TvShowSeason) node.getUserObject();

      boolean showNewIcon = TvShowModuleManager.getInstance().getSettings().isShowNewTvShowIcon();
      Icon newIcon = (showNewIcon && season.isNewlyAdded()) ? IconManager.NEW_GREEN2 : null;
      setIcon(newIcon);

      if (season.isDummy()) {
        renderer.setForeground(colorDummy);
      }
    }
    else if (value instanceof TvShowTreeDataProvider.TvShowEpisodeTreeNode node) {
      TvShowEpisode episode = (TvShowEpisode) node.getUserObject();

      boolean showNewIcon = TvShowModuleManager.getInstance().getSettings().isShowNewTvShowIcon();
      Icon newIcon = (showNewIcon && episode.isNewlyAdded()) ? IconManager.NEW_GREEN2 : null;
      setIcon(newIcon);

      if (episode.isDummy()) {
        renderer.setForeground(colorDummy);
      }
    }

    return renderer;
  }

  @Override
  public String getToolTipText() {
    if (!TvShowModuleManager.getInstance().getSettings().isShowTvShowTableTooltips()) {
      return null;
    }

    return super.getToolTipText();
  }
}

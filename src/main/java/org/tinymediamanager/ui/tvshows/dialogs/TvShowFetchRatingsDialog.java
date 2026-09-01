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
package org.tinymediamanager.ui.tvshows.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.scraper.rating.RatingProvider;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.components.combobox.TmmCheckComboBox;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.dialogs.TmmDialog;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link TvShowFetchRatingsDialog} is used to choose which sources should be used for rating fetching.
 * 
 * @author Manuel Laggner
 */
public class TvShowFetchRatingsDialog extends TmmDialog {
  private final TmmCheckComboBox<RatingProvider.RatingSource> cbSources;

  public TvShowFetchRatingsDialog() {
    super(TmmResourceBundle.getString("tvshow.fetchratings"), "tvShowFetchRatings");
    setMinimumSize(new Dimension(500, 300));

    JPanel panelContent = new JPanel();
    getContentPane().add(panelContent, BorderLayout.CENTER);
    panelContent.setLayout(new MigLayout("", "[][250lp, grow]", "[][50lp]"));

    JLabel lblRatingSource = new TmmLabel(TmmResourceBundle.getString("metatag.rating.source"));
    panelContent.add(lblRatingSource, "cell 0 0,alignx right");

    cbSources = new TmmCheckComboBox<>(RatingProvider.RatingSource.getRatingSourcesForTvShows());
    panelContent.add(cbSources, "cell 1 0,growx, wmin 0");

    {
      JButton cancelButton = new JButton(TmmResourceBundle.getString("Button.cancel"));
      cancelButton.setIcon(IconManager.CANCEL_INV);
      cancelButton.addActionListener(e -> {
        cbSources.clearSelection();
        setVisible(false);
      });
      addButton(cancelButton);

      JButton btnOk = new JButton(TmmResourceBundle.getString("Button.ok"));
      btnOk.setIcon(IconManager.APPLY_INV);
      btnOk.addActionListener(e -> {
        setVisible(false);
      });
      addButton(btnOk);
    }
  }

  public List<RatingProvider.RatingSource> getSelectedRatingSources() {
    return cbSources.getSelectedItems();
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible) {
      // init defaults
      loadDefaults();
    }

    // super
    super.setVisible(visible);
  }

  private void loadDefaults() {
    List<RatingProvider.RatingSource> selectedItems = TvShowModuleManager.getInstance().getSettings().getFetchRatingSources();
    cbSources.setSelectedItems(selectedItems);
  }
}

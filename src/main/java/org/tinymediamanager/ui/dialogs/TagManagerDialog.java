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
package org.tinymediamanager.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.FontMetrics;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.TagManager;
import org.tinymediamanager.core.TagManager.TagInfo;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TableColumnResizer;
import org.tinymediamanager.ui.components.table.TmmEditorTable;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.components.table.TmmTableFormat;
import org.tinymediamanager.ui.components.table.TmmTableModel;
import org.tinymediamanager.ui.components.textfield.EnhancedTextField;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.swing.GlazedListsSwing;
import net.miginfocom.swing.MigLayout;

/**
 * The class {@link TagManagerDialog} provides a centralized UI for managing tags across a collection of {@link MediaEntity} instances. It shows all
 * tags with usage counts, allows filtering, deleting, and renaming tags, and displays which entities use a selected tag.
 * <p>
 * This dialog is modular: it can be instantiated for movies, TV shows, or episodes by passing the appropriate {@link TagManager} instance.
 * </p>
 *
 * @author Manuel Laggner
 */
public class TagManagerDialog extends TmmDialog {
  private final TagManager            tagManager;

  private final SortedList<TagInfo>   tagEventList;
  private final TmmTable              tagTable;
  private final TmmTable              usageTable;
  private final EventList<EntityInfo> usageEventList;
  private final JTextField            filterField;
  private final JLabel                lblTagCount;

  /**
   * Creates a new {@link TagManagerDialog}.
   *
   * @param tagManager
   *          the tag manager instance (bound to movies, TV shows, or episodes)
   * @param titleSuffix
   *          a human-readable suffix for the dialog title (e.g. "Movies", "TV Shows", "Episodes")
   */
  public TagManagerDialog(TagManager tagManager, String titleSuffix) {
    super(TmmResourceBundle.getString("tagmanager.title") + " - " + titleSuffix, "tagManager");

    this.tagManager = tagManager;

    tagEventList = new SortedList<>(GlazedListsSwing.swingThreadProxyList(GlazedLists.threadSafeList(new BasicEventList<>())));
    usageEventList = GlazedListsSwing.swingThreadProxyList(GlazedLists.threadSafeList(new BasicEventList<>()));

    // --- filter panel ---
    JPanel filterPanel = new JPanel(new MigLayout("insets 8 8 0 8, gap 8", "[grow][]", "[]"));
    filterField = EnhancedTextField.createSearchTextField();
    filterPanel.add(filterField, "growx");

    lblTagCount = new JLabel("");
    filterPanel.add(lblTagCount, "span 2, growx");
    setTopInformationPanel(filterPanel);

    // --- main split pane ---
    JPanel contentPanel = new JPanel();
    contentPanel.setLayout(new MigLayout("insets 0 n n n", "[500lp:600lp,grow]", "[250lp:350lp,grow]"));
    getContentPane().add(contentPanel, BorderLayout.CENTER);

    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    splitPane.setResizeWeight(0.6);
    splitPane.setDividerLocation(350);

    // left: tag table
    {
      tagTable = new TmmEditorTable(new TmmTableModel<>(tagEventList, new TagTableFormat())) {
        @Override
        protected void editButtonClicked(int row) {
          int index = convertRowIndexToModel(row);
          TagInfo selectedTag = tagEventList.get(index);

          if (selectedTag != null && selectedTag.count() >= 0) {
            renameSelectedTag(selectedTag);
          }
        }

        @Override
        protected boolean isLinkCell(int row, int column) {
          return isEditorColumn(column) || isDeleteColumn(column);
        }

        private boolean isDeleteColumn(int column) {
          if (column < 0) {
            return false;
          }
          return "delete".equals(getColumnModel().getColumn(column).getIdentifier());
        }

        @Override
        protected void linkClicked(int row, int column, MouseEvent mouseEvent) {
          int index = convertRowIndexToModel(row);
          TagInfo selectedTag = tagEventList.get(index);

          if (selectedTag != null && selectedTag.count() >= 0) {
            deleteSelectedTag(selectedTag);
          }
        }
      };
      tagTable.installComparatorChooser(tagEventList);
      JScrollPane scrollPane = new JScrollPane();
      tagTable.configureScrollPane(scrollPane);
      splitPane.setLeftComponent(scrollPane);

      tagTable.getSelectionModel().addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
          updateUsageTable();
        }
      });
    }

    // right: usage table
    {
      usageTable = new TmmTable(new TmmTableModel<>(usageEventList, new UsageTableFormat()));
      JScrollPane scrollPane = new JScrollPane();
      usageTable.configureScrollPane(scrollPane);
      splitPane.setRightComponent(scrollPane);
    }

    contentPanel.add(splitPane, "cell 0 0, grow");

    // --- buttons ---
    JButton btnRefresh = new JButton(TmmResourceBundle.getString("tagmanager.refresh"));
    btnRefresh.setIcon(IconManager.REFRESH);
    btnRefresh.addActionListener(e -> refreshData());
    addButton(btnRefresh);

    JButton btnClose = new JButton(TmmResourceBundle.getString("Button.close"));
    btnClose.setIcon(IconManager.APPLY);
    btnClose.addActionListener(e -> setVisible(false));
    addDefaultButton(btnClose);

    // --- filter listener ---
    filterField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        applyFilter();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        applyFilter();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        applyFilter();
      }
    });

    // --- load data ---
    refreshData();

    setSize(800, 500);
  }

  /**
   * Reloads all tags from the {@link TagManager} and rebuilds the table.
   */
  private void refreshData() {
    List<TagInfo> allTags = tagManager.getAllTags();
    tagEventList.clear();

    if (allTags.isEmpty()) {
      tagEventList.add(new TagInfo(TmmResourceBundle.getString("tagmanager.notags"), -1));
      lblTagCount.setText("");
      return;
    }

    tagEventList.addAll(allTags);
    lblTagCount.setText(TmmResourceBundle.getString("tagmanager.tagcount") + " " + allTags.size());

    usageEventList.clear();

    SwingUtilities.invokeLater(() -> {
      TableColumnResizer.adjustColumnPreferredWidths(tagTable);
      if (tagTable.getRowCount() > 0) {
        tagTable.getSelectionModel().setSelectionInterval(0, 0);
      }
      updateUsageTable();
    });
  }

  /**
   * Applies the filter text to the tag table.
   */
  private void applyFilter() {
    String filter = filterField.getText().trim();

    List<TagInfo> allTags = tagManager.getAllTags();
    List<TagInfo> filtered;

    if (StringUtils.isBlank(filter)) {
      filtered = allTags;
    }
    else {
      String lowerFilter = filter.toLowerCase();
      filtered = allTags.stream().filter(ti -> ti.tag().toLowerCase().contains(lowerFilter)).collect(Collectors.toList());
    }

    tagEventList.clear();
    if (filtered.isEmpty()) {
      tagEventList.add(new TagInfo(TmmResourceBundle.getString("tagmanager.nomatch"), -1));
      lblTagCount.setText(TmmResourceBundle.getString("tagmanager.nomatchlong"));
    }
    else {
      tagEventList.addAll(filtered);
      lblTagCount.setText(TmmResourceBundle.getString("tagmanager.tagcount") + " " + filtered.size());
    }
  }

  /**
   * Updates the usage table based on the currently selected tag.
   */
  private void updateUsageTable() {
    int row = tagTable.getSelectedRow();
    usageEventList.clear();

    if (row < 0 || row >= tagEventList.size()) {
      return;
    }

    TagInfo selectedTag = tagEventList.get(row);
    if (selectedTag == null || selectedTag.count() == 0) {
      return;
    }

    List<MediaEntity> usages = tagManager.getUsages(selectedTag.tag());
    for (MediaEntity entity : usages) {
      String displayLabel = buildDisplayLabel(entity);
      int tagCount = entity.getTags().size();
      String videoFile = "";
      try {
        MediaFile mainFile = entity.getMainFile();
        if (mainFile != null) {
          videoFile = mainFile.getFilename();
        }
      }
      catch (Exception ignored) {
        // ignore
      }
      usageEventList.add(new EntityInfo(displayLabel, tagCount, videoFile));
    }

    TableColumnResizer.adjustColumnPreferredWidths(usageTable);
  }

  private String buildDisplayLabel(MediaEntity entity) {
    if (entity instanceof TvShowEpisode episode) {
      TvShow show = episode.getTvShow();
      String showTitle = show != null ? show.getTitle() : "?";
      int season = episode.getSeason();
      int ep = episode.getEpisode();
      return showTitle + " - S" + String.format("%02d", season) + "E" + String.format("%02d", ep) + " - " + entity.getTitle();
    }
    return entity.getTitle();
  }

  /**
   * Deletes the selecte tag from all entities.
   */
  private void deleteSelectedTag(TagInfo selectedTag) {
    // build confirmation message
    String msg = TmmResourceBundle.getString("tagmanager.deleteconfirm") + " \"" + selectedTag.tag() + "\" ("
        + TmmResourceBundle.getString("tagmanager.usages") + ": " + selectedTag.count() + ")?";

    int confirm = JOptionPane.showConfirmDialog(this, msg, TmmResourceBundle.getString("tagmanager.delete"), JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE);

    if (confirm != JOptionPane.YES_OPTION) {
      return;
    }

    tagManager.deleteTag(selectedTag.tag());
    refreshData();
  }

  /**
   * Renames the selected tag across all entities.
   */
  private void renameSelectedTag(TagInfo selectedTag) {

    String prompt = TmmResourceBundle.getString("tagmanager.renameprompt");
    String initialValue = selectedTag.tag();

    String newName = JOptionPane.showInputDialog(this, prompt, initialValue);

    if (StringUtils.isBlank(newName)) {
      return;
    }
    newName = newName.trim();

    tagManager.renameTag(selectedTag.tag(), newName);
    refreshData();
  }

  // ----- inner classes -----

  private static class TagTableFormat extends TmmTableFormat<TagInfo> {

    public TagTableFormat() {
      Comparator<String> stringComparator = new StringComparator();
      Comparator<Integer> integerComparator = new IntegerComparator();

      FontMetrics fontMetrics = getFontMetrics();

      // the tag itself
      Column col = new Column(TmmResourceBundle.getString("tagmanager.tag"), "tag", TagInfo::tag, String.class);
      col.setColumnComparator(stringComparator);
      addColumn(col);

      // the amount of entities with that tag
      col = new Column(TmmResourceBundle.getString("tagmanager.usages"), "count", tagInfo -> tagInfo.count() >= 0 ? tagInfo.count() : null,
          Integer.class);
      col.setColumnComparator(integerComparator);
      col.setMinWidth(fontMetrics.stringWidth(TmmResourceBundle.getString("tagmanager.usages")) + getCellPadding());
      col.setColumnResizeable(false);
      addColumn(col);

      // rename/edit
      col = new Column("", "edit", info -> IconManager.EDIT, ImageIcon.class);
      col.setHeaderIcon(IconManager.EDIT_HEADER);
      col.setColumnResizeable(false);
      col.setHeaderTooltip(TmmResourceBundle.getString("tagmanager.rename"));
      col.setCellTooltip(tag -> TmmResourceBundle.getString("tagmanager.rename"));
      addColumn(col);

      // delete
      col = new Column("", "delete", info -> IconManager.DELETE_FOREVER, ImageIcon.class);
      col.setHeaderIcon(IconManager.DELETE_HEADER);
      col.setColumnResizeable(false);
      col.setHeaderTooltip(TmmResourceBundle.getString("tagmanager.delete"));
      col.setCellTooltip(tag -> TmmResourceBundle.getString("tagmanager.delete"));
      addColumn(col);
    }
  }

  private static class UsageTableFormat extends TmmTableFormat<EntityInfo> {

    public UsageTableFormat() {
      FontMetrics fontMetrics = getFontMetrics();

      Column col = new Column(TmmResourceBundle.getString("metatag.title"), "title", EntityInfo::displayLabel, String.class);
      addColumn(col);

      col = new Column(TmmResourceBundle.getString("metatag.filename"), "videoFile", EntityInfo::videoFile, String.class);
      addColumn(col);

      col = new Column(TmmResourceBundle.getString("metatag.tags"), "tagCount", EntityInfo::tagCount, Integer.class);
      col.setMinWidth(fontMetrics.stringWidth(TmmResourceBundle.getString("metatag.tags")) + getCellPadding());
      col.setColumnResizeable(false);
      addColumn(col);
    }
  }

  private record EntityInfo(String displayLabel, int tagCount, String videoFile) {
  }
}

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

import static org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType.BACKGROUND;
import static org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType.THUMB;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.ImageUtils;
import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.movie.MovieArtworkHelper;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.tvshow.TvShowArtworkHelper;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.scraper.ArtworkSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaArtwork.ImageSizeAndUrl;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.interfaces.IMediaArtworkProvider;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.ui.ArtworkDragAndDropListener;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.WrapLayout;
import org.tinymediamanager.ui.components.NoBorderScrollPane;
import org.tinymediamanager.ui.components.button.FlatButton;
import org.tinymediamanager.ui.components.button.SquareIconButton;
import org.tinymediamanager.ui.components.combobox.MediaScraperCheckComboBox;
import org.tinymediamanager.ui.components.combobox.TmmCheckComboBox;
import org.tinymediamanager.ui.components.label.ImageLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.slider.RangeSlider;
import org.tinymediamanager.ui.components.textfield.EnhancedTextField;

import net.miginfocom.swing.MigLayout;

/**
 * The Class ImageChooser. Let the user choose the right image for the media entity
 *
 * @author Manuel Laggner
 */
public class ImageChooserDialog extends TmmDialog {
  private static final Logger              LOGGER         = LoggerFactory.getLogger(ImageChooserDialog.class);
  private static final String              DIALOG_ID      = "imageChooser";

  private final Map<String, Object>        ids;
  private final MediaArtworkType           type;
  private final MediaType                  mediaType;
  private final ImageLabel                 imageLabel;
  private final List<MediaScraper>         artworkScrapers;

  private final List<JToggleButton>        buttons        = new ArrayList<>();
  private final List<JPanel>               imagePanels    = new ArrayList<>();

  private final List<ImageSize>            imageSizes     = new ArrayList<>();
  private final List<MediaLanguages>       imageLanguages = new ArrayList<>();

  private final ActionListener             filterListener;

  private ButtonGroup                      buttonGroup;
  private JProgressBar                     progressBar;
  private JLabel                           lblProgressAction;
  private JScrollPane                      scrollPane;
  private JPanel                           panelImages;
  private LockableViewPort                 viewport;
  private JTextField                       tfImageUrl;

  private String                           openFolderPath = null;
  private List<MediaArtwork>               artwork;
  private List<String>                     extraThumbs    = null;
  private List<String>                     extraFanarts   = null;
  private DownloadTask                     task;

  private MediaScraperCheckComboBox        cbScraper;
  private TmmCheckComboBox<MediaLanguages> cbLanguage;
  private RangeSlider                      widthSlider;
  private RangeSlider                      heightSlider;
  private JLabel                           lblMinWidth;
  private JLabel                           lblMaxWidth;
  private JLabel                           lblMinHeight;
  private JLabel                           lblMaxHeight;
  private JComboBox<SortOrder>             cbSortOrder;

  private JLabel                           lblThumbs;
  private JButton                          btnMarkExtrathumbs;
  private JButton                          btnUnMarkExtrathumbs;
  private JLabel                           lblExtrathumbsSelected;

  private JLabel                           lblFanart;
  private JButton                          btnMarkExtrafanart;
  private JButton                          btnUnMarkExtrafanart;
  private JLabel                           lblExtrafanartSelected;

  private boolean                          persistFilters = false;
  private JButton                          btnClearWidthFilter;
  private JButton                          btnClearHeightFilter;

  enum SortOrder {
    SCORE(TmmResourceBundle.getString("imagechooser.sortby.score")),
    SIZE(TmmResourceBundle.getString("imagechooser.sortby.size")),;

    private final String displayName;

    SortOrder(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  /**
   * Instantiates a new image chooser dialog.
   *
   * @param parent
   *          the parent of this dialog
   * @param ids
   *          the ids
   * @param type
   *          the type
   * @param artworkScrapers
   *          the artwork providers
   * @param imageLabel
   *          the image label
   * @param mediaType
   *          the media for which artwork has to be chosen
   */
  public ImageChooserDialog(JDialog parent, final Map<String, Object> ids, MediaArtworkType type, List<MediaScraper> artworkScrapers,
      ImageLabel imageLabel, MediaType mediaType) {

    super(parent, "", DIALOG_ID);
    this.imageLabel = imageLabel;
    this.type = type;
    this.mediaType = mediaType;
    this.ids = ids;
    this.artworkScrapers = artworkScrapers;
    this.artwork = null;

    switch (mediaType) {
      case MOVIE, MOVIE_SET -> this.persistFilters = MovieModuleManager.getInstance().getSettings().isStoreUiFilters();
      case TV_SHOW, TV_EPISODE -> this.persistFilters = TvShowModuleManager.getInstance().getSettings().isStoreUiFilters();
    }

    filterListener = e -> SwingUtilities.invokeLater(this::filterChanged);

    init();

    cbScraper.addActionListener(filterListener);
    cbLanguage.addActionListener(filterListener);
    cbLanguage.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        // nothing to do here
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        updateEntries();

      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        updateEntries();
      }

      private void updateEntries() {
        updateLanguageCombobox();
      }
    });

    widthSlider.addChangeListener(e -> SwingUtilities.invokeLater(this::filterChanged));
    heightSlider.addChangeListener(e -> SwingUtilities.invokeLater(this::filterChanged));
    cbSortOrder.addActionListener(l -> SwingUtilities.invokeLater(() -> {
      imagePanels.sort(getImagePanelComparator());
      filterChanged();
    }));
  }

  private void init() {
    switch (type) {
      case BACKGROUND:
        setTitle(TmmResourceBundle.getString("image.choose.fanart"));
        break;

      case POSTER:
        setTitle(TmmResourceBundle.getString("image.choose.poster"));
        break;

      case BANNER:
        setTitle(TmmResourceBundle.getString("image.choose.banner"));
        break;

      case SEASON_POSTER:
        Object season = ids.get("tvShowSeason");
        if (season instanceof TvShowSeason tvShowSeason) {
          setTitle(TmmResourceBundle.getString("image.choose.season") + " - " + TmmResourceBundle.getString("metatag.season") + " "
              + tvShowSeason.getSeason());
        }
        else if (season instanceof Integer) {
          setTitle(TmmResourceBundle.getString("image.choose.season") + " - " + TmmResourceBundle.getString("metatag.season") + " " + season);
        }
        else {
          setTitle(TmmResourceBundle.getString("image.choose.season"));
        }
        break;

      case SEASON_FANART:
        season = ids.get("tvShowSeason");
        if (season instanceof TvShowSeason tvShowSeason) {
          setTitle(TmmResourceBundle.getString("image.choose.season.fanart") + " - " + TmmResourceBundle.getString("metatag.season") + " "
              + tvShowSeason.getSeason());
        }
        else if (season instanceof Integer) {
          setTitle(TmmResourceBundle.getString("image.choose.season.fanart") + " - " + TmmResourceBundle.getString("metatag.season") + " " + season);
        }
        else {
          setTitle(TmmResourceBundle.getString("image.choose.season.fanart"));
        }
        break;

      case SEASON_BANNER:
        season = ids.get("tvShowSeason");
        if (season instanceof TvShowSeason tvShowSeason) {
          setTitle(TmmResourceBundle.getString("image.choose.season.banner") + " - " + TmmResourceBundle.getString("metatag.season") + " "
              + tvShowSeason.getSeason());
        }
        else if (season instanceof Integer) {
          setTitle(TmmResourceBundle.getString("image.choose.season.banner") + " - " + TmmResourceBundle.getString("metatag.season") + " " + season);
        }
        else {
          setTitle(TmmResourceBundle.getString("image.choose.season.banner"));
        }
        break;

      case SEASON_THUMB:
        season = ids.get("tvShowSeason");
        if (season instanceof TvShowSeason tvShowSeason) {
          setTitle(TmmResourceBundle.getString("image.choose.season.thumb") + " - " + TmmResourceBundle.getString("metatag.season") + " "
              + tvShowSeason.getSeason());
        }
        else if (season instanceof Integer) {
          setTitle(TmmResourceBundle.getString("image.choose.season.thumb") + " - " + TmmResourceBundle.getString("metatag.season") + " " + season);
        }
        else {
          setTitle(TmmResourceBundle.getString("image.choose.season.thumb"));
        }
        break;

      case CLEARART:
        setTitle(TmmResourceBundle.getString("image.choose.clearart"));
        break;

      case DISC:
        setTitle(TmmResourceBundle.getString("image.choose.disc"));
        break;

      case CLEARLOGO:
        setTitle(TmmResourceBundle.getString("image.choose.clearlogo"));
        break;

      case CHARACTERART:
        setTitle(TmmResourceBundle.getString("image.choose.characterart"));
        break;

      case THUMB:
        setTitle(TmmResourceBundle.getString("image.choose.thumb"));
        break;

      case KEYART:
        setTitle(TmmResourceBundle.getString("image.choose.keyart"));
        break;
    }

    /* UI components */
    JPanel contentPanel = new JPanel();
    getContentPane().add(contentPanel, BorderLayout.CENTER);
    contentPanel.setLayout(new MigLayout("hidemode 3, insets n n 0 n", "[850lp,grow][]", "[][10lp!][500lp,grow][shrink 0][][]"));
    {
      JPanel panelFilter = new JPanel();
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelFilter, new TmmLabel(TmmResourceBundle.getString("movieextendedsearch.filterby")),
          true);

      contentPanel.add(collapsiblePanel, "cell 0 0 2 1,grow, wmin 0");
      panelFilter.setLayout(
          new MigLayout("insets 0", "[][25%:n][30lp!][][30lp:n,right][15%:25%][30lp:n,right][75lp:75lp,grow][grow][10lp!]", "[center][center]"));

      {
        JLabel lblScraperT = new TmmLabel(TmmResourceBundle.getString("scraper.artwork"));
        panelFilter.add(lblScraperT, "cell 0 0");

        cbScraper = new MediaScraperCheckComboBox(artworkScrapers);
        cbScraper.setFocusable(false);
        panelFilter.add(cbScraper, "cell 1 0,growx,wmin 0");

        btnClearWidthFilter = new FlatButton(IconManager.DELETE);
        btnClearWidthFilter.addActionListener((actionEvent) -> {
          widthSlider.setLowValue(widthSlider.getMinimum());
          widthSlider.setHighValue(widthSlider.getMaximum());
          filterChanged();
        });
        panelFilter.add(btnClearWidthFilter, "cell 7 0");

        JLabel lblLanguageT = new TmmLabel(TmmResourceBundle.getString("metatag.language"));
        panelFilter.add(lblLanguageT, "cell 0 1");

        cbLanguage = new TmmCheckComboBox();
        cbLanguage.setFocusable(false);
        cbLanguage.setSingleLineEditor(); // looks weird when preselecting langu?
        panelFilter.add(cbLanguage, "cell 1 1,growx,wmin 0");
      }
      {
        JLabel lblWidthT = new TmmLabel(TmmResourceBundle.getString("metatag.width"));
        panelFilter.add(lblWidthT, "cell 3 0");

        lblMinWidth = new JLabel("");
        TmmFontHelper.changeFont(lblMinWidth, TmmFontHelper.L1);
        panelFilter.add(lblMinWidth, "cell 4 0");

        widthSlider = new RangeSlider(0, 4000);
        configureInitialSlider(widthSlider, true);
        panelFilter.add(widthSlider, "cell 5 0,growx");

        lblMaxWidth = new JLabel("");
        TmmFontHelper.changeFont(lblMaxWidth, TmmFontHelper.L1);
        panelFilter.add(lblMaxWidth, "cell 6 0");

        JLabel lblHeightT = new TmmLabel(TmmResourceBundle.getString("metatag.height"));
        panelFilter.add(lblHeightT, "cell 3 1");

        lblMinHeight = new JLabel("");
        TmmFontHelper.changeFont(lblMinHeight, TmmFontHelper.L1);
        panelFilter.add(lblMinHeight, "cell 4 1,alignx right");

        heightSlider = new RangeSlider(0, 4000);
        configureInitialSlider(heightSlider, false);
        panelFilter.add(heightSlider, "cell 5 1,growx");

        lblMaxHeight = new JLabel("");
        TmmFontHelper.changeFont(lblMaxHeight, TmmFontHelper.L1);
        panelFilter.add(lblMaxHeight, "cell 6 1");
      }
      {
        JLabel lblSortByT = new JLabel(TmmResourceBundle.getString("imagechooser.sortby"));
        panelFilter.add(lblSortByT, "flowx,cell 8 0,alignx trailing");

        cbSortOrder = new JComboBox(SortOrder.values());
        panelFilter.add(cbSortOrder, "cell 8 0,alignx right");
      }

      btnClearHeightFilter = new FlatButton(IconManager.DELETE);
      btnClearHeightFilter.addActionListener((actionEvent) -> {
        heightSlider.setLowValue(heightSlider.getMinimum());
        heightSlider.setHighValue(heightSlider.getMaximum());
        filterChanged();
      });

      panelFilter.add(btnClearHeightFilter, "cell 7 1");
    }
    {
      scrollPane = new NoBorderScrollPane();
      viewport = new LockableViewPort();

      scrollPane.setViewport(viewport);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      contentPanel.add(scrollPane, "cell 0 2 2 1,grow");
      {
        panelImages = new JPanel();
        viewport.setView(panelImages);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panelImages.setLayout(new WrapLayout(FlowLayout.LEFT));
      }
    }
    {
      JSeparator separator = new JSeparator();
      contentPanel.add(separator, "cell 0 3 2 1,growx");
    }
    {
      tfImageUrl = new EnhancedTextField(TmmResourceBundle.getString("image.inserturl"));
      contentPanel.add(tfImageUrl, "cell 0 4,growx");
      tfImageUrl.setColumns(10);

      JButton btnAddImage = new JButton(TmmResourceBundle.getString("image.downloadimage"));
      btnAddImage.addActionListener(e -> {
        if (StringUtils.isNotBlank(tfImageUrl.getText())) {
          downloadAndPreviewImage(tfImageUrl.getText());
        }
      });
      contentPanel.add(btnAddImage, "cell 1 4");
    }

    {
      // add buttons to select/deselect all extrafanarts/extrathumbs
      if (type == BACKGROUND || type == THUMB) {
        lblThumbs = new JLabel(TmmResourceBundle.getString("mediafiletype.extrathumb") + ":");
        contentPanel.add(lblThumbs, "flowx,cell 0 5");
        lblThumbs.setVisible(false);

        btnMarkExtrathumbs = new SquareIconButton(IconManager.CHECK_ALL);
        contentPanel.add(btnMarkExtrathumbs, "cell 0 5");
        btnMarkExtrathumbs.setVisible(false);
        btnMarkExtrathumbs.setToolTipText(TmmResourceBundle.getString("image.extrathumbs.markall"));
        btnMarkExtrathumbs.addActionListener(arg0 -> {
          for (JToggleButton button : buttons) {
            if (button.getClientProperty("MediaArtworkExtrathumb") instanceof JCheckBox chkbx) {
              chkbx.setSelected(true);
              updateExtrathumbSelectedCount();
            }
          }
        });

        btnUnMarkExtrathumbs = new SquareIconButton(IconManager.CLEAR_ALL);
        contentPanel.add(btnUnMarkExtrathumbs, "cell 0 5");
        btnUnMarkExtrathumbs.setVisible(false);
        btnUnMarkExtrathumbs.setToolTipText(TmmResourceBundle.getString("image.extrathumbs.unmarkall"));
        btnUnMarkExtrathumbs.addActionListener(arg0 -> {
          for (JToggleButton button : buttons) {
            if (button.getClientProperty("MediaArtworkExtrathumb") instanceof JCheckBox chkbx) {
              chkbx.setSelected(false);
              updateExtrathumbSelectedCount();
            }
          }
        });

        lblExtrathumbsSelected = new JLabel("");
        contentPanel.add(lblExtrathumbsSelected, "cell 0 5, gapx n 100lp");
      }
    }
    {
      if (type == BACKGROUND) {
        lblFanart = new JLabel(TmmResourceBundle.getString("mediafiletype.extrafanart") + ":");
        contentPanel.add(lblFanart, "flowx,cell 0 5");
        lblFanart.setVisible(false);

        btnMarkExtrafanart = new SquareIconButton(IconManager.CHECK_ALL);
        contentPanel.add(btnMarkExtrafanart, "cell 0 5");
        btnMarkExtrafanart.setVisible(false);
        btnMarkExtrafanart.setToolTipText(TmmResourceBundle.getString("image.extrafanart.markall"));
        btnMarkExtrafanart.addActionListener(arg0 -> {
          for (JToggleButton button : buttons) {
            if (button.getClientProperty("MediaArtworkExtrafanart") instanceof JCheckBox chkbx) {
              chkbx.setSelected(true);
              updateExtraFanartSelectedCount();
            }
          }
        });

        btnUnMarkExtrafanart = new SquareIconButton(IconManager.CLEAR_ALL);
        contentPanel.add(btnUnMarkExtrafanart, "cell 0 5");
        btnUnMarkExtrafanart.setVisible(false);
        btnUnMarkExtrafanart.setToolTipText(TmmResourceBundle.getString("image.extrafanart.unmarkall"));
        btnUnMarkExtrafanart.addActionListener(arg0 -> {
          for (JToggleButton button : buttons) {
            if (button.getClientProperty("MediaArtworkExtrafanart") instanceof JCheckBox chkbx) {
              chkbx.setSelected(false);
              updateExtraFanartSelectedCount();
            }
          }
        });

        lblExtrafanartSelected = new JLabel("");
        contentPanel.add(lblExtrafanartSelected, "cell 0 5");
      }
    }

    {
      JPanel infoPanel = new JPanel();
      infoPanel.setLayout(new MigLayout("", "[][grow]", "[]"));

      progressBar = new JProgressBar();
      infoPanel.add(progressBar, "cell 0 0");

      lblProgressAction = new JLabel("");
      infoPanel.add(lblProgressAction, "cell 1 0");

      setBottomInformationPanel(infoPanel);
    }

    {
      JButton cancelButton = new JButton(TmmResourceBundle.getString("Button.cancel"));
      Action actionCancel = new CancelAction();
      cancelButton.setAction(actionCancel);
      cancelButton.setActionCommand("Cancel");
      addButton(cancelButton);

      JButton btnAddFile = new JButton(TmmResourceBundle.getString("Button.addfile"));
      Action actionLocalFile = new LocalFileChooseAction();
      btnAddFile.setAction(actionLocalFile);
      addButton(btnAddFile);

      JButton okButton = new JButton(TmmResourceBundle.getString("Button.ok"));
      Action actionOK = new OkAction();
      okButton.setAction(actionOK);
      okButton.setActionCommand("OK");
      addDefaultButton(okButton);
    }

    {
      new DropTarget(this, new ArtworkDragAndDropListener(imageLabel) {
        @Override
        public void drop(DropTargetDropEvent dtde) {
          super.drop(dtde);

          // cancel the task and close the dialog
          if (task != null) {
            task.cancel(true);
          }

          setVisible(false);
        }
      });
    }

    if (persistFilters) {
      // load saved filters
      loadFilters();
    }

    // init values
    filterChanged();
  }

  private void loadFilters() {

    // artwork scrapers
    List<String> scraperIds = ParserUtils
        .split(TmmProperties.getInstance().getProperty("imagechooser.scrapers." + mediaType.name() + "." + type.name()));
    if (!scraperIds.isEmpty()) {
      List<MediaScraper> selectedScrapers = new ArrayList<>();
      for (MediaScraper scraper : cbScraper.getItems()) {
        if (scraperIds.contains(scraper.getId())) {
          selectedScrapers.add(scraper);
        }
      }

      if (!selectedScrapers.isEmpty()) {
        cbScraper.setSelectedItems(selectedScrapers);
      }
    }

    // language
    List<String> languages = ParserUtils
        .split(TmmProperties.getInstance().getProperty("imagechooser.language." + mediaType.name() + "." + type.name()));
    if (!languages.isEmpty()) {
      List<MediaLanguages> selectedLanguages = new ArrayList<>();

      for (String lang : languages) {
        try {
          MediaLanguages language = MediaLanguages.valueOf(lang);
          selectedLanguages.add(language);
        }
        catch (Exception e) {
          // just ignore
        }
      }

      if (!selectedLanguages.isEmpty()) {
        cbLanguage.setItems(selectedLanguages);
        cbLanguage.setSelectedItems(selectedLanguages);
      }
    }

    // width
    int minWidth = TmmProperties.getInstance().getPropertyAsInteger("imagechooser.minwidth." + mediaType.name() + "." + type.name());
    int maxWidth = TmmProperties.getInstance().getPropertyAsInteger("imagechooser.maxwidth." + mediaType.name() + "." + type.name());
    if (minWidth > 0 && maxWidth > 0 && maxWidth >= minWidth) {
      try {
        widthSlider.setLowValue(minWidth);
        widthSlider.setHighValue(maxWidth);
      }
      catch (Exception ignored) {
        // ignore - just not crash
      }
    }

    // height
    int minHeight = TmmProperties.getInstance().getPropertyAsInteger("imagechooser.minheight." + mediaType.name() + "." + type.name());
    int maxHeight = TmmProperties.getInstance().getPropertyAsInteger("imagechooser.maxheight." + mediaType.name() + "." + type.name());
    if (minHeight > 0 && maxHeight > 0 && maxHeight >= minHeight) {
      try {
        heightSlider.setLowValue(minHeight);
        heightSlider.setHighValue(maxHeight);
      }
      catch (Exception ignored) {
        // ignore - just not crash
      }
    }

    // sort order
    String sortOrder = TmmProperties.getInstance().getProperty("imagechooser.sortorder." + mediaType.name() + "." + type.name());
    if (StringUtils.isNotBlank(sortOrder)) {
      try {
        cbSortOrder.setSelectedItem(SortOrder.valueOf(sortOrder));
      }
      catch (Exception ignored) {
        // ignore - just not crash
      }
    }
  }

  private void startScraping() {
    // scrape from online sources or use pre-selected artwork
    task = new DownloadTask(ids, artworkScrapers, artwork);
    task.execute();
  }

  private void updateExtrathumbSelectedCount() {
    int count = 0;
    for (JToggleButton btn : buttons) {
      if (btn.getClientProperty("MediaArtworkExtrathumb") instanceof JCheckBox checkBox) {
        if (checkBox.isSelected()) {
          count++;
        }
      }
    }
    if (count > 0) {
      lblExtrathumbsSelected.setText(count + " " + TmmResourceBundle.getString("tmm.selected"));
    }
    else {
      lblExtrathumbsSelected.setText("");
    }
  }

  private void setArtwork(List<MediaArtwork> artwork) {
    this.artwork = artwork;
  }

  private void updateExtraFanartSelectedCount() {
    int count = 0;
    for (JToggleButton btn : buttons) {
      if (btn.getClientProperty("MediaArtworkExtrafanart") instanceof JCheckBox checkBox) {
        if (checkBox.isSelected()) {
          count++;
        }
      }
    }
    if (count > 0) {
      lblExtrafanartSelected.setText(count + " " + TmmResourceBundle.getString("tmm.selected"));
    }
    else {
      lblExtrafanartSelected.setText("");
    }
  }

  public void bindExtraThumbs(List<String> extraThumbs) {
    if (type != BACKGROUND && type != THUMB) {
      return;
    }

    this.extraThumbs = extraThumbs;

    if (extraThumbs != null) {
      lblThumbs.setVisible(true);
      btnMarkExtrathumbs.setVisible(true);
      btnUnMarkExtrathumbs.setVisible(true);
    }
    else {
      lblThumbs.setVisible(false);
      btnMarkExtrathumbs.setVisible(false);
      btnUnMarkExtrathumbs.setVisible(false);
    }
  }

  public void bindExtraFanarts(List<String> extraFanarts) {
    if (type != BACKGROUND) {
      return;
    }

    this.extraFanarts = extraFanarts;

    if (extraFanarts != null) {
      lblFanart.setVisible(true);
      btnMarkExtrafanart.setVisible(true);
      btnUnMarkExtrafanart.setVisible(true);
    }
    else {
      lblFanart.setVisible(false);
      btnMarkExtrafanart.setVisible(false);
      btnUnMarkExtrafanart.setVisible(false);
    }
  }

  public void setOpenFolderPath(String openFolderPath) {
    this.openFolderPath = openFolderPath;
  }

  private void startProgressBar(String description) {
    lblProgressAction.setText(description);
    progressBar.setVisible(true);
    progressBar.setIndeterminate(true);
  }

  private void stopProgressBar() {
    lblProgressAction.setText("");
    progressBar.setVisible(false);
    progressBar.setIndeterminate(false);
  }

  private void addImage(byte[] imageData, final MediaArtwork artwork) throws Exception {
    BufferedImage originalImage = ImageUtils.createImage(imageData);
    artwork.addImageSize(originalImage.getWidth(), originalImage.getHeight(), artwork.getPreviewUrl(), 0);

    Point size = null;

    GridBagLayout gbl = new GridBagLayout();

    switch (type) {
      case BACKGROUND:
      case CLEARART:
      case THUMB:
      case DISC:
      case CHARACTERART:
        size = ImageUtils.calculateSize(300, 150, originalImage.getWidth(), originalImage.getHeight(), true);
        break;

      case BANNER:
      case LOGO:
      case CLEARLOGO:
        size = ImageUtils.calculateSize(300, 100, originalImage.getWidth(), originalImage.getHeight(), true);
        break;

      case POSTER:
      case KEYART:
      default:
        size = ImageUtils.calculateSize(150, 250, originalImage.getWidth(), originalImage.getHeight(), true);
        break;

    }

    gbl.columnWeights = new double[] { Double.MIN_VALUE };
    gbl.rowWeights = new double[] { Double.MIN_VALUE };
    JPanel imagePanel = new JPanel();
    imagePanel.setLayout(gbl);

    int row = 0;

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(5, 5, 5, 5);

    JToggleButton button = new JToggleButton();
    button.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() >= 2 && e.getButton() == MouseEvent.BUTTON1) {
          button.setSelected(true);
          new OkAction().actionPerformed(new ActionEvent(e.getSource(), e.getID(), "OK"));
        }
      }
    });
    button.setBackground(Color.white);
    button.setMargin(new Insets(10, 10, 10, 10));
    if (artwork.isAnimated()) {
      button.setText("<html><img width=\"" + size.x + "\" height=\"" + size.y + "\" src='" + artwork.getPreviewUrl() + "'/></html>");
      button.setBorder(BorderFactory.createLineBorder(Color.GREEN, 3));
    }
    else {
      ImageIcon imageIcon = new ImageIcon(
          Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, size.x, size.y, Scalr.OP_ANTIALIAS));
      button.setIcon(imageIcon);
    }

    button.putClientProperty("MediaArtwork", artwork);

    buttons.add(button);
    imagePanel.add(button, gbc);

    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = ++row;
    gbc.gridwidth = 3;
    gbc.anchor = GridBagConstraints.LAST_LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(0, 5, 0, 5);

    JComboBox cb;
    if (!artwork.getImageSizes().isEmpty()) {
      cb = new JComboBox(artwork.getImageSizes().toArray());
    }
    else {
      cb = new JComboBox(new String[] { originalImage.getWidth() + "x" + originalImage.getHeight() });
    }
    button.putClientProperty("MediaArtworkSize", cb);
    imagePanel.add(cb, gbc);

    // release memory
    originalImage.flush();

    /* show image button */
    gbc = new GridBagConstraints();
    gbc.gridx = 3;
    gbc.gridy = row;
    gbc.insets = new Insets(0, 5, 0, 5);

    JButton btnShowOriginalImage = new FlatButton(IconManager.LINK);
    btnShowOriginalImage.setToolTipText(TmmResourceBundle.getString("image.showoriginal"));
    btnShowOriginalImage.addActionListener(e -> {
      ImagePreviewDialog dialog = new ImagePreviewDialog(artwork.getOriginalUrl());

      String path;
      if (StringUtils.isNotBlank(openFolderPath)) {
        path = openFolderPath;
      }
      else {
        path = TmmProperties.getInstance().getProperty(DIALOG_ID + ".path");
      }

      dialog.setOpenFolderPath(path);
      dialog.setVisible(true);
    });
    imagePanel.add(btnShowOriginalImage, gbc);

    if (extraFanarts != null || extraThumbs != null) {
      int x = 0;
      int y = ++row;

      // should we provide an option for extrafanart
      if (extraFanarts != null) {
        gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.LAST_LINE_START;
        gbc.insets = new Insets(0, 5, 0, 5);
        JCheckBox chkbx = new JCheckBox("Extrafanart");
        button.putClientProperty("MediaArtworkExtrafanart", chkbx);
        chkbx.addActionListener(l -> updateExtraFanartSelectedCount());
        imagePanel.add(chkbx, gbc);

        x += 2;
      }

      // should we provide an option for extrathumbs
      if (extraThumbs != null) {
        gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.LAST_LINE_START;
        gbc.insets = new Insets(0, 5, 0, 5);
        JCheckBox chkbx = new JCheckBox("Extrathumb");
        button.putClientProperty("MediaArtworkExtrathumb", chkbx);
        chkbx.addActionListener(l -> updateExtrathumbSelectedCount());
        imagePanel.add(chkbx, gbc);
      }
    }

    imagePanel.putClientProperty("MediaArtwork", artwork);

    imagePanels.add(imagePanel);
    imagePanels.sort(getImagePanelComparator());

    // update filters
    SwingUtilities.invokeLater(() -> {
      // we can directly update the lists since this is the EDT which is synchronized per se
      artwork.getImageSizes().forEach(sizeAndUrl -> {
        ImageSize imageSize = new ImageSize(sizeAndUrl.getWidth(), sizeAndUrl.getHeight());
        if (!imageSizes.contains(imageSize)) {
          imageSizes.add(imageSize);
        }
      });

      // - indicates no text
      MediaLanguages mediaLanguage;
      if ("-".equals(artwork.getLanguage())) {
        mediaLanguage = MediaLanguages.none;
      }
      else {
        mediaLanguage = MediaLanguages.get(artwork.getLanguage());
      }

      if (!imageLanguages.contains(mediaLanguage)) {
        imageLanguages.add(mediaLanguage);
      }

      updateSizeSlider();
      updateLanguageCombobox();
      filterChanged();
    });
  }

  /**
   * Configures the initial state of a RangeSlider including maximum value, high value, ticks, and a listener for dynamic reconfiguration.
   *
   * @param slider
   *          the RangeSlider to configure
   * @param isWidth
   *          true to configure width slider; false for height slider
   */
  private void configureInitialSlider(RangeSlider slider, boolean isWidth) {
    // cap maximum slightly above well-known max for the type
    slider.setMaximum(getSuggestedMax(isWidth));
    configureSliderTicks(slider, isWidth);
    slider.setHighValue(slider.getMaximum());
    // Add listener to reconfigure ticks when maximum changes
    slider.addPropertyChangeListener("maximum", evt -> configureSliderTicks(slider, isWidth));
  }

  @NotNull
  private Comparator<JPanel> getImagePanelComparator() {
    return (o1, o2) -> {
      Object obj1 = o1.getClientProperty("MediaArtwork");
      Object obj2 = o2.getClientProperty("MediaArtwork");

      if (!(obj1 instanceof MediaArtwork artwork1) || !(obj2 instanceof MediaArtwork artwork2)) {
        return 0;
      }

      int result = 0;

      if (cbSortOrder.getSelectedItem() == SortOrder.SCORE) {
        // sort by score
        int score1 = 0;
        int score2 = 0;
        if (mediaType == MediaType.MOVIE || mediaType == MediaType.MOVIE_SET) {
          score1 = MovieArtworkHelper.getMatchingScoreAccordingPreferences(artwork1);
          score2 = MovieArtworkHelper.getMatchingScoreAccordingPreferences(artwork2);
        }
        else if (mediaType == MediaType.TV_SHOW || mediaType == MediaType.TV_EPISODE) {
          score1 = TvShowArtworkHelper.getMatchingScoreAccordingPreferences(artwork1);
          score2 = TvShowArtworkHelper.getMatchingScoreAccordingPreferences(artwork2);
        }
        result = Integer.compare(score2, score1);
        if (result == 0) {
          // same score - sort by likes descending
          result = Integer.compare(artwork2.getLikes(), artwork1.getLikes());
        }
        if (result == 0) {
          // last resort
          if (artwork1.getBiggestArtwork() == null || artwork2.getBiggestArtwork() == null) {
            result = 0; // cannot compare!
          }
          else {
            result = artwork2.getBiggestArtwork().compareTo(artwork1.getBiggestArtwork());
          }
        }
      }
      else if (cbSortOrder.getSelectedItem() == SortOrder.SIZE) {
        // sort by size
        if (artwork1.getBiggestArtwork() == null || artwork2.getBiggestArtwork() == null) {
          return 0; // cannot compare!
        }
        result = artwork2.getBiggestArtwork().compareTo(artwork1.getBiggestArtwork());
        if (result == 0) {
          // same size - sort by likes descending
          result = Integer.compare(artwork2.getLikes(), artwork1.getLikes());
        }
        if (result == 0) {
          // last resort
          result = MovieArtworkHelper.getMatchingScoreAccordingPreferences(artwork2)
              - MovieArtworkHelper.getMatchingScoreAccordingPreferences(artwork1);
        }
      }

      return result;
    };
  }

  private void updateSizeSlider() {
    int maxWidth = 0;

    for (ImageSize imageSize : imageSizes) {
      if (imageSize.width > maxWidth) {
        maxWidth = imageSize.width;
      }
    }

    // default suggested cap based on type
    int suggestedWidthMax = getSuggestedMax(true);
    int targetWidthMax = Math.max(suggestedWidthMax, maxWidth);
    if (widthSlider.getMaximum() != targetWidthMax) {
      int newWidth = roundHighValue(targetWidthMax);
      widthSlider.setMaximum(newWidth); // enable the next tick to be print
      widthSlider.setHighValue(newWidth);
    }

    int maxHeight = 0;
    for (ImageSize imageSize : imageSizes) {
      if (imageSize.height > maxHeight) {
        maxHeight = imageSize.height;
      }
    }

    int suggestedHeightMax = getSuggestedMax(false);
    int targetHeightMax = Math.max(suggestedHeightMax, maxHeight);
    if (heightSlider.getMaximum() != targetHeightMax) {
      int newHeight = roundHighValue(targetHeightMax);
      heightSlider.setMaximum(newHeight); // enable the next tick to be print
      heightSlider.setHighValue(newHeight);
    }

    // reconfigure ticks when range changes
    configureSliderTicks(widthSlider, true);
    configureSliderTicks(heightSlider, false);
  }

  private int roundHighValue(int value) {
    if (value <= 0) {
      return 0;
    }

    // multiply by 1.05 and round up to the next 100
    int rounded = (int) Math.ceil(value * 1.05 / 100.0) * 100;
    return rounded;
  }

  private void updateLanguageCombobox() {
    if (cbLanguage.isPopupVisible()) {
      // do not update combobox while popup is open
      // the update will be done when the popup is closed
      return;
    }

    List<MediaLanguages> allItems = cbLanguage.getItems();

    if (allItems.size() == imageLanguages.size()) {
      // same size, no need to update
      return;
    }

    cbLanguage.removeActionListener(filterListener);

    List<MediaLanguages> selectedItems = cbLanguage.getSelectedItems();

    for (MediaLanguages mediaLanguages : imageLanguages) {
      if (!allItems.contains(mediaLanguages)) {
        allItems.add(mediaLanguages);
      }
    }

    // and add them in the right order
    List<MediaLanguages> newValues = new ArrayList<>();

    // add none in the front - this will come from MediaLanguages.valuesSorted()
    newValues.add(MediaLanguages.none);

    for (MediaLanguages mediaLanguages : MediaLanguages.valuesSorted()) {
      if (allItems.contains(mediaLanguages)) {
        newValues.add(mediaLanguages);
      }
    }

    cbLanguage.setItems(newValues);
    cbLanguage.setSelectedItems(selectedItems);

    cbLanguage.addActionListener(filterListener);
  }

  private void filterChanged() {
    // update labels
    lblMinWidth.setText(String.valueOf(widthSlider.getLowValue()));
    lblMaxWidth.setText(String.valueOf(widthSlider.getHighValue()));
    lblMinHeight.setText(String.valueOf(heightSlider.getLowValue()));
    lblMaxHeight.setText(String.valueOf(heightSlider.getHighValue()));

    panelImages.removeAll();
    ButtonModel selectedButton = null;
    MediaArtwork selectedArtwork = null;

    if (buttonGroup != null) {
      selectedButton = buttonGroup.getSelection();
      // Also store which artwork is currently selected so we can restore it if it's still visible
      if (selectedButton != null) {
        for (JToggleButton btn : buttons) {
          if (btn.getModel() == selectedButton && btn.getClientProperty("MediaArtwork") instanceof MediaArtwork art) {
            selectedArtwork = art;
            break;
          }
        }
      }
    }

    buttonGroup = new NoneSelectedButtonGroup();

    for (JPanel panel : imagePanels) {
      Object obj = panel.getClientProperty("MediaArtwork");
      if (!(obj instanceof MediaArtwork mediaArtwork)) {
        continue;
      }

      if (cbScraper.getSelectedItems().isEmpty() && widthSlider.isUnchanged() && heightSlider.isUnchanged()
          && cbLanguage.getSelectedItems().isEmpty()) {
        // nothing selected - add all
        panelImages.add(panel);
        for (Component child : panel.getComponents()) {
          if (child instanceof JCheckBox) {
            // JCheckBox is a subclass of JToggleButton -> skip
            continue;
          }

          if (child instanceof JToggleButton button) {
            buttonGroup.add(button);
            // Update the resolution combobox to show all sizes
            updateResolutionCombobox(button, false);
          }
        }
      }
      else {
        // filter
        boolean scraperMatch = true;
        boolean sizeMatch = true;
        boolean languageMatch = true;

        // on scraper
        if (!cbScraper.getSelectedItems().isEmpty()) {
          scraperMatch = false;

          for (MediaScraper scraper : cbScraper.getSelectedItems()) {
            if (scraper.getId().equals(mediaArtwork.getProviderId())) {
              scraperMatch = true;
              break;
            }
          }
        }

        // on size
        if (!widthSlider.isUnchanged() || !heightSlider.isUnchanged()) {
          sizeMatch = false;

          for (ImageSizeAndUrl imageSizeAndUrl : mediaArtwork.getImageSizes()) {
            if (widthSlider.contains(imageSizeAndUrl.getWidth()) && heightSlider.contains(imageSizeAndUrl.getHeight())) {
              sizeMatch = true;
              break;
            }
          }
        }

        // on language
        if (!cbLanguage.getSelectedItems().isEmpty()) {
          languageMatch = false;
          List<String> languages = new ArrayList<>();

          for (MediaLanguages mediaLanguages : cbLanguage.getSelectedItems()) {
            if (mediaLanguages == MediaLanguages.none) {
              languages.add("-");
              languages.add("");
            }
            else {
              languages.add(mediaLanguages.getLanguage().toLowerCase(Locale.ROOT));
            }
          }

          if (languages.contains(mediaArtwork.getLanguage())) {
            languageMatch = true;
          }
        }

        if (scraperMatch && sizeMatch && languageMatch) {
          panelImages.add(panel);
          for (Component child : panel.getComponents()) {
            if (child instanceof JCheckBox) {
              continue;
            }

            if (child instanceof JToggleButton button) {
              buttonGroup.add(button);
              // Update the resolution combobox to only show matching sizes
              updateResolutionCombobox(button, !widthSlider.isUnchanged() || !heightSlider.isUnchanged());
            }
          }
        }
      }
    }

    // Restore the selection if the previously selected artwork is still visible
    if (selectedArtwork != null) {
      for (Component comp : panelImages.getComponents()) {
        if (comp instanceof JPanel panel) {
          if (panel.getClientProperty("MediaArtwork") == selectedArtwork) {
            for (Component child : panel.getComponents()) {
              if (child instanceof JCheckBox) {
                continue;
              }
              if (child instanceof JToggleButton button && button.getModel() == selectedButton) {
                buttonGroup.setSelected(button.getModel(), true);
                break;
              }
            }
            break;
          }
        }
      }
    }

    viewport.setLocked(true);
    panelImages.revalidate();
    scrollPane.revalidate();
    getContentPane().revalidate();
    getContentPane().repaint();
    viewport.setLocked(false);
  }

  /**
   * Update the resolution combobox for a button to show only sizes matching the current filter.
   *
   * @param button
   *          the button containing the combobox
   * @param filterBySizeRange
   *          true to filter by size range, false to show all
   */
  private void updateResolutionCombobox(JToggleButton button, boolean filterBySizeRange) {
    Object comboboxProperty = button.getClientProperty("MediaArtworkSize");
    Object artworkProperty = button.getClientProperty("MediaArtwork");

    if (!(comboboxProperty instanceof JComboBox) || !(artworkProperty instanceof MediaArtwork)) {
      return;
    }

    @SuppressWarnings("rawtypes")
    JComboBox cb = (JComboBox) comboboxProperty;
    MediaArtwork artwork = (MediaArtwork) artworkProperty;

    // Remove all items
    cb.removeAllItems();

    if (artwork.getImageSizes().isEmpty()) {
      // No sizes available, show nothing or default
      return;
    }

    // Add items based on filter
    for (ImageSizeAndUrl sizeAndUrl : artwork.getImageSizes()) {
      if (!filterBySizeRange) {
        // No filter active - add all sizes
        cb.addItem(sizeAndUrl);
      }
      else {
        // Filter by size range
        if (widthSlider.contains(sizeAndUrl.getWidth()) && heightSlider.contains(sizeAndUrl.getHeight())) {
          cb.addItem(sizeAndUrl);
        }
      }
    }

    // Restore selection if it's still in the list
    if (cb.getItemCount() > 0) {
      // Select first item if previous selection is no longer available
      cb.setSelectedIndex(0);
    }
  }

  private void downloadAndPreviewImage(String url) {
    Runnable task = () -> {
      try {
        final MediaArtwork art;
        switch (type) {
          case BANNER:
            art = new MediaArtwork("", MediaArtworkType.BANNER);
            break;

          case CLEARART:
            art = new MediaArtwork("", MediaArtworkType.CLEARART);
            break;

          case DISC:
            art = new MediaArtwork("", MediaArtworkType.DISC);
            break;

          case BACKGROUND:
            art = new MediaArtwork("", BACKGROUND);
            break;

          case CLEARLOGO:
          case LOGO:
            art = new MediaArtwork("", MediaArtworkType.CLEARLOGO);
            break;

          case CHARACTERART:
            art = new MediaArtwork("", MediaArtworkType.CHARACTERART);
            break;

          case POSTER:
            art = new MediaArtwork("", MediaArtworkType.POSTER);
            break;

          case SEASON_POSTER:
            art = new MediaArtwork("", MediaArtworkType.SEASON_POSTER);
            break;

          case SEASON_FANART:
            art = new MediaArtwork("", MediaArtworkType.SEASON_FANART);
            break;

          case SEASON_BANNER:
            art = new MediaArtwork("", MediaArtworkType.SEASON_BANNER);
            break;

          case SEASON_THUMB:
            art = new MediaArtwork("", MediaArtworkType.SEASON_THUMB);
            break;

          case THUMB:
            art = new MediaArtwork("", MediaArtworkType.THUMB);
            break;

          case KEYART:
            art = new MediaArtwork("", MediaArtworkType.KEYART);
            break;

          default:
            return;
        }
        art.setPreviewUrl(url);
        art.setOriginalUrl(url);

        Url previewUrl = new Url(art.getPreviewUrl());
        byte[] artworkBytes = previewUrl.getBytesWithRetry(5);

        if (artworkBytes.length > 0) {

          SwingUtilities.invokeLater(() -> {
            try {
              addImage(artworkBytes, art);
            }
            catch (Exception e) {
              LOGGER.debug("Could not add image '{}' - '{}'", art.getPreviewUrl(), e.getMessage());
            }
          });
          tfImageUrl.setText("");
        }
        else {
          JOptionPane.showMessageDialog(ImageChooserDialog.this, TmmResourceBundle.getString("message.errorloadimage"));
        }
      }
      catch (Exception e) {
        LOGGER.error("Could not download manually entered image url '{}' - '{}'", tfImageUrl.getText(), e.getMessage());
      }
    };
    task.run();
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible && task == null) {
      startScraping();
    }

    super.setVisible(visible);
  }

  private void persistFilters() {
    List<MediaScraper> selectedScrapers = cbScraper.getSelectedItems();
    List<String> scraperIds = new ArrayList<>();
    for (MediaScraper scraper : selectedScrapers) {
      scraperIds.add(scraper.getId());
    }
    TmmProperties.getInstance().putProperty("imagechooser.scrapers." + mediaType.name() + "." + type.name(), String.join(",", scraperIds));

    List<MediaLanguages> selectedLanguages = cbLanguage.getSelectedItems();
    List<String> languages = new ArrayList<>();
    for (MediaLanguages lang : selectedLanguages) {
      languages.add(lang.name());
    }
    TmmProperties.getInstance().putProperty("imagechooser.language." + mediaType.name() + "." + type.name(), String.join(",", languages));

    TmmProperties.getInstance()
        .putProperty("imagechooser.minwidth." + mediaType.name() + "." + type.name(), String.valueOf(widthSlider.getLowValue()));
    TmmProperties.getInstance()
        .putProperty("imagechooser.maxwidth." + mediaType.name() + "." + type.name(), String.valueOf(widthSlider.getHighValue()));
    TmmProperties.getInstance()
        .putProperty("imagechooser.minheight." + mediaType.name() + "." + type.name(), String.valueOf(heightSlider.getLowValue()));
    TmmProperties.getInstance()
        .putProperty("imagechooser.maxheight." + mediaType.name() + "." + type.name(), String.valueOf(heightSlider.getHighValue()));
    TmmProperties.getInstance()
        .putProperty("imagechooser.sortorder." + mediaType.name() + "." + type.name(), ((SortOrder) cbSortOrder.getSelectedItem()).name());
  }

  /**
   * call a new image chooser dialog without extrathumbs and extrafanart usage.<br />
   * this method also checks if there are valid IDs for scraping
   *
   * @param parent
   *          the parent of this dialog
   * @param ids
   *          the ids
   * @param type
   *          the type
   * @param artworkScrapers
   *          the artwork providers
   * @param mediaType
   *          the media for which artwork has to be chosen
   * @param defaultPath
   *          the default path to open
   */
  public static String chooseImage(JDialog parent, final Map<String, Object> ids, MediaArtworkType type, List<MediaScraper> artworkScrapers,
      MediaType mediaType, String defaultPath) {
    return chooseImage(parent, ids, type, artworkScrapers, null, null, mediaType, defaultPath);
  }

  /**
   * call a new image chooser dialog with extrathumbs and extrafanart usage.<br />
   * this method also checks if there are valid IDs for scraping
   *
   * @param parent
   *          the parent of this dialog
   * @param ids
   *          the ids
   * @param type
   *          the type
   * @param artworkScrapers
   *          the artwork providers
   * @param extraThumbs
   *          the extra thumbs
   * @param extraFanarts
   *          the extra fanarts
   * @param mediaType
   *          the media for which artwork has to be chosen
   * @param defaultPath
   *          the default path to open
   */
  public static String chooseImage(JDialog parent, final Map<String, Object> ids, MediaArtworkType type, List<MediaScraper> artworkScrapers,
      List<String> extraThumbs, List<String> extraFanarts, MediaType mediaType, String defaultPath) {
    if (ids.isEmpty()) {
      return "";
    }

    ImageLabel lblImage = new ImageLabel();
    ImageChooserDialog dialog = new ImageChooserDialog(parent, ids, type, artworkScrapers, lblImage, mediaType);

    dialog.bindExtraThumbs(extraThumbs);
    dialog.bindExtraFanarts(extraFanarts);
    dialog.setOpenFolderPath(defaultPath);

    dialog.setLocationRelativeTo(MainWindow.getInstance());
    dialog.startScraping();
    dialog.setVisible(true);
    return lblImage.getImageUrl();
  }

  /**
   * call a new image chooser dialog with extrathumbs and extrafanart usage.<br />
   * this method also checks if there are valid IDs for scraping
   *
   * @param parent
   *          the parent of this dialog
   * @param ids
   *          the ids
   * @param type
   *          the type private List<MediaArtwork> artwork;
   * @param artworkScrapers
   *          the artwork providers
   * @param extraThumbs
   *          the extra thumbs
   * @param extraFanarts
   *          the extra fanarts
   * @param artwork
   *          all pre-selected artwork to show
   * @param mediaType
   *          the media for which artwork has to be chosen
   * @param defaultPath
   *          the default path to open
   */
  public static String chooseImage(JDialog parent, final Map<String, Object> ids, MediaArtworkType type, List<MediaScraper> artworkScrapers,
      List<String> extraThumbs, List<String> extraFanarts, List<MediaArtwork> artwork, MediaType mediaType, String defaultPath) {
    if (ids.isEmpty() || ListUtils.isEmpty(artwork)) {
      return "";
    }

    List<MediaArtwork> filteredArtwork = artwork.stream().filter(mediaArtwork -> mediaArtwork.getType() == type).toList();

    ImageLabel lblImage = new ImageLabel();
    ImageChooserDialog dialog = new ImageChooserDialog(parent, ids, type, artworkScrapers, lblImage, mediaType);

    dialog.setArtwork(filteredArtwork);
    dialog.bindExtraThumbs(extraThumbs);
    dialog.bindExtraFanarts(extraFanarts);
    dialog.setOpenFolderPath(defaultPath);

    dialog.setLocationRelativeTo(MainWindow.getInstance());
    dialog.startScraping();
    dialog.setVisible(true);
    return lblImage.getImageUrl();
  }

  private class OkAction extends AbstractAction {
    public OkAction() {
      putValue(NAME, TmmResourceBundle.getString("Button.ok"));
      putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("image.seteselected"));
      putValue(SMALL_ICON, IconManager.APPLY_INV);
      putValue(LARGE_ICON_KEY, IconManager.APPLY_INV);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      MediaArtwork artwork = null;
      ImageSizeAndUrl resolution = null;

      List<String> selectedExtraThumbs = getSelectedExtraThumbs();
      List<String> selectedExtraFanarts = getSelectedExtrafanarts();

      // get selected button
      for (JToggleButton button : buttons) {
        if (button.isSelected()) {
          Object clientProperty = button.getClientProperty("MediaArtwork");
          if (clientProperty instanceof MediaArtwork) {
            artwork = (MediaArtwork) clientProperty;
            clientProperty = button.getClientProperty("MediaArtworkSize");
            // try to get the size
            if (clientProperty instanceof JComboBox) {
              @SuppressWarnings("rawtypes")
              JComboBox cb = (JComboBox) clientProperty;
              if (cb.getSelectedItem() instanceof ImageSizeAndUrl) {
                resolution = (ImageSizeAndUrl) cb.getSelectedItem();
              }
            }
            break;
          }
        }
      }

      // nothing selected
      if (artwork == null && selectedExtraFanarts.isEmpty() && selectedExtraThumbs.isEmpty()) {
        JOptionPane.showMessageDialog(ImageChooserDialog.this, TmmResourceBundle.getString("image.noneselected"));
        return;
      }

      if (artwork != null) {
        imageLabel.clearImage();
        if (resolution != null) {
          imageLabel.setImageUrl(resolution.getUrl());
        }
        else {
          imageLabel.setImageUrl(artwork.getOriginalUrl());
        }
      }

      // extrathumbs
      if (extraThumbs != null) {
        extraThumbs.clear();
        extraThumbs.addAll(selectedExtraThumbs);
      }

      // extrafanart
      if (extraFanarts != null) {
        extraFanarts.clear();
        extraFanarts.addAll(selectedExtraFanarts);
      }

      task.cancel(true);

      if (persistFilters) {
        // save current filters
        persistFilters();
      }

      setVisible(false);
    }

    /**
     * Process extra thumbs.
     */
    private List<String> getSelectedExtraThumbs() {
      List<String> selectedExtraThumbs = new ArrayList<>();

      // get extrathumbs
      for (JToggleButton button : buttons) {
        if (button.getClientProperty("MediaArtworkExtrathumb") instanceof JCheckBox
            && button.getClientProperty("MediaArtwork") instanceof MediaArtwork
            && button.getClientProperty("MediaArtworkSize") instanceof JComboBox) {
          JCheckBox chkbx = (JCheckBox) button.getClientProperty("MediaArtworkExtrathumb");
          if (chkbx.isSelected()) {
            MediaArtwork artwork = (MediaArtwork) button.getClientProperty("MediaArtwork");
            @SuppressWarnings("rawtypes")
            JComboBox cb = (JComboBox) button.getClientProperty("MediaArtworkSize");
            if (cb.getSelectedItem() instanceof ImageSizeAndUrl size) {
              selectedExtraThumbs.add(size.getUrl());
            }
            else if (cb.getSelectedItem() instanceof String) {
              selectedExtraThumbs.add(artwork.getOriginalUrl());
            }
          }
        }
      }

      return selectedExtraThumbs;
    }

    /**
     * Process extra fanart.
     */
    private List<String> getSelectedExtrafanarts() {
      List<String> selectedExtrafanarts = new ArrayList<>();

      // get extrafanart
      for (JToggleButton button : buttons) {
        if (button.getClientProperty("MediaArtworkExtrafanart") instanceof JCheckBox
            && button.getClientProperty("MediaArtwork") instanceof MediaArtwork
            && button.getClientProperty("MediaArtworkSize") instanceof JComboBox) {
          JCheckBox chkbx = (JCheckBox) button.getClientProperty("MediaArtworkExtrafanart");
          if (chkbx.isSelected()) {
            MediaArtwork artwork = (MediaArtwork) button.getClientProperty("MediaArtwork");
            @SuppressWarnings("rawtypes")
            JComboBox cb = (JComboBox) button.getClientProperty("MediaArtworkSize");
            if (cb.getSelectedItem() instanceof ImageSizeAndUrl size) {
              selectedExtrafanarts.add(size.getUrl());
            }
            else if (cb.getSelectedItem() instanceof String) {
              selectedExtrafanarts.add(artwork.getOriginalUrl());
            }
          }
        }
      }
      return selectedExtrafanarts;
    }
  }

  private class CancelAction extends AbstractAction {
    public CancelAction() {
      putValue(NAME, TmmResourceBundle.getString("Button.cancel"));
      putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("Button.cancel"));
      putValue(SMALL_ICON, IconManager.CANCEL_INV);
      putValue(LARGE_ICON_KEY, IconManager.CANCEL_INV);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      task.cancel(true);
      setVisible(false);
    }
  }

  private class DownloadTask extends SwingWorker<Void, DownloadChunk> {
    private final Map<String, Object>                ids;
    private final List<MediaScraper>                 artworkScrapers;
    private final List<MediaArtwork>                 prescrapedArtwork;

    private boolean                                  imagesFound = false;
    private ExecutorCompletionService<DownloadChunk> service;

    public DownloadTask(Map<String, Object> ids, List<MediaScraper> artworkScrapers, List<MediaArtwork> artwork) {
      this.ids = ids;
      this.artworkScrapers = artworkScrapers;
      this.prescrapedArtwork = artwork;
    }

    @Override
    public Void doInBackground() {
      if (ids.isEmpty()) {
        JOptionPane.showMessageDialog(ImageChooserDialog.this, TmmResourceBundle.getString("image.download.noid"));
        return null;
      }

      SwingUtilities.invokeLater(() -> startProgressBar(TmmResourceBundle.getString("image.download.progress")));

      if (ListUtils.isEmpty(artworkScrapers) && ListUtils.isEmpty(prescrapedArtwork)) {
        return null;
      }

      // open a thread pool to parallel download the images
      ThreadPoolExecutor pool = new ThreadPoolExecutor(5, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
      pool.allowCoreThreadTimeOut(true);
      service = new ExecutorCompletionService<>(pool);

      if (ListUtils.isEmpty(prescrapedArtwork)) {
        // get images from all artwork providers
        for (MediaScraper scraper : artworkScrapers) {
          try {
            IMediaArtworkProvider artworkProvider = (IMediaArtworkProvider) scraper.getMediaProvider();

            ArtworkSearchAndScrapeOptions options = new ArtworkSearchAndScrapeOptions(mediaType);
            if (mediaType == MediaType.MOVIE || mediaType == MediaType.MOVIE_SET) {
              options.setLanguage(MovieModuleManager.getInstance().getSettings().getDefaultImageScraperLanguage());
              options.setFanartSize(MovieModuleManager.getInstance().getSettings().getImageFanartSize());
              options.setPosterSize(MovieModuleManager.getInstance().getSettings().getImagePosterSize());
            }
            else if (mediaType == MediaType.TV_SHOW || mediaType == MediaType.TV_EPISODE) {
              options.setLanguage(TvShowModuleManager.getInstance().getSettings().getScraperLanguage());
              options.setFanartSize(TvShowModuleManager.getInstance().getSettings().getImageFanartSize());
              options.setPosterSize(TvShowModuleManager.getInstance().getSettings().getImagePosterSize());
              options.setThumbSize(TvShowModuleManager.getInstance().getSettings().getImageThumbSize());
            }
            else {
              continue;
            }

            switch (type) {
              case POSTER:
                options.setArtworkType(MediaArtworkType.POSTER);
                break;

              case BACKGROUND:
                options.setArtworkType(BACKGROUND);
                break;

              case BANNER:
                options.setArtworkType(MediaArtworkType.BANNER);
                break;

              case SEASON_POSTER:
                options.setArtworkType(MediaArtworkType.SEASON_POSTER);
                break;

              case SEASON_FANART:
                options.setArtworkType(MediaArtworkType.SEASON_FANART);
                break;

              case SEASON_BANNER:
                options.setArtworkType(MediaArtworkType.SEASON_BANNER);
                break;

              case SEASON_THUMB:
                options.setArtworkType(MediaArtworkType.SEASON_THUMB);
                break;

              case CLEARART:
                options.setArtworkType(MediaArtworkType.CLEARART);
                break;

              case DISC:
                options.setArtworkType(MediaArtworkType.DISC);
                break;

              case CLEARLOGO:
              case LOGO:
                options.setArtworkType(MediaArtworkType.CLEARLOGO);
                break;

              case CHARACTERART:
                options.setArtworkType(MediaArtworkType.CHARACTERART);
                break;

              case KEYART:
                options.setArtworkType(MediaArtworkType.KEYART);
                break;

              case THUMB:
                options.setArtworkType(MediaArtworkType.THUMB);
                break;
            }

            // populate ids
            options.setIds(ids);

            // get the artwork
            List<MediaArtwork> artwork = artworkProvider.getArtwork(options);
            if (artwork == null || artwork.isEmpty()) {
              continue;
            }

            processImages(artwork);
            if (isCancelled()) {
              return null;
            }
          }
          catch (MissingIdException e) {
            LOGGER.debug("could not fetch artwork: {}", e.getIds());
          }
          catch (ScrapeException e) {
            LOGGER.error("getArtwork", e);
          }
          catch (Exception e) {
            if (e instanceof InterruptedException || e instanceof InterruptedIOException) { // NOSONAR
              // shutdown the pool
              pool.getQueue().clear();
              pool.shutdownNow();

              return null;
            }
            LOGGER.error("Could not process artwork downloading - '{}'", e.getMessage());
          }
        } // end foreach scraper
      }
      else {
        // add pre-scraped artwork
        processImages(prescrapedArtwork);
      }

      // wait for all downloads to finish
      pool.shutdown();
      while (true) {
        try {
          final Future<DownloadChunk> future = service.poll(1, TimeUnit.SECONDS);
          if (future != null) {
            DownloadChunk dc = future.get();
            if (dc.imageData.length > 0) {
              publish(dc);
              imagesFound = true;
            }
          }
          else if (pool.isTerminated()) {
            // no result got and the pool is terminated -> we're finished
            break;
          }
        }
        catch (InterruptedException e) { // NOSONAR
          return null;
        }
        catch (ExecutionException e) {
          LOGGER.error("ThreadPool imageChooser: Error getting result! - '{}'", e.getMessage());
        }
      }

      return null;
    }

    private void processImages(List<MediaArtwork> artwork) {
      int season = MediaIdUtil.getIdAsIntOrDefault(ids, "tvShowSeason", -1);

      // display all images
      for (MediaArtwork art : artwork) {
        if (isCancelled()) {
          return;
        }
        if (art.getPreviewUrl().isEmpty()) {
          continue;
        }

        // for seasons, just use the season related artwork
        if (season > -1 && season != art.getSeason()) {
          continue;
        }

        Callable<DownloadChunk> callable = () -> {
          Url url = new Url(art.getPreviewUrl());
          DownloadChunk chunk = new DownloadChunk();
          chunk.artwork = art;
          try {
            chunk.imageData = url.getBytesWithRetry(5);
          }
          catch (Exception e) {
            LOGGER.debug("Could not add image '{}' - '{}'", art.getPreviewUrl(), e.getMessage());
          }
          return chunk;
        };

        service.submit(callable);
      }
    }

    @Override
    protected void process(List<DownloadChunk> chunks) {
      for (DownloadChunk chunk : chunks) {
        try {
          addImage(chunk.imageData, chunk.artwork);
        }
        catch (Exception e) {
          LOGGER.debug("Could not add image '{}' - '{}'", chunk.artwork.getPreviewUrl(), e.getMessage());
        }
      }
    }

    @Override
    public void done() {
      if (!imagesFound) {
        JLabel lblNothingFound = new JLabel(TmmResourceBundle.getString("image.download.nothingfound"));
        TmmFontHelper.changeFont(lblNothingFound, 1.33);
        panelImages.add(lblNothingFound);
        panelImages.validate();
        panelImages.getParent().validate();
      }
      SwingUtilities.invokeLater(ImageChooserDialog.this::stopProgressBar);
    }
  }

  private static class DownloadChunk {
    private byte[]       imageData;
    private MediaArtwork artwork;
  }

  private class LocalFileChooseAction extends AbstractAction {
    public LocalFileChooseAction() {
      putValue(NAME, TmmResourceBundle.getString("image.choose.file"));
      putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("image.choose.file"));
      putValue(SMALL_ICON, IconManager.FILE_OPEN_INV);
      putValue(LARGE_ICON_KEY, IconManager.FILE_OPEN_INV);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      String path;
      if (StringUtils.isNotBlank(openFolderPath)) {
        path = openFolderPath;
      }
      else {
        path = TmmProperties.getInstance().getProperty(DIALOG_ID + ".path");
      }

      Path file = TmmUIHelper.selectFile(TmmResourceBundle.getString("image.choose"), path,
          new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "bmp", "gif", "tbn", "webp"));
      if (file != null && Utils.isRegularFile(file)) {
        imageLabel.clearImage();
        imageLabel.setImageUrl(file.toAbsolutePath().toUri().toString());
        task.cancel(true);
        TmmProperties.getInstance().putProperty(DIALOG_ID + ".path", file.getParent().toString());
        setVisible(false);
      }
    }
  }

  private static final class LockableViewPort extends JViewport {

    private boolean locked = false;

    @Override
    public void setViewPosition(Point p) {
      if (locked) {
        return;
      }
      super.setViewPosition(p);
    }

    public boolean isLocked() {
      return locked;
    }

    public void setLocked(boolean locked) {
      this.locked = locked;
    }
  }

  private static class NoneSelectedButtonGroup extends ButtonGroup {

    @Override
    public void setSelected(ButtonModel model, boolean selected) {
      if (selected) {
        super.setSelected(model, selected);
      }
      else {
        clearSelection();
      }
    }
  }

  private record ImageSize(int width, int height) implements Comparable<ImageSize> {
    @Override
    public int compareTo(ImageSize o) {
      if (width == o.width && height == o.height) {
        return 0;
      }
      if (width < o.width || (width == o.width && height < o.height)) {
        return -1;
      }
      return 1;
    }

    @NotNull
    @Override
    public String toString() {
      return width + "x" + height;
    }
  }

  /**
   * Configure major/minor tick marks and labels on a RangeSlider depending on artwork type.
   *
   * @param slider
   *          the RangeSlider to configure
   * @param isWidth
   *          true to configure width ticks; false for height
   */
  private void configureSliderTicks(RangeSlider slider, boolean isWidth) {
    // ensure ticks are painted
    slider.setPaintTicks(true);
    slider.setPaintLabels(true);
    slider.setSnapToTicks(true);

    // select well-known sizes based on type
    int[] majors = getWellKnownSizes(isWidth);

    // build label table within current slider min/max
    Hashtable<Integer, JComponent> table = new Hashtable<>();
    int min = slider.getMinimum();
    int max = slider.getMaximum();

    List<Integer> majorsInRange = new ArrayList<>();
    for (int v : majors) {
      if (v >= min && v <= max && !majorsInRange.contains(v)) { // dedupe
        majorsInRange.add(v);
      }
    }

    List<Integer> filteredMajors = new ArrayList<>();
    // If only a few presets are visible, keep them all to avoid hiding common sizes (e.g. 1080 for fanart)
    if (majorsInRange.size() <= 6) {
      filteredMajors.addAll(majorsInRange);
    }
    else {
      // downsample labels to avoid overlap when the range is much larger than the presets
      double minSeparation = 0.18d; // normalized spacing
      double lastNorm = -1d;
      for (int v : majorsInRange) {
        double norm = (double) (v - min) / Math.max(1d, max - min);
        if (lastNorm < 0 || norm - lastNorm >= minSeparation) {
          filteredMajors.add(v);
          lastNorm = norm;
        }
      }
      // always try to keep the largest preset label if available and not overlapping
      if (!majorsInRange.isEmpty()) {
        int last = majorsInRange.get(majorsInRange.size() - 1);
        double normLast = (double) (last - min) / Math.max(1d, max - min);
        double normPrev = filteredMajors.isEmpty() ? -1d : (double) (filteredMajors.get(filteredMajors.size() - 1) - min) / Math.max(1d, max - min);
        if (filteredMajors.isEmpty() || normLast - normPrev >= minSeparation) {
          if (!filteredMajors.contains(last)) {
            filteredMajors.add(last);
          }
        }
      }
    }

    for (int v : filteredMajors) {
      table.put(v, new JLabel(String.valueOf(v)));
    }

    // set a reasonable major/minor spacing based on range and cap tick density
    int range = Math.max(1, max - min);
    int majorSpacing;
    int minorSpacing;

    if (majors.length >= 2) {
      int step = Math.abs(majors[1] - majors[0]);
      majorSpacing = Math.max(50, step);
    }
    else {
      majorSpacing = Math.max(100, range / 5);
    }
    // limit to ~12 major ticks across the range to reduce clutter
    int maxMajorTicks = 12;
    majorSpacing = Math.max(majorSpacing, (int) Math.ceil(range / (double) maxMajorTicks));

    // Calculate minor spacing - provide 3-5 minor ticks between major ticks for better granularity
    if (majorSpacing > 0) {
      minorSpacing = Math.max(10, majorSpacing / 5);
    }
    else {
      minorSpacing = Math.max(20, range / 20);
    }

    slider.setMajorTickSpacing(majorSpacing);
    slider.setMinorTickSpacing(minorSpacing);
    slider.setLabelTable(table);
  }

  /**
   * Returns an array of well known sizes for the current artwork type (TMDb/fanart.tv conventions).
   *
   * @param isWidth
   *          true to return width values; false to return height values
   * @return int[] of sizes
   */
  private int[] getWellKnownSizes(boolean isWidth) {
    // Defaults if type is unknown
    int[] fallback = isWidth ? new int[] { 640, 1280, 1920, 3840 } : new int[] { 360, 720, 1080, 2160 };

    switch (type) {
      case POSTER:
      case SEASON_POSTER:
      case KEYART:
        // TMDb posters: typically 500x750, 1000x1500, 2000x3000
        return isWidth ? new int[] { 500, 1000, 2000 } : new int[] { 750, 1500, 3000 };

      case BACKGROUND:
      case SEASON_FANART:
        // Backdrops/Fanart: 1280x720, 1920x1080, 3840x2160
        return isWidth ? new int[] { 1280, 1920, 3840 } : new int[] { 720, 1080, 2160 };

      case THUMB:
      case SEASON_THUMB:
        // Thumbs: vary widely; use practical steps
        return isWidth ? new int[] { 320, 640, 1280 } : new int[] { 180, 360, 720 };

      case BANNER:
      case SEASON_BANNER:
        // Banners often 1000x185 or 1920x360
        return isWidth ? new int[] { 1000, 1920 } : new int[] { 185, 360 };

      case CLEARLOGO:
      case LOGO:
        // Logos commonly around 800x310
        return isWidth ? new int[] { 800 } : new int[] { 310 };

      case CLEARART:
      case CHARACTERART:
      case DISC:
        // Artwork types with flexible sizes; provide broader ranges
        return isWidth ? new int[] { 1000, 1500, 2000 } : new int[] { 1000, 1500, 2000 };

      default:
        return fallback;
    }
  }

  /**
   * Suggested maximum value for slider: slightly above the well-known max for type, but not lower than current dynamic sizes.
   *
   * @param isWidth
   *          true for width, false for height
   * @return suggested max value
   */
  private int getSuggestedMax(boolean isWidth) {
    int[] known = getWellKnownSizes(isWidth);
    int knownMax = 0;
    for (int v : known) {
      if (v > knownMax) {
        knownMax = v;
      }
    }
    // add small headroom: 3840->4000; 3000->3100; else +100
    if (knownMax >= 3840) {
      return 4000;
    }
    if (knownMax >= 3000) {
      return 3100;
    }
    return knownMax + 100;
  }
}

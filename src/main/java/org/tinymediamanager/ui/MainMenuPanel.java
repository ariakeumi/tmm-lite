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
package org.tinymediamanager.ui;

import static org.tinymediamanager.ui.TmmUIHelper.openFolder;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.TinyMediaManager;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TagManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.WolDevice;
import org.tinymediamanager.license.License;
import org.tinymediamanager.thirdparty.KodiRPC;
import org.tinymediamanager.ui.actions.AboutAction;
import org.tinymediamanager.ui.actions.BugReportAction;
import org.tinymediamanager.ui.actions.CheckForUpdateAction;
import org.tinymediamanager.ui.actions.CleanupImageCacheAction;
import org.tinymediamanager.ui.actions.ClearDatabaseAction;
import org.tinymediamanager.ui.actions.ClearHttpCacheAction;
import org.tinymediamanager.ui.actions.ClearImageCacheAction;
import org.tinymediamanager.ui.actions.ClearSimklSyncStateAction;
import org.tinymediamanager.ui.actions.CloseTmmAction;
import org.tinymediamanager.ui.actions.CreateDesktopFileAction;
import org.tinymediamanager.ui.actions.DeleteTrashAction;
import org.tinymediamanager.ui.actions.DocsAction;
import org.tinymediamanager.ui.actions.ExportAnalysisDataAction;
import org.tinymediamanager.ui.actions.ExportLogAction;
import org.tinymediamanager.ui.actions.FaqAction;
import org.tinymediamanager.ui.actions.ForumAction;
import org.tinymediamanager.ui.actions.HomepageAction;
import org.tinymediamanager.ui.actions.ImportV4DataAction;
import org.tinymediamanager.ui.actions.RebuildImageCacheAction;
import org.tinymediamanager.ui.actions.ShowChangelogAction;
import org.tinymediamanager.ui.actions.UnlockAction;
import org.tinymediamanager.ui.components.toolbar.ToolbarButton;
import org.tinymediamanager.ui.dialogs.ActivityLogDialog;
import org.tinymediamanager.ui.dialogs.FullLogDialog;
import org.tinymediamanager.ui.dialogs.MessageHistoryDialog;
import org.tinymediamanager.ui.dialogs.SettingsDialog;
import org.tinymediamanager.ui.dialogs.TagManagerDialog;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.movies.dialogs.MovieJmteExplorerDialog;
import org.tinymediamanager.ui.thirdparty.KodiRPCMenu;
import org.tinymediamanager.ui.tvshows.dialogs.TvShowJmteExplorerDialog;

import net.miginfocom.swing.MigLayout;

/**
 * this class is used to draw the main panel on the left hand side with the same black top bar as the main tabbed pane
 * 
 * @author Manuel Laggner
 */
public class MainMenuPanel extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(MainMenuPanel.class);

  private final JPanel        contentPanel;
  private final ButtonGroup   buttons;

  private final JPopupMenu    menuTools;
  private final JPopupMenu    menuInfo;

  private final JButton       btnTools;
  private final JButton       btnSettings;

  public MainMenuPanel() {
    buttons = new ButtonGroup();
    setLayout(new BorderLayout());

    contentPanel = new JPanel(new MigLayout("insets 0, gapy 15lp, wrap", "[center]", "32lp[]"));
    contentPanel.setOpaque(false);
    add(contentPanel, BorderLayout.CENTER);

    JPanel bottomPanel = new JPanel(new MigLayout("insets 0, gapy 15lp, wrap", "[center, grow]", "[]10lp"));
    bottomPanel.setOpaque(false);
    add(bottomPanel, BorderLayout.SOUTH);

    menuTools = buildToolsMenu();

    btnTools = new ToolbarButton(IconManager.TOOLBAR_TOOLS, IconManager.TOOLBAR_TOOLS_HOVER, menuTools);
    btnTools.setToolTipText(TmmResourceBundle.getString("Toolbar.tools"));
    bottomPanel.add(btnTools, "growx");

    btnSettings = new ToolbarButton(IconManager.TOOLBAR_SETTINGS, IconManager.TOOLBAR_SETTINGS_HOVER);
    btnSettings.setToolTipText(TmmResourceBundle.getString("Toolbar.settings"));
    btnSettings.addActionListener(listener -> {
      JDialog settingsDialog = SettingsDialog.getInstance();
      settingsDialog.setVisible(true);
    });
    bottomPanel.add(btnSettings, "growx");

    menuInfo = buildInfoMenu();
    JButton btnInfo = new ToolbarButton(IconManager.TOOLBAR_ABOUT, IconManager.TOOLBAR_ABOUT_HOVER, menuInfo);
    btnInfo.setToolTipText(TmmResourceBundle.getString("Toolbar.help"));
    bottomPanel.add(btnInfo, "growx");
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setBackground(UIManager.getColor("Tmm.toolbar.background"));
  }

  public void addModule(ITmmUIModule module) {
    JToggleButton btnModule = new ModuleButton(module);
    btnModule.setToolTipText(module.getTabTitle());
    contentPanel.add(btnModule, "gapx 1lp 1lp");

    buttons.add(btnModule);

    // select movies per default on startup
    if (module instanceof MovieUIModule) {
      btnModule.setSelected(true);

      // add the hint for this part
      HintManager.getInstance().addHint(TmmResourceBundle.getString("hintmanager.module"), btnModule, SwingConstants.RIGHT);
    }
  }

  public JPopupMenu getToolsMenu() {
    return menuTools;
  }

  public JPopupMenu getInfoMenu() {
    return menuInfo;
  }

  private JPopupMenu buildToolsMenu() {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem tmmLogs = new JMenuItem(TmmResourceBundle.getString("tmm.activitylog"));
    menu.add(tmmLogs);
    tmmLogs.addActionListener(arg0 -> {
      JDialog logDialog = new ActivityLogDialog();
      logDialog.setLocationRelativeTo(MainWindow.getInstance());
      logDialog.setVisible(true);
    });

    JMenuItem tmmMessages = new JMenuItem(TmmResourceBundle.getString("tmm.messages"));
    tmmMessages.setMnemonic(KeyEvent.VK_L);
    menu.add(tmmMessages);
    tmmMessages.addActionListener(arg0 -> {
      JDialog messageDialog = MessageHistoryDialog.getInstance();
      messageDialog.setVisible(true);
    });

    JMenuItem tmmFolder = new JMenuItem(TmmResourceBundle.getString("tmm.gotoinstalldir"));
    menu.add(tmmFolder);
    tmmFolder.setToolTipText(TmmResourceBundle.getString("tmm.gotoinstalldir.desc"));
    tmmFolder.addActionListener(arg0 -> openFolder(Paths.get(System.getProperty("user.dir"))));

    JMenuItem contentFolder = new JMenuItem(TmmResourceBundle.getString("tmm.gotocontentdir"));
    menu.add(contentFolder);
    contentFolder.setToolTipText(TmmResourceBundle.getString("tmm.gotocontentdir.desc"));
    contentFolder.addActionListener(arg0 -> openFolder(Paths.get(Globals.CONTENT_FOLDER)));

    JMenuItem dataFolder = new JMenuItem(TmmResourceBundle.getString("tmm.gotodatadir"));
    menu.add(dataFolder);
    dataFolder.setToolTipText(TmmResourceBundle.getString("tmm.gotodatadir.desc"));
    dataFolder.addActionListener(arg0 -> openFolder(Paths.get(Globals.DATA_FOLDER)));

    JMenuItem logFolder = new JMenuItem(TmmResourceBundle.getString("tmm.gotologdir"));
    menu.add(logFolder);
    logFolder.setToolTipText(TmmResourceBundle.getString("tmm.gotologdir.desc"));
    logFolder.addActionListener(arg0 -> openFolder(Paths.get(Globals.LOG_FOLDER)));

    JMenuItem tmpFolder = new JMenuItem(TmmResourceBundle.getString("tmm.gototmpdir"));
    menu.add(tmpFolder);
    tmpFolder.addActionListener(arg0 -> openFolder(Paths.get(Utils.getTempFolder())));

    menu.add(new DeleteTrashAction());
    menu.addSeparator();

    final JMenu menuWakeOnLan = new JMenu(TmmResourceBundle.getString("tmm.wakeonlan"));
    menuWakeOnLan.setMnemonic(KeyEvent.VK_W);
    menuWakeOnLan.addMenuListener(new TmmLazyMenuAdapter() {
      @Override
      protected void menuWillBecomeVisible(JMenu menu) {
        menu.removeAll();
        for (final WolDevice device : Settings.getInstance().getWolDevices()) {
          JMenuItem item = new JMenuItem(device.getName());
          item.addActionListener(arg01 -> Utils.sendWakeOnLanPacket(device.getMacAddress()));
          menu.add(item);
        }
      }
    });
    menu.add(menuWakeOnLan);

    final JMenu kodiRPCMenu = KodiRPCMenu.createKodiMenuTop();
    menu.add(kodiRPCMenu);

    // activate/deactivate menu items based on some status
    menu.addPopupMenuListener(new TmmLazyMenuAdapter() {
      @Override
      protected void menuWillBecomeVisible(JMenu menu) {
        if (!Settings.getInstance().getWolDevices().isEmpty()) {
          menu.setEnabled(true);
        }
        else {
          menu.setEnabled(false);
        }

        JMenu kodiRPCMenu = null;
        for (Component comp : menu.getMenuComponents()) {
          if (comp instanceof JMenu subMenu && subMenu.getIcon() == IconManager.KODI) {
            kodiRPCMenu = subMenu;
            break;
          }
        }

        if (kodiRPCMenu != null) {
          if (KodiRPC.getInstance().isConnected()) {
            kodiRPCMenu.setText(KodiRPC.getInstance().getVersion());
            kodiRPCMenu.setEnabled(true);
          }
          else {
            kodiRPCMenu.setText("Kodi");
            kodiRPCMenu.setEnabled(false);
          }
        }
      }
    });

    {
      menu.addSeparator();
      JMenu jmteExplorerMenu = new JMenu(TmmResourceBundle.getString("jmteexplorer.title"));
      JMenuItem movieJmteExplorer = new JMenuItem(TmmResourceBundle.getString("tmm.movies"));
      movieJmteExplorer.addActionListener(arg0 -> new MovieJmteExplorerDialog(MainWindow.getInstance()).setVisible(true));
      jmteExplorerMenu.add(movieJmteExplorer);

      JMenuItem tvShowJmteExplorer = new JMenuItem(TmmResourceBundle.getString("tmm.tvshows"));
      tvShowJmteExplorer.addActionListener(arg0 -> new TvShowJmteExplorerDialog(MainWindow.getInstance()).setVisible(true));
      jmteExplorerMenu.add(tvShowJmteExplorer);

      menu.add(jmteExplorerMenu);
    }

    {
      JMenu tagManagerMenu = new JMenu(TmmResourceBundle.getString("tagmanager.title"));

      JMenuItem movieTags = new JMenuItem(TmmResourceBundle.getString("tmm.movies"));
      movieTags.addActionListener(arg0 -> new TagManagerDialog(TagManager.forMovies(), TmmResourceBundle.getString("tmm.movies")).setVisible(true));
      tagManagerMenu.add(movieTags);

      JMenuItem tvShowTags = new JMenuItem(TmmResourceBundle.getString("tmm.tvshows"));
      tvShowTags
          .addActionListener(arg0 -> new TagManagerDialog(TagManager.forTvShows(), TmmResourceBundle.getString("tmm.tvshows")).setVisible(true));
      tagManagerMenu.add(tvShowTags);

      JMenuItem episodeTags = new JMenuItem(TmmResourceBundle.getString("metatag.episodes"));
      episodeTags.addActionListener(
          arg0 -> new TagManagerDialog(TagManager.forEpisodes(), TmmResourceBundle.getString("metatag.episodes")).setVisible(true));
      tagManagerMenu.add(episodeTags);

      menu.add(tagManagerMenu);
    }

    if (Globals.canCheckForUpdates() || Globals.isDebug()) {
      menu.addSeparator();
    }

    if (Globals.canCheckForUpdates()) {
      menu.add(new CheckForUpdateAction());
    }

    // debug menu
    if (Globals.isDebug()) {
      final JMenu debugMenu = new JMenu("Debug");

      JMenuItem trace = new JMenuItem("set Console Logger to TRACE");
      trace.addActionListener(arg0 -> {
        System.setProperty("tmm.consoleloglevel", "TRACE");
        TinyMediaManager.setLogLevels();
        MessageManager.getInstance().pushMessage(new Message("Trace levels set!", "Test"));
        LOGGER.trace("if you see that, we're now on TRACE logging level ;)");
      });
      debugMenu.add(trace);

      JMenuItem traceLogs = new JMenuItem("Show all logs from this session");
      debugMenu.add(traceLogs);
      traceLogs.addActionListener(arg0 -> {
        JDialog logDialog = new FullLogDialog();
        logDialog.setLocationRelativeTo(MainWindow.getInstance());
        logDialog.setVisible(true);
      });

      menu.add(debugMenu);
    }

    menu.addSeparator();
    menu.add(new BugReportAction());
    menu.add(new ExportLogAction());
    menu.add(new ExportAnalysisDataAction());

    if (SystemUtils.IS_OS_LINUX) {
      menu.addSeparator();
      menu.add(new CreateDesktopFileAction());
    }

    menu.addSeparator();
    // housekeeping
    JMenu menuHousekeeping = new JMenu(TmmResourceBundle.getString("tmm.housekeeping"));
    menuHousekeeping.add(new CleanupImageCacheAction());
    menuHousekeeping.add(new ClearImageCacheAction());
    menuHousekeeping.add(new RebuildImageCacheAction());
    menuHousekeeping.add(new ClearHttpCacheAction());
    menuHousekeeping.addSeparator();
    menuHousekeeping.add(new ClearSimklSyncStateAction());

    menu.add(menuHousekeeping);
    menu.add(new ClearDatabaseAction());
    menu.add(new ImportV4DataAction());

    // a dedicated close action because on XWayland sometimes the window decorations are missing
    if (SystemUtils.IS_OS_LINUX) {
      menu.addSeparator();
      menu.add(new CloseTmmAction());
    }

    return menu;
  }

  void addHints() {
    HintManager.getInstance().addHint(TmmResourceBundle.getString("hintmanager.tools"), btnTools, SwingConstants.RIGHT);
    HintManager.getInstance().addHint(TmmResourceBundle.getString("hintmanager.settings"), btnSettings, SwingConstants.RIGHT);
  }

  private JPopupMenu buildInfoMenu() {
    JPopupMenu menu = new JPopupMenu();

    menu.add(new FaqAction());
    menu.add(new DocsAction());
    menu.add(new ForumAction());
    menu.add(new ShowChangelogAction());
    menu.addSeparator();

    menu.add(new BugReportAction());

    menu.addSeparator();
    if (!License.getInstance().isValidLicense()) {
      menu.add(new UnlockAction());
    }
    menu.add(new HomepageAction());
    menu.add(new AboutAction());

    return menu;
  }

  private static class ModuleButton extends JToggleButton {
    private final Icon icon;
    private final Icon activeIcon;

    private ModuleButton(ITmmUIModule module) {
      super(module.getMenuIcon());
      this.icon = module.getMenuIcon();
      this.activeIcon = module.getMenuActiveIcon();
      putClientProperty("tmm.module", module);

      init();
    }

    private void init() {
      setOpaque(true);
      setContentAreaFilled(false);

      addItemListener(l -> {
        if (isSelected()) {
          setIcon(activeIcon);
          MainWindow.getInstance().setActiveModule((ITmmUIModule) getClientProperty("tmm.module"));
        }
        else {
          setIcon(icon);
        }
      });
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
      });
    }
  }
}

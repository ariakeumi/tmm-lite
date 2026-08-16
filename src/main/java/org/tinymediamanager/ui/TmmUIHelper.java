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

import java.awt.Desktop;
import java.awt.Font;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.ReleaseInfo;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.TmmStore;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.thirdparty.TinyFileDialogs;
import org.tinymediamanager.ui.components.label.ImageLabel;
import org.tinymediamanager.ui.components.label.LinkLabel;
import org.tinymediamanager.ui.dialogs.ImagePreviewDialog;
import org.tinymediamanager.ui.dialogs.UpdateDialog;
import org.tinymediamanager.ui.plaf.dark.TmmDarkLaf;
import org.tinymediamanager.ui.plaf.light.TmmLightLaf;
import org.tinymediamanager.updater.UpdateCheck;
import org.tinymediamanager.updater.UpdaterTask;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.SystemFileChooser;

/**
 * The Class TmmUIHelper.
 * 
 * @author Manuel Laggner
 */
public class TmmUIHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(TmmUIHelper.class);

  private TmmUIHelper() {
    throw new IllegalAccessError();
  }

  public static void setLookAndFeel() {
    try {
      LOGGER.trace("load theme");

      TmmUIHelper.setTheme();
      // decrease the tooltip timeout
      ToolTipManager.sharedInstance().setInitialDelay(300);
    }
    catch (Exception e) {
      LOGGER.error("Failed to initialize LaF - '{}'", e.getMessage());
    }

    // load font settings
    try {
      LOGGER.trace("load font settings");

      // sanity check
      Font font = Font.decode(Settings.getInstance().getFontFamily());
      FontUIResource savedFont = new FontUIResource(font.getFamily(), font.getStyle(), Settings.getInstance().getFontSize());

      UIManager.put("defaultFont", savedFont);
    }
    catch (Exception e) {
      LOGGER.warn("Could not set default font '{}' - '{}'", Settings.getInstance().getFontFamily(), e.getMessage());
    }
  }

  public static Path selectDirectory(String title, String initialPath) {
    // are we forced to open the legacy file chooser?
    if ("true".equalsIgnoreCase(System.getProperty("tmm.legacy.filechooser"))) {
      return openJFileChooser(JFileChooser.DIRECTORIES_ONLY, title, initialPath, true, null, null);
    }

    // check if the initialPath is accessible
    if (StringUtils.isBlank(initialPath) || !Files.exists(Paths.get(initialPath))) {
      initialPath = System.getProperty("user.home");
    }

    Path initialDir = Paths.get(initialPath);

    if (SystemUtils.IS_OS_LINUX) {
      // try to open with tinyfiledialogs
      try {
        return new TinyFileDialogs().chooseDirectory(title, initialDir);
      }
      catch (Exception | Error e) {
        LOGGER.error("Could not call TinyFileDialogs - '{}'", e.getMessage());
      }
    }

    // try with FlatLaf native file chooser
    try {
      SystemFileChooser fc = new SystemFileChooser();
      fc.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
      fc.setCurrentDirectory(initialDir.toFile());
      if (fc.showOpenDialog(null) == SystemFileChooser.APPROVE_OPTION) {
        File directory = fc.getSelectedFile();
        return directory.toPath();
      }
      else {
        return null;
      }
    }
    catch (Exception | Error e) {
      LOGGER.error("Could not call FlatFlaf SystemFileChooser - '{}'", e.getMessage());
    }

    // open JFileChooser
    return openJFileChooser(JFileChooser.DIRECTORIES_ONLY, title, initialPath, true, null, null);
  }

  private static Path openJFileChooser(int mode, String dialogTitle, String initialPath, boolean open, String filename,
      FileNameExtensionFilter filter) {
    JFileChooser fileChooser = null;

    if (StringUtils.isNotBlank(initialPath)) {
      Path path = Paths.get(initialPath);
      if (Files.exists(path)) {
        fileChooser = new JFileChooser(path.toFile());
      }
    }

    if (fileChooser == null) {
      fileChooser = new JFileChooser();
    }

    fileChooser.setFileSelectionMode(mode);
    fileChooser.setDialogTitle(dialogTitle);

    int result = -1;
    if (open) {
      fileChooser.setFileFilter(filter);
      result = fileChooser.showOpenDialog(MainWindow.getFrame());
    }
    else {
      if (StringUtils.isNotBlank(filename)) {
        fileChooser.setSelectedFile(new File(filename));
        fileChooser.setFileFilter(filter);
      }
      result = fileChooser.showSaveDialog(MainWindow.getFrame());
    }

    if (result == JFileChooser.APPROVE_OPTION) {
      return fileChooser.getSelectedFile().toPath();
    }

    return null;
  }

  public static Path selectFile(String title, String initialPath, FileNameExtensionFilter filter) {
    // are we forced to open the legacy file chooser?
    if ("true".equalsIgnoreCase(System.getProperty("tmm.legacy.filechooser"))) {
      return openJFileChooser(JFileChooser.FILES_ONLY, title, initialPath, true, null, filter);
    }

    if (SystemUtils.IS_OS_LINUX) {
      // use TinyFileDialogs
      try {
        // check if the initialPath is accessible
        if (StringUtils.isBlank(initialPath) || !Files.exists(Paths.get(initialPath))) {
          initialPath = System.getProperty("user.home");
        }

        String[] filterList = null;
        String filterDescription = null;

        if (filter != null) {
          List<String> extensions = new ArrayList<>();
          filterDescription = filter.getDescription();
          for (String extension : filter.getExtensions()) {
            extensions.add("*" + extension);
          }
          filterList = extensions.toArray(new String[0]);
        }

        return new TinyFileDialogs().openFile(title, Paths.get(initialPath), filterList, filterDescription);
      }
      catch (Exception | Error e) {
        LOGGER.error("Could not call TinyFileDialogs - '{}'", e.getMessage());
      }
    }

    // try with FlatLaf native file chooser
    try {
      SystemFileChooser fc = new SystemFileChooser();
      fc.setMultiSelectionEnabled(false);
      if (StringUtils.isNotBlank(initialPath)) {
        fc.setCurrentDirectory(new File(initialPath));
      }
      fc.setFileFilter(convertFilter(filter));
      if (fc.showOpenDialog(null) == SystemFileChooser.APPROVE_OPTION) {
        File file = fc.getSelectedFile();
        return file.toPath();
      }
      else {
        return null;
      }
    }
    catch (Exception | Error e) {
      LOGGER.error("Could not call FlatFlaf SystemFileChooser - '{}'", e.getMessage());
    }

    // open JFileChooser
    return openJFileChooser(JFileChooser.FILES_ONLY, title, initialPath, true, null, filter);
  }

  public static Path selectApplication(String title, String initialPath) {
    if (SystemUtils.IS_OS_MAC) {
      try {
        Process process = Runtime.getRuntime()
            .exec(new String[] { //
                "/usr/bin/osascript", //
                "-e", //
                "set selectedFolder to choose application as alias\n"//
                    + "return POSIX path of selectedFolder" });
        int result = process.waitFor();
        if (result == 0) {
          String selectedFolder = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
          return Paths.get(selectedFolder);
        }
      }
      catch (Exception e) {
        LOGGER.error("Could not call osascript - '{}'", e.getMessage());
      }

      return null;
    }
    else if (SystemUtils.IS_OS_WINDOWS) {
      return selectFile(title, initialPath, new FileNameExtensionFilter(TmmResourceBundle.getString("tmm.executables"), "exe"));
    }
    else {
      return selectFile(title, initialPath, null);
    }
  }

  public static Path saveFile(String title, String initialPath, String filename, FileNameExtensionFilter filter) {
    // are we forced to open the legacy file chooser?
    if ("true".equalsIgnoreCase(System.getProperty("tmm.legacy.filechooser"))) {
      return openJFileChooser(JFileChooser.FILES_ONLY, title, initialPath, false, filename, filter);
    }

    Path selectedFile = Paths.get(initialPath, filename);

    if (SystemUtils.IS_OS_LINUX) {
      // try to open with TinyFileDialogs
      try {
        String[] filterList = null;
        String filterDescription = null;

        if (filter != null) {
          List<String> extensions = new ArrayList<>();
          filterDescription = filter.getDescription();
          for (String extension : filter.getExtensions()) {
            extensions.add("*" + extension);
          }
          filterList = extensions.toArray(new String[0]);
        }

        return new TinyFileDialogs().saveFile(title, selectedFile, filterList, filterDescription);
      }
      catch (Exception | Error e) {
        LOGGER.error("Could not call TinyFileDialogs - '{}'", e.getMessage());
      }
    }

    // try with FlatLaf native file chooser
    try {
      SystemFileChooser fc = new SystemFileChooser();
      if (StringUtils.isNotBlank(initialPath)) {
        fc.setCurrentDirectory(new File(initialPath));
      }
      fc.setFileFilter(convertFilter(filter));
      fc.setSelectedFile(selectedFile.toFile());
      if (fc.showSaveDialog(null) == SystemFileChooser.APPROVE_OPTION) {
        return fc.getSelectedFile().toPath();
      }
      else {
        return null;
      }
    }
    catch (Exception | Error e) {
      LOGGER.error("Could not call FlatFlaf SystemFileChooser - '{}'", e.getMessage());
    }

    return openJFileChooser(JFileChooser.FILES_ONLY, title, initialPath, false, filename, filter);
  }

  /**
   * Convert a Swing FileNameExtensionFilter to a FlatLaf SystemFileChooser.FileNameExtensionFilter
   *
   * @param filter
   *          the Swing filter
   * @return the FlatLaf filter
   */
  private static SystemFileChooser.FileNameExtensionFilter convertFilter(FileNameExtensionFilter filter) {
    if (filter == null) {
      return null;
    }
    String[] extensions = filter.getExtensions();
    for (int i = 0; i < extensions.length; i++) {
      extensions[i] = extensions[i].replace(".", "");
    }
    return new SystemFileChooser.FileNameExtensionFilter(filter.getDescription(), extensions);
  }

  /**
   * opens a file with the default application
   *
   * @param file
   *          the {@link Path} to the file to open
   * @throws Exception
   *           any exception occurred
   */
  public static void openFile(Path file) throws Exception {
    if (file == null) {
      return;
    }
    String abs = file.toAbsolutePath().toString();
    if (StringUtils.isBlank(abs)) {
      return;
    }

    // opening a root folder "/" or "D:\\" has no filename, but could be opened
    boolean rootFolder = false;
    if (file.getFileName() == null) {
      rootFolder = true;
    }

    String fileType = rootFolder ? ".mkv" : "." + FilenameUtils.getExtension(file.getFileName().toString().toLowerCase(Locale.ROOT));
    if (!rootFolder && StringUtils.isNotBlank(Settings.getInstance().getMediaPlayer())
        && Settings.getInstance().getAllSupportedFileTypes().contains(fileType)) {
      if (SystemUtils.IS_OS_MAC) {
        exec(new String[] { "open", Settings.getInstance().getMediaPlayer(), "--args", abs });
      }
      else {
        exec(new String[] { Settings.getInstance().getMediaPlayer(), abs });
      }
    }
    else if (SystemUtils.IS_OS_WINDOWS) {
      // try to open directly
      try {
        Desktop.getDesktop().open(file.toFile());
      }
      catch (Exception e) {
        LOGGER.debug("could not open file with the default app - '{}'", e.getMessage());
        // use explorer directly - ship around access exceptions and the unresolved network bug
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6780505
        exec(new String[] { "explorer", abs });
      }
    }
    else if (SystemUtils.IS_OS_LINUX) {
      // try all different starters
      boolean started = false;
      try {
        exec(new String[] { "gio", "open", abs });
        started = true;
      }
      catch (IOException ignored) {
        // no exception handling needed
      }

      if (!started) {
        try {
          exec(new String[] { "xdg-open", abs });
        }
        catch (IOException ignored) {
          // no exception handling needed
        }
      }

      if (!started && Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file.toFile());
      }
    }
    else if (Desktop.isDesktopSupported()) {
      Desktop.getDesktop().open(file.toFile());
    }
    else {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * opens a folder in the default file manager
   * 
   * @param path
   *          the {@link Path} to the folder to open
   */
  public static void openFolder(Path path) {
    try {
      // check whether this location exists
      if (Files.exists(path) && Files.isDirectory(path)) {
        TmmUIHelper.openFile(path);
      }
      else {
        LOGGER.debug("could not open folder '{}' -> does not exist?", path);
        BasicFileAttributes fileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
        LOGGER.debug("isDir {}", fileAttributes.isDirectory());
        LOGGER.debug("isRegularFile {}", fileAttributes.isRegularFile());
        LOGGER.debug("isOther {}", fileAttributes.isOther());
        LOGGER.debug("isSymlink {}", fileAttributes.isSymbolicLink());
        LOGGER.debug("creationTime {}", fileAttributes.creationTime());
      }
    }
    catch (Exception ex) {
      LOGGER.error("Could not open file manager - '{}'", ex.getMessage());
      MessageManager.getInstance()
          .pushMessage(new Message(Message.MessageLevel.ERROR, path, "message.erroropenfolder", new String[] { ":", ex.getLocalizedMessage() }));
    }
  }

  /**
   * browse to the url
   *
   * @param url
   *          the url to browse
   * @throws Exception
   *           any exception occurred
   */
  public static void browseUrl(String url) throws Exception {
    if (StringUtils.isBlank(url)) {
      return;
    }

    // handle local files via Desktop.open / Linux fallbacks
    if (url.startsWith("file:/")) {
      openFile(Path.of(new URI(url)));
      return;
    }

    // handle urls
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      URI uri = new URI(url);
      // best practice: open the browser in a new thread
      new Thread(() -> {
        try {
          Desktop.getDesktop().browse(uri);
        }
        catch (Exception e) {
          // handle exceptions in the EDT
          SwingUtilities.invokeLater(() -> MessageManager.getInstance()
              .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() })));
        }
      }).start();
    }
    else if (SystemUtils.IS_OS_LINUX) {
      // try all different starters
      boolean started = false;
      try {
        exec(new String[] { "gio", "open", url });
        started = true;
      }
      catch (IOException ignored) {
        // no exception handling needed
      }

      if (!started) {
        try {
          exec(new String[] { "xdg-open", url });
        }
        catch (IOException ignored) {
          // no exception handling needed
        }
      }
    }
    else {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * browse to the url without throwing any exception
   *
   * @param url
   *          the url to browse
   */
  public static void browseUrlSilently(String url) {
    try {
      browseUrl(url);
    }
    catch (Exception e) {
      LOGGER.error("Could not open url '{}' - '{}'", url, e.getMessage());
      MessageManager.getInstance()
          .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() }));
    }
  }

  /**
   * Executes a command line and discards the stdout and stderr of the spawned process.
   *
   * @param cmdline
   *          the command including all parameters
   * @throws IOException
   *           any {@link IOException} thrown while processing
   */
  private static void exec(String[] cmdline) throws IOException {
    Process p = Runtime.getRuntime().exec(cmdline);

    // The purpose of the following to threads is to read stdout and stderr from the processes, which are spawned in this class.
    // On some platforms (for sure on Linux) the process might block otherwise, because the internal buffers fill up.
    // MPV for example is quite verbose and blocks up after about 1 min.
    StreamRedirectThread stdoutReader = new StreamRedirectThread(p.getInputStream(), new NirvanaOutputStream());
    StreamRedirectThread stderrReader = new StreamRedirectThread(p.getErrorStream(), new NirvanaOutputStream());
    new Thread(stdoutReader).start();
    new Thread(stderrReader).start();
  }

  /**
   * This OutputStream discards all bytes written to it.
   */
  private static class NirvanaOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      // nothing to write
    }

    @Override
    public void write(@NotNull byte[] b) throws IOException {
      // nothing to write
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
      // nothing to write
    }
  }

  /**
   * Reads from an InputStream and writes the contents directly to an OutputStream
   */
  public static class StreamRedirectThread implements Runnable {
    private final InputStream  in;
    private final OutputStream out;

    public StreamRedirectThread(InputStream in, OutputStream out) {
      super();
      this.in = in;
      this.out = out;
    }

    @Override
    public void run() {
      try {
        int length = -1;
        byte[] buffer = new byte[1024 * 1024];
        while (in != null && (length = in.read(buffer)) >= 0) {
          out.write(buffer, 0, length);
        }
      }
      catch (Exception e) {
        LOGGER.debug("Could not redirect stream: '{}'", e.getLocalizedMessage());
      }
    }
  }

  /**
   * Enhance a {@link LinkLabel} which shows an image preview dialog on click
   *
   * @param linklabel
   *          the {@link LinkLabel} to add the action listener to
   * @param image
   *          the image
   * @return the {@link LinkLabel} containing the action listener
   */
  public static LinkLabel createLinkForImage(LinkLabel linklabel, ImageLabel image) {
    linklabel.addActionListener(e -> {
      if (StringUtils.isNotBlank(image.getImagePath())) {
        ImagePreviewDialog dialog = new ImagePreviewDialog(Paths.get(image.getImagePath()));
        dialog.setVisible(true);
      }
      else {
        ImagePreviewDialog dialog = new ImagePreviewDialog(image.getImageUrl());
        dialog.setVisible(true);
      }
    });

    return linklabel;
  }

  /**
   * Update UI of all application windows immediately. Invoke after changing anything in the LaF.
   */
  public static void updateUI() {
    // update all visible components
    for (Window w : Window.getWindows()) {
      SwingUtilities.updateComponentTreeUI(w);
      w.invalidate();
    }

    // update icons
    IconManager.updateIcons();
  }

  /**
   * sets the theme according to the settings
   *
   * @throws Exception
   *           any exception occurred
   */
  public static void setTheme() throws Exception {

    switch (Settings.getInstance().getTheme()) {
      case "Dark":
        FlatLaf.setup(new TmmDarkLaf());
        break;

      case "Light":
      default:
        FlatLaf.setup(new TmmLightLaf());
        break;
    }
  }

  /**
   * checks for our automatic update setting interval <br>
   * Nightly users are always true!
   *
   * @return true/false
   */
  public static boolean shouldCheckForUpdate() {
    if (ReleaseInfo.isNightly()) {
      return true;
    }

    if (!Settings.getInstance().isEnableAutomaticUpdate()) {
      return false;
    }

    try {
      // get the timestamp for the last update check
      long old = TmmStore.getInstance().getAsLong("tmm.update.lastCheck", 0L);
      long now = new Date().getTime();

      return now > old + (long) Settings.getInstance().getAutomaticUpdateInterval() * 1000 * 3600 * 24F;
    }
    catch (Exception e) {
      LOGGER.debug("Could not check the update interval - '{}'", e.getMessage());
      return true;
    }
  }

  public static void checkForUpdate(int delayInSeconds) {
    Runnable runnable = () -> {
      try {
        UpdateCheck updateCheck = new UpdateCheck();
        if (updateCheck.isUpdateAvailable()) {
          LOGGER.info("Update available");

          // we might need this somewhen...
          if (Globals.isSelfUpdatable() && updateCheck.isForcedUpdate()) {
            LOGGER.info("Updating (forced)...");
            // start the updater task
            TmmTaskManager.getInstance().addDownloadTask(new UpdaterTask());
            return;
          }

          // show whatsnewdialog with the option to update
          SwingUtilities.invokeLater(() -> {
            if (StringUtils.isNotBlank(updateCheck.getChangelog())) {
              UpdateDialog dialog = new UpdateDialog(updateCheck.getChangelog(), updateCheck.getBaseUrl());
              dialog.setVisible(true);
            }
            else if (Globals.isSelfUpdatable()) {
              // do the update without changelog popup
              Object[] options = { TmmResourceBundle.getString("Button.yes"), TmmResourceBundle.getString("Button.no") };
              int answer = JOptionPane.showOptionDialog(MainWindow.getInstance(), TmmResourceBundle.getString("tmm.update.message"),
                  TmmResourceBundle.getString("tmm.update.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);
              if (answer == JOptionPane.YES_OPTION) {
                LOGGER.info("Updating...");

                // start the updater task
                TmmTaskManager.getInstance().addDownloadTask(new UpdaterTask());
              }
            }
          });
        }
        else {
          // no update found
          if (delayInSeconds == 0) { // show no update dialog only when manually triggered
            JOptionPane.showMessageDialog(MainWindow.getInstance(), TmmResourceBundle.getString("tmm.update.notfound"),
                TmmResourceBundle.getString("tmm.update.title"), JOptionPane.INFORMATION_MESSAGE);
          }
        }
      }
      catch (Exception e) {
        LOGGER.warn("Update check failed - '{}'", e.getMessage());
      }
    };

    if (delayInSeconds > 0) {
      // update task start a few secs after GUI...
      Timer timer = new Timer(delayInSeconds * 1000, e -> TmmTaskManager.getInstance().addUnnamedTask(runnable));
      timer.setRepeats(false);
      timer.start();
    }
    else {
      TmmTaskManager.getInstance().addUnnamedTask(runnable);
    }
  }

  /**
   * Removes the selected rows of the given {@link JTable} from the given {@link List}. The given {@link List} needs to be bound to the model!
   *
   * @param table
   *          the {@link JTable} to get the selected rows from
   * @param list
   *          the {@link List} to remove the entries from
   * @param <E>
   *          the type of the rows
   */
  public static <E> void removeSelectedRowsFromJTable(JTable table, List<E> list) {
    int[] indexRows = getSelectedRowsAsModelRows(table);

    for (int indexRow : indexRows) {
      try {
        list.remove(indexRow);
      }
      catch (Exception e) {
        LOGGER.debug("Could not remove entry from the list - '{}'", e.getMessage());
      }
    }
  }

  /**
   * Get all selected rows from the given {@link JTable} as rows from the underlying model
   * 
   * @param jTable
   *          the {@link JTable}
   * @return an int[] containing the model indices of all selected rows
   */
  public static int[] getSelectedRowsAsModelRows(JTable jTable) {
    int[] tableRows = jTable.getSelectedRows();
    int[] modelRows = new int[tableRows.length];
    for (int i = 0; i < tableRows.length; i++) {
      modelRows[i] = jTable.convertRowIndexToModel(tableRows[i]);
    }

    // sort it (descending)
    ArrayUtils.reverse(modelRows);
    return modelRows;
  }

  /**
   * Get all selected rows from the given {@link JList} as rows from the underlying model
   *
   * @param jList
   *          the {@link JList}
   * @return an int[] containing the model indices of all selected rows
   */
  public static int[] getSelectedRowsAsModelRows(JList<?> jList) {
    int[] selectedIndices = jList.getSelectedIndices();

    // sort it (descending)
    ArrayUtils.reverse(selectedIndices);
    return selectedIndices;
  }
}

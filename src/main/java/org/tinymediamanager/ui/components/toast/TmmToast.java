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
package org.tinymediamanager.ui.components.toast;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The Class {@link TmmToast} provides a glass-pane based toast notification system for TmmDialog.
 * <p>
 * Toast messages appear as semi-transparent overlays on the right side of dialogs, automatically fading in and out. Multiple toasts stack vertically.
 * </p>
 *
 * @author Manuel Laggner
 */
public class TmmToast extends JComponent {
  /** map windows to their toast glass pane instance */
  private static final WeakHashMap<Window, TmmToast> INSTANCES = new WeakHashMap<>();

  private final List<ToastMessage>                   toasts    = new ArrayList<>();
  private final Timer                                animator;

  private static final int                           FPS       = 60;
  private static final int                           TICK_MS   = 1000 / FPS;
  private static final int                           MARGIN    = 16;
  private static final int                           GAP       = 12;
  private static final int                           MAX_WIDTH = 400;
  private static final int                           PADDING_H = 16;
  private static final int                           PADDING_V = 12;
  private static final int                           ARC       = 8;
  /** gap between title and message */
  private static final int                           TITLE_GAP = 4;

  /** the window which owns this toast instance */
  private Window                                     ownerWindow;
  /** the previous glass pane which had been set before installing this toast */
  private JComponent                                 previousGlassPane;
  /** the visibility of the previous glass pane, to restore on uninstall */
  private boolean                                    previousGlassPaneVisible;
  /** component listener registered to the owner window */
  private ComponentAdapter                           resizeListener;

  private TmmToast() {
    setOpaque(false);
    animator = new Timer(TICK_MS, e -> tick());
    // timer starts on-demand when first toast is shown
  }

  /**
   * Install a toast glass pane for the given window if not already installed.
   *
   * @param window
   *          the target window (dialog/frame)
   * @return the installed {@link TmmToast} instance
   */
  public static TmmToast install(Window window) {
    if (window == null) {
      throw new IllegalArgumentException("window must not be null");
    }
    synchronized (INSTANCES) {
      TmmToast toast = INSTANCES.get(window);
      if (toast == null) {
        toast = new TmmToast();
        toast.ownerWindow = window;
        INSTANCES.put(window, toast);
        if (window instanceof RootPaneContainer rpc) {
          // remember previous glass pane to be able to restore it
          if (rpc.getGlassPane() instanceof JComponent gp) {
            toast.previousGlassPane = gp;
            toast.previousGlassPaneVisible = gp.isVisible();
          }
          rpc.getRootPane().setGlassPane(toast);
          toast.setVisible(true);

          // handle window resize to update toast positions
          final TmmToast finalToast = toast;
          toast.resizeListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
              finalToast.repaint();
            }
          };
          window.addComponentListener(toast.resizeListener);
        }
      }
      return toast;
    }
  }

  /**
   * Uninstall the toast glass pane from the given window and restore the previous state.
   * <p>
   * Stops animations, removes listeners, clears messages and restores the previous glass pane to avoid memory leaks.
   * </p>
   *
   * @param window
   *          the window where the toast has been installed
   */
  public static void uninstall(Window window) {
    if (window == null) {
      return;
    }
    synchronized (INSTANCES) {
      TmmToast toast = INSTANCES.remove(window);
      if (toast != null) {
        toast.uninstallInternal();
      }
    }
  }

  /**
   * Check whether the toast has been installed for the given window.
   *
   * @param window
   *          the window to check
   * @return true if installed; false otherwise
   */
  public static boolean isInstalled(Window window) {
    synchronized (INSTANCES) {
      return INSTANCES.containsKey(window);
    }
  }

  /**
   * Perform the actual uninstall/cleanup. Restores previous glass pane, removes listeners and stops animation.
   */
  private void uninstallInternal() {
    // stop and clear animation
    if (animator.isRunning()) {
      animator.stop();
    }
    synchronized (toasts) {
      toasts.clear();
    }

    // remove listener from window
    if (ownerWindow != null && resizeListener != null) {
      try {
        ownerWindow.removeComponentListener(resizeListener);
      }
      catch (Exception ignored) {
        // ignore
      }
      resizeListener = null;
    }

    // restore previous glass pane
    if (ownerWindow instanceof RootPaneContainer rpc) {
      try {
        // only restore if our toast is still the current glass pane
        if (rpc.getGlassPane() == this && previousGlassPane != null) {
          rpc.getRootPane().setGlassPane(previousGlassPane);
          previousGlassPane.setVisible(previousGlassPaneVisible);
        }
        else if (rpc.getGlassPane() == this) {
          // fallback: set a fresh default glass pane
          JComponent defaultGp = new JComponent() {
            // no state
          };
          defaultGp.setVisible(false);
          rpc.getRootPane().setGlassPane(defaultGp);
        }
      }
      catch (Exception ignored) {
        // ignore
      }
    }

    // hide ourselves and drop references
    setVisible(false);
    previousGlassPane = null;
    ownerWindow = null;
  }

  /**
   * Show a toast message on this glass pane.
   *
   * @param message
   *          the text to display
   * @param type
   *          the toast type (determines color and icon)
   * @param durationMs
   *          duration in milliseconds before fade-out begins
   */
  public void showToast(final String message, final ToastType type, final int durationMs) {
    if (message == null || message.trim().isEmpty()) {
      return;
    }
    SwingUtilities.invokeLater(() -> {
      synchronized (toasts) {
        toasts.add(0, new ToastMessage(null, message, type, durationMs));
      }
      // ensure animator is running
      if (!animator.isRunning()) {
        animator.start();
      }
      repaint();
    });
  }

  /**
   * Show a toast with default INFO type and 3 second duration.
   *
   * @param message
   *          the text to display
   */
  public void showToast(final String message) {
    showToast(message, ToastType.INFO, 3000);
  }

  /**
   * Show a toast message with an optional title on this glass pane.
   * <p>
   * The title is rendered in bold above the message text.
   * </p>
   *
   * @param title
   *          the optional title (rendered bold); may be null or empty
   * @param message
   *          the text to display (required)
   * @param type
   *          the toast type (determines color)
   * @param durationMs
   *          duration in milliseconds before fade-out begins
   */
  public void showToast(final String title, final String message, final ToastType type, final int durationMs) {
    if (message == null || message.trim().isEmpty()) {
      return;
    }
    SwingUtilities.invokeLater(() -> {
      synchronized (toasts) {
        toasts.add(0, new ToastMessage(title, message, type, durationMs));
      }
      if (!animator.isRunning()) {
        animator.start();
      }
      repaint();
    });
  }

  /**
   * Show a toast with an optional title, default INFO type and 3 second duration.
   *
   * @param title
   *          the optional title (rendered bold); may be null or empty
   * @param message
   *          the text to display (required)
   */
  public void showToast(final String title, final String message) {
    showToast(title, message, ToastType.INFO, 3000);
  }

  /**
   * Update animation state and remove expired toasts.
   */
  private void tick() {
    boolean needsRepaint = false;
    boolean hasActiveToasts = false;
    long now = System.currentTimeMillis();
    synchronized (toasts) {
      Iterator<ToastMessage> it = toasts.iterator();
      while (it.hasNext()) {
        ToastMessage t = it.next();
        long life = now - t.createdAt;
        float oldAlpha = t.alpha;

        // start fading out after duration expires
        if (!t.fading && life >= t.durationMs) {
          t.fading = true;
          t.fadeStart = now;
        }

        // compute alpha
        if (t.fading) {
          long fadeElapsed = now - t.fadeStart;
          float alpha = 1f - Math.min(1f, fadeElapsed / (float) t.fadeDurationMs);
          t.alpha = Math.max(0f, alpha);
          if (t.alpha <= 0.01f) {
            it.remove();
            needsRepaint = true;
            continue;
          }
        }
        else {
          // fade in
          long since = now - t.createdAt;
          t.alpha = Math.min(1f, Math.max(0f, since / (float) t.fadeInMs));
        }

        // only repaint if alpha actually changed
        if (Math.abs(t.alpha - oldAlpha) > 0.001f) {
          needsRepaint = true;
        }

        hasActiveToasts = true;
      }
    }

    // stop timer if no toasts are active
    if (!hasActiveToasts && animator.isRunning()) {
      animator.stop();
    }

    if (needsRepaint) {
      repaint();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      int windowWidth = getWidth();
      int y = MARGIN;

      synchronized (toasts) {
        for (ToastMessage toast : toasts) {
          Dimension size = calculateToastSize(g2, toast);
          int x = windowWidth - MARGIN - size.width;

          // slide in from right with easing (ease-out cubic)
          float slideProgress = 1f - toast.alpha;
          float easedProgress = 1f - (float) Math.pow(slideProgress, 3);
          int slide = (int) ((1f - easedProgress) * 30);
          x += slide;

          drawToast(g2, toast, x, y, size);
          y += size.height + GAP;
        }
      }
    }
    finally {
      g2.dispose();
    }
  }

  /**
   * Calculate the size needed for a toast message.
   *
   * @param g2
   *          the graphics context
   * @param toast
   *          the toast message
   * @return the dimensions
   */
  private Dimension calculateToastSize(Graphics2D g2, ToastMessage toast) {
    FontMetrics fm = g2.getFontMetrics();
    Font titleFont = g2.getFont().deriveFont(g2.getFont().getStyle() | Font.BOLD);
    FontMetrics tfm = g2.getFontMetrics(titleFont);

    int windowWidth = getWidth();
    int maxTextWidth = Math.min(MAX_WIDTH - 2 * PADDING_H, windowWidth - 2 * (MARGIN + PADDING_H));

    // calculate text dimensions with word wrapping
    List<String> messageLines = wrapText(toast.message, fm, maxTextWidth);
    int messageTextHeight = messageLines.size() * fm.getHeight();
    int messageTextWidth = 0;
    for (String line : messageLines) {
      messageTextWidth = Math.max(messageTextWidth, fm.stringWidth(line));
    }

    int titleTextHeight = 0;
    int titleTextWidth = 0;
    if (toast.title != null && !toast.title.trim().isEmpty()) {
      List<String> titleLines = wrapText(toast.title, tfm, maxTextWidth);
      titleTextHeight = titleLines.size() * tfm.getHeight();
      for (String line : titleLines) {
        titleTextWidth = Math.max(titleTextWidth, tfm.stringWidth(line));
      }
    }

    int contentWidth = Math.max(messageTextWidth, titleTextWidth);
    int width = Math.min(MAX_WIDTH, contentWidth + 2 * PADDING_H);

    int height = messageTextHeight + 2 * PADDING_V;
    if (titleTextHeight > 0) {
      height += titleTextHeight + TITLE_GAP;
    }

    return new Dimension(width, height);
  }

  /**
   * Wrap text to fit within the specified width.
   *
   * @param text
   *          the text to wrap
   * @param fm
   *          the font metrics
   * @param maxWidth
   *          the maximum width
   * @return list of wrapped lines
   */
  private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
    List<String> lines = new ArrayList<>();
    String[] words = text.split(" ");
    StringBuilder currentLine = new StringBuilder();

    for (String word : words) {
      String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
      if (fm.stringWidth(testLine) <= maxWidth) {
        if (!currentLine.isEmpty()) {
          currentLine.append(" ");
        }
        currentLine.append(word);
      }
      else {
        if (!currentLine.isEmpty()) {
          lines.add(currentLine.toString());
          currentLine = new StringBuilder(word);
        }
        else {
          // word is too long, add it anyway
          lines.add(word);
        }
      }
    }
    if (!currentLine.isEmpty()) {
      lines.add(currentLine.toString());
    }

    return lines.isEmpty() ? List.of(text) : lines;
  }

  /**
   * Draw a single toast message.
   *
   * @param g2
   *          the graphics context
   * @param toast
   *          the toast message
   * @param x
   *          the x position
   * @param y
   *          the y position
   * @param size
   *          the size
   */
  private void drawToast(Graphics2D g2, ToastMessage toast, int x, int y, Dimension size) {
    // apply alpha
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, toast.alpha));

    // background
    Color bg = toast.type.getBackgroundColor();
    g2.setColor(bg);
    g2.fillRoundRect(x, y, size.width, size.height, ARC, ARC);

    // border
    Color borderColor = toast.type.getBorderColor();
    g2.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), (int) (toast.alpha * 255)));
    g2.drawRoundRect(x, y, size.width, size.height, ARC, ARC);

    // text
    g2.setColor(toast.type.getTextColor());
    FontMetrics fm = g2.getFontMetrics();
    int textX = x + PADDING_H;
    int textY = y + PADDING_V + fm.getAscent();

    // draw title if present (bold)
    if (toast.title != null && !toast.title.trim().isEmpty()) {
      Font original = g2.getFont();
      Font bold = original.deriveFont(original.getStyle() | Font.BOLD);
      g2.setFont(bold);
      FontMetrics tfm = g2.getFontMetrics();
      List<String> titleLines = wrapText(toast.title, tfm, size.width - 2 * PADDING_H);
      for (String line : titleLines) {
        g2.drawString(line, textX, textY);
        textY += tfm.getHeight();
      }
      // gap between title and message
      textY += TITLE_GAP;
      // restore normal font for message
      g2.setFont(original);
      fm = g2.getFontMetrics();
    }

    // draw wrapped message text
    List<String> lines = wrapText(toast.message, fm, size.width - 2 * PADDING_H);
    for (String line : lines) {
      g2.drawString(line, textX, textY);
      textY += fm.getHeight();
    }
  }

  /**
   * The Class {@link ToastMessage} represents a single toast notification.
   */
  private static class ToastMessage {
    /** optional title (rendered bold) */
    final String    title;
    final String    message;
    final ToastType type;
    final long      createdAt;
    final int       durationMs;
    float           alpha          = 0f;
    boolean         fading         = false;
    long            fadeStart      = 0;
    final int       fadeInMs       = 250;
    final int       fadeDurationMs = 600;

    ToastMessage(String title, String message, ToastType type, int durationMs) {
      this.title = title;
      this.message = message;
      this.type = type == null ? ToastType.INFO : type;
      this.createdAt = System.currentTimeMillis();
      this.durationMs = Math.max(1000, durationMs);
    }
  }

  /**
   * Toast types controlling appearance (colors).
   */
  public enum ToastType {
    /** Informational message */
    INFO,
    /** Success message */
    SUCCESS,
    /** Warning message */
    WARNING,
    /** Error message */
    ERROR;

    /**
     * Detect whether the current FlatLaf theme is dark based on panel background brightness.
     *
     * @return true if the theme is dark
     */
    private static boolean isDarkTheme() {
      Color bg = UIManager.getColor("Panel.background");
      if (bg == null) {
        return false;
      }
      return (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3 < 128;
    }

    /**
     * Get the background color for this toast type.
     *
     * @return the background color
     */
    Color getBackgroundColor() {
      boolean dark = isDarkTheme();
      switch (this) {
        case SUCCESS:
          return dark ? new Color(46, 125, 50) : new Color(200, 230, 201);

        case WARNING:
          return dark ? new Color(120, 56, 0) : new Color(255, 248, 225);

        case ERROR:
          return dark ? new Color(109, 26, 26) : new Color(255, 205, 210);

        case INFO:
        default:
          Color panelBg = UIManager.getColor("Panel.background");
          if (panelBg != null) {
            int brightness = (panelBg.getRed() + panelBg.getGreen() + panelBg.getBlue()) / 3;
            if (brightness > 128) {
              // light theme - darken
              return new Color(Math.max(0, panelBg.getRed() - 40), Math.max(0, panelBg.getGreen() - 40), Math.max(0, panelBg.getBlue() - 40));
            }
            else {
              // dark theme - lighten
              return new Color(Math.min(255, panelBg.getRed() + 40), Math.min(255, panelBg.getGreen() + 40), Math.min(255, panelBg.getBlue() + 40));
            }
          }
          return new Color(66, 66, 66);
      }
    }

    /**
     * Get the border color for this toast type.
     *
     * @return the border color
     */
    Color getBorderColor() {
      boolean dark = isDarkTheme();
      switch (this) {
        case SUCCESS:
          return dark ? new Color(56, 142, 60) : new Color(165, 214, 167);

        case WARNING:
          return dark ? new Color(160, 80, 0) : new Color(255, 224, 130);

        case ERROR:
          return dark ? new Color(142, 36, 36) : new Color(239, 154, 154);

        default:
          return new Color(0, 0, 0, 100);
      }
    }

    /**
     * Get the text color for this toast type.
     *
     * @return the text color
     */
    Color getTextColor() {
      boolean dark = isDarkTheme();
      switch (this) {
        case SUCCESS:
          return dark ? new Color(200, 230, 201) : new Color(46, 125, 50);

        case WARNING:
          return dark ? new Color(255, 204, 128) : new Color(230, 81, 0);

        case ERROR:
          return dark ? new Color(239, 154, 154) : new Color(183, 28, 28);

        case INFO:
        default:
          Color panelBg = UIManager.getColor("Panel.background");
          if (panelBg != null) {
            int brightness = (panelBg.getRed() + panelBg.getGreen() + panelBg.getBlue()) / 3;
            return brightness > 128 ? Color.BLACK : Color.WHITE;
          }
          return Color.BLACK;
      }
    }
  }
}

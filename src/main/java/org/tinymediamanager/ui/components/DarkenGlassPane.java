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
package org.tinymediamanager.ui.components;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import javax.swing.JComponent;
import javax.swing.RootPaneContainer;

/**
 * The class {@link DarkenGlassPane} provides a semi-transparent dark overlay for modal dialogs.
 * <p>
 * When a modal child dialog is displayed over a parent window, this glass pane darkens the parent content similar to Material Design patterns. The
 * overlay uses a 50% opacity black color.
 * </p>
 * <p>
 * Usage pattern:
 * </p>
 * 
 * <pre>
 * // Install on parent before showing child dialog
 * DarkenGlassPane.install(parentWindow);
 * childDialog.setVisible(true);
 *
 * // When parent regains focus (no more modal children), uninstall
 * DarkenGlassPane.uninstall(parentWindow);
 * </pre>
 *
 * @author Manuel Laggner
 */
public class DarkenGlassPane extends JComponent {

  private static final WeakHashMap<Window, WeakReference<DarkenGlassPane>> INSTANCES = new WeakHashMap<>();
  private static final float                                               OPACITY   = 0.5f;

  @SuppressWarnings("unused")
  private Window                                                           ownerWindow;
  private JComponent                                                       previousGlassPane;
  private boolean                                                          previousGlassPaneVisible;

  private DarkenGlassPane() {
    setOpaque(false);
  }

  /**
   * Install a darken glass pane for the given window if not already installed.
   *
   * @param window
   *          the target window (dialog/frame)
   */
  public static void install(Window window) {
    if (window == null) {
      return;
    }

    synchronized (INSTANCES) {
      WeakReference<DarkenGlassPane> ref = INSTANCES.get(window);
      DarkenGlassPane pane = ref != null ? ref.get() : null;

      if (pane == null) {
        pane = new DarkenGlassPane();
        pane.ownerWindow = window;

        if (window instanceof RootPaneContainer rpc) {
          // remember previous glass pane to be able to restore it
          JComponent glassPane = (JComponent) rpc.getGlassPane();
          if (glassPane != null && !(glassPane instanceof DarkenGlassPane)) {
            pane.previousGlassPane = glassPane;
            pane.previousGlassPaneVisible = glassPane.isVisible();
          }

          rpc.getRootPane().setGlassPane(pane);
          pane.setVisible(true);
        }

        INSTANCES.put(window, new WeakReference<>(pane));
      }
    }
  }

  /**
   * Uninstall the darken glass pane from the given window and restore the previous state.
   * <p>
   * Restores the previous glass pane to avoid memory leaks.
   * </p>
   *
   * @param window
   *          the target window
   */
  public static void uninstall(Window window) {
    if (window == null) {
      return;
    }

    synchronized (INSTANCES) {
      WeakReference<DarkenGlassPane> ref = INSTANCES.remove(window);
      if (ref == null) {
        return;
      }

      DarkenGlassPane pane = ref.get();
      if (pane == null) {
        return;
      }

      // restore previous glass pane
      if (window instanceof RootPaneContainer rpc) {
        try {
          // only restore if our pane is still the current glass pane
          if (rpc.getGlassPane() == pane && pane.previousGlassPane != null) {
            rpc.getRootPane().setGlassPane(pane.previousGlassPane);
            pane.previousGlassPane.setVisible(pane.previousGlassPaneVisible);
          }
          else if (rpc.getGlassPane() == pane) {
            // fallback: set a fresh default glass pane
            JComponent defaultGp = new JComponent() {
              // no state
            };
            defaultGp.setVisible(false);
            rpc.getRootPane().setGlassPane(defaultGp);
          }
        }
        catch (Exception ignored) {
          // ignore any exceptions during restoration
        }
      }

      // hide ourselves and drop references
      pane.setVisible(false);
      pane.previousGlassPane = null;
      pane.ownerWindow = null;
    }
  }

  @Override
  protected void paintComponent(Graphics g1) {
    super.paintComponent(g1);

    // paint semi-transparent dark overlay
    Graphics2D g2d = (Graphics2D) g1.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

    g2d.setPaint(Color.black);
    Composite savedComposite = g2d.getComposite();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACITY));
    g2d.fillRect(0, 0, getWidth(), getHeight());
    g2d.setComposite(savedComposite);
    g2d.dispose();
  }
}

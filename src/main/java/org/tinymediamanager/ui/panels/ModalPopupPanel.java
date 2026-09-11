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
package org.tinymediamanager.ui.panels;

import static javax.swing.SwingConstants.CENTER;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.JTextComponent;

import org.tinymediamanager.ui.EqualsLayout;
import org.tinymediamanager.ui.TmmFontHelper;

import net.miginfocom.swing.MigLayout;

/**
 * the class {@link ModalPopupPanel} is used to enable a Material UI like popup panel which is embedded into the surrounding *
 * {@link javax.swing.JFrame}/{@link javax.swing.JDialog}
 * 
 * @author Manuel Laggner
 */
public class ModalPopupPanel extends JPanel {
  private static final int               ARC = 15;

  private final IModalPopupPanelProvider popupPanelProvider;

  private final JLabel                   lblTitle;
  private final JPanel                   contentPanel;
  private final JPanel                   buttonPanel;

  private Runnable                       onCloseHandler;
  private Runnable                       onCancelHandler;

  private JButton                        defaultFocusButton;

  /**
   * key code of the ENTER/ESCAPE key cycle that has to be swallowed because it was already handled by a popup, or <code>0</code> if nothing to
   * swallow
   */
  private static int                     swallowedKeyCode;

  /**
   * number of currently showing {@link ModalPopupPanel}s, used to (un)install {@link EnterEscapeSwallower} for exactly as long as it is needed
   */
  private static int                     showingPopupCount;

  public ModalPopupPanel(IModalPopupPanelProvider parent) {
    this(parent, null);
  }

  public ModalPopupPanel(IModalPopupPanelProvider parent, String title) {
    super(false);

    this.popupPanelProvider = parent;

    setOpaque(false);
    addMouseListener(new MouseAdapter() {
      // prevent clicks from going through the panel
    });

    setLayout(new MigLayout("", "[center, grow]", "[center, grow]"));

    JPanel layoutPanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g1) {
        super.paintComponent(g1);

        // background
        Graphics2D g2d = (Graphics2D) g1.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Dimension arcs = new Dimension(ARC, ARC);
        int width = getWidth();
        int height = getHeight();

        // Draws the rounded opaque panel with borders.
        g2d.setColor(getBackground());
        g2d.fillRoundRect(0, 0, width - 1, height - 1, arcs.width, arcs.height);// paint background
        g2d.setColor(new Color(80, 80, 80));
        g2d.drawRoundRect(0, 0, width - 1, height - 1, arcs.width, arcs.height);
        g2d.dispose();
      }

      @Override
      protected void paintChildren(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 15, 15));
        super.paintChildren(g2d);
        g2d.dispose();
      }
    };

    layoutPanel.setOpaque(false);
    layoutPanel.setLayout(new BorderLayout());
    add(layoutPanel, "cell 0 0");

    JPanel titlePanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2d = (Graphics2D) g1.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int width = getWidth();
        int height = getHeight();
        g2d.setColor(javax.swing.UIManager.getColor("PopupPanelTitle.background"));
        int arc = ARC;
        g2d.fillRoundRect(0, 0, width, arc * 2, arc, arc);
        g2d.fillRect(0, arc, width, height - arc);
        g2d.setColor(javax.swing.UIManager.getColor("Separator.foreground"));
        g2d.drawLine(0, height - 1, width, height - 1);
        g2d.dispose();
      }
    };
    titlePanel.setOpaque(false);
    titlePanel.setLayout(new MigLayout("insets 0, gap 0", "[grow]", "[grow]"));

    lblTitle = new JLabel(title);
    lblTitle.setHorizontalAlignment(CENTER);
    TmmFontHelper.changeFont(lblTitle, TmmFontHelper.H3, Font.BOLD);
    titlePanel.add(lblTitle, "cell 0 0, grow, gaptop 5, gapbottom 5, gapleft 20, gapright 20");
    layoutPanel.add(titlePanel, BorderLayout.NORTH);

    contentPanel = new JPanel();
    contentPanel.setBorder(new EmptyBorder(0, 20, 0, 20));
    layoutPanel.add(contentPanel, BorderLayout.CENTER);

    {
      JPanel bottomPanel = new JPanel();
      layoutPanel.add(bottomPanel, BorderLayout.SOUTH);
      bottomPanel.setLayout(new MigLayout("insets n 0 0 0, gap rel 0", "[grow][]", "[shrink 0][]"));

      bottomPanel.add(new JSeparator(), "cell 0 0 2 1,growx");

      buttonPanel = new JPanel();
      EqualsLayout layout = new EqualsLayout(5);
      layout.setMinWidth(100);
      buttonPanel.setLayout(layout);
      buttonPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
      bottomPanel.add(buttonPanel, "cell 1 1");
    }

    addAncestorListener(new AncestorListener() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        // keep the ENTER/ESCAPE trailing-key swallower installed for exactly as long as at least one popup is showing
        // (it is uninstalled again in ancestorRemoved once it is idle)
        showingPopupCount++;
        EnterEscapeSwallower.install();

        // the popup does not block window wide key bindings or key listeners of the surrounding UI, so move the focus
        // into the popup to make sure keystrokes do not leak to the components behind it (unless the content took focus itself)
        SwingUtilities.invokeLater(() -> {
          if (!isShowing()) {
            return;
          }

          Window window = SwingUtilities.getWindowAncestor(ModalPopupPanel.this);
          Component focusOwner = window == null ? null : window.getFocusOwner();
          if (SwingUtilities.isDescendingFrom(focusOwner, ModalPopupPanel.this)) {
            return;
          }

          JComponent focusTarget = findFirstFocusable(ModalPopupPanel.this, JTextComponent.class);
          if (focusTarget == null) {
            focusTarget = defaultFocusButton;
          }
          if (focusTarget == null) {
            focusTarget = findFirstFocusable(ModalPopupPanel.this, JComponent.class);
          }
          if (focusTarget != null && focusTarget.isFocusable()) {
            focusTarget.requestFocusInWindow();
          }
        });
      }

      @Override
      public void ancestorMoved(AncestorEvent event) {
        // not needed
      }

      @Override
      public void ancestorRemoved(AncestorEvent event) {
        showingPopupCount--;
        EnterEscapeSwallower.releaseIfIdle();
      }
    });
  }

  @Override
  protected void paintComponent(Graphics g1) {
    super.paintComponent(g1);

    // background
    Graphics2D g = (Graphics2D) g1.create();
    g.setPaint(Color.black);
    Composite savedComposite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
    g.fillRect(0, 0, getWidth(), getHeight());
    g.setComposite(savedComposite);
    g.dispose();
  }

  public void setTitle(String title) {
    lblTitle.setText(title);
  }

  public void setContent(IModalPopupPanel panel) {
    JComponent content = panel.getContent();
    content.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentHidden(ComponentEvent e) {
        popupPanelProvider.hideModalPopupPanel(ModalPopupPanel.this);
        if (panel.isCancelled()) {
          onCancel();
        }
        else {
          onClose();
        }
      }
    });
    contentPanel.add(content);

    if (panel.getCancelButton() != null) {
      buttonPanel.add(panel.getCancelButton());
    }
    if (panel.getCloseButton() != null) {
      buttonPanel.add(panel.getCloseButton());
      defaultFocusButton = panel.getCloseButton();
    }

    installDefaultKeyBindings(panel);
  }

  /**
   * binds ENTER to the default (close) action and ESC to the cancel action while this popup is showing.
   * <p>
   * The bindings are registered as {@link JComponent#WHEN_IN_FOCUSED_WINDOW} so they work regardless of which component currently holds the focus,
   * and they are automatically (un)registered together with the popup itself, which also lets them take precedence over the surrounding window/root
   * pane bindings while the popup is visible.
   *
   * @param panel
   *          the content panel providing the buttons to trigger
   */
  private void installDefaultKeyBindings(IModalPopupPanel panel) {
    InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap actionMap = getActionMap();

    inputMap.put(KeyStroke.getKeyStroke("pressed ENTER"), "popupConfirm");
    actionMap.put("popupConfirm", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JButton closeButton = panel.getCloseButton();
        if (closeButton != null) {
          // close the popup and swallow the rest of this ENTER key cycle, so the (already generated) ENTER key event
          // does not leak to the components behind the popup (e.g. the "edit selected item" shortcut of the master tables)
          EnterEscapeSwallower.swallow(KeyEvent.VK_ENTER);
          closeButton.doClick();
        }
      }
    });

    inputMap.put(KeyStroke.getKeyStroke("pressed ESCAPE"), "popupCancel");
    actionMap.put("popupCancel", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JButton cancelButton = panel.getCancelButton();
        EnterEscapeSwallower.swallow(KeyEvent.VK_ESCAPE);
        if (cancelButton != null) {
          cancelButton.doClick();
        }
        else if (panel.getCloseButton() != null) {
          panel.getCloseButton().doClick();
        }
      }
    });
  }

  /**
   * The class {@link EnterEscapeSwallower} discards the trailing key events of an ENTER/ESCAPE cycle that has already been consumed by a
   * {@link ModalPopupPanel}. A single physical key press produces a {@link KeyEvent#KEY_PRESSED}, {@link KeyEvent#KEY_TYPED} and
   * {@link KeyEvent#KEY_RELEASED} event sequence which are dispatched one after another: while the {@link KeyEvent#KEY_PRESSED} triggers the popup
   * action (and thereby closes the popup and restores the focus to the component behind it), the following events would otherwise reach that
   * underlying component and trigger unwanted actions (e.g. the "edit selected item" shortcut of the master tables).
   * <p>
   * The dispatcher is installed while at least one popup is showing (see {@link #install()}) and uninstalled again as soon as no popup is showing and
   * no trailing key cycle is pending anymore, so it never lingers beyond its actual use.
   */
  private static class EnterEscapeSwallower implements KeyEventDispatcher {
    private static EnterEscapeSwallower INSTANCE;

    private EnterEscapeSwallower() {
    }

    /**
     * installs the dispatcher into the current {@link KeyboardFocusManager}s dispatcher chain if it is not already installed; safe to call for every
     * shown popup
     */
    static void install() {
      if (INSTANCE == null) {
        INSTANCE = new EnterEscapeSwallower();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(INSTANCE);
      }
    }

    /**
     * marks the trailing key events of the given key code to be swallowed until its release; the dispatcher itself is already installed while a popup
     * is showing
     *
     * @param keyCode
     *          the key code ({@link KeyEvent#VK_ENTER} or {@link KeyEvent#VK_ESCAPE})
     */
    static void swallow(int keyCode) {
      swallowedKeyCode = keyCode;
    }

    /**
     * uninstalls the dispatcher once no popup is showing and no key cycle has to be swallowed anymore
     */
    static void releaseIfIdle() {
      if (INSTANCE != null && showingPopupCount == 0 && swallowedKeyCode == 0) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(INSTANCE);
        INSTANCE = null;
      }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
      if (swallowedKeyCode == 0) {
        return false;
      }

      switch (e.getID()) {
        case KeyEvent.KEY_PRESSED:
          if (e.getKeyCode() != swallowedKeyCode) {
            // a different key was pressed -> the popup already finished handling its cycle, do not swallow anything else
            swallowedKeyCode = 0;
            releaseIfIdle();
          }
          return false;

        case KeyEvent.KEY_TYPED:
          // KEY_TYPED events carry the char but no keyCode, so match ENTER/RETURN by its character
          if (swallowedKeyCode == KeyEvent.VK_ENTER && (e.getKeyChar() == '\n' || e.getKeyChar() == '\r')) {
            e.consume();
            return true;
          }
          if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            swallowedKeyCode = 0;
            releaseIfIdle();
          }
          return false;

        case KeyEvent.KEY_RELEASED:
          if (e.getKeyCode() == swallowedKeyCode) {
            swallowedKeyCode = 0;
            releaseIfIdle();
          }
          return false;

        default:
          return false;
      }
    }
  }

  /**
   * searches the component tree depth-first for the first focusable component of the given type
   *
   * @param parent
   *          the container to start the search in
   * @param type
   *          the expected type of the component to find
   *
   * @return the first focusable component of the given type, or <code>null</code> if there is none
   */
  private static <T extends JComponent> T findFirstFocusable(Container parent, Class<T> type) {
    for (Component component : parent.getComponents()) {
      if (type.isInstance(component)) {
        T candidate = type.cast(component);
        if (candidate.isFocusable()) {
          return candidate;
        }
      }

      if (component instanceof Container) {
        T found = findFirstFocusable((Container) component, type);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  public void setOnCloseHandler(Runnable onCloseHandler) {
    this.onCloseHandler = onCloseHandler;
  }

  public void setOnCancelHandler(Runnable onCancelHandler) {
    this.onCancelHandler = onCancelHandler;
  }

  protected void onClose() {
    if (onCloseHandler != null) {
      onCloseHandler.run();
    }
  }

  protected void onCancel() {
    if (onCancelHandler != null) {
      onCancelHandler.run();
    }
  }
}

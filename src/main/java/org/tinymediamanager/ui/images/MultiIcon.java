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

package org.tinymediamanager.ui.images;

import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

/**
 * The class {@link MultiIcon} is used to display several {@link Icon}s side by side within a single icon slot (e.g. a {@link javax.swing.JLabel}).
 * <p>
 * The given icons are painted from left to right with a small gap in between and are vertically centered relative to the tallest icon. The referenced
 * {@link Icon} instances are kept as-is, so recoloring/rescaling of the underlying icons is reflected automatically.
 *
 * @author Manuel Laggner
 */
public class MultiIcon implements Icon {
  private static final int GAP = 2;

  private final List<Icon> icons;

  /**
   * Creates a new {@link MultiIcon} from the given {@link Icon}s. {@code null} entries are ignored.
   *
   * @param childIcons
   *          the icons to display side by side
   */
  public MultiIcon(Icon... childIcons) {
    icons = new ArrayList<>();
    if (childIcons != null) {
      for (Icon icon : childIcons) {
        if (icon != null) {
          icons.add(icon);
        }
      }
    }
  }

  /**
   * Convenience factory to combine up to two icons. Returns {@code null} if no icon is given, the single icon if only one is given, or a
   * {@link MultiIcon} if both are given.
   *
   * @param first
   *          the first (left-most) icon, may be {@code null}
   * @param second
   *          the second icon, may be {@code null}
   * @return the composed {@link Icon}
   */
  public static Icon of(Icon first, Icon second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return new MultiIcon(first, second);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    int height = getIconHeight();
    int currentX = x;
    for (Icon icon : icons) {
      int iconY = y + (height - icon.getIconHeight()) / 2;
      icon.paintIcon(c, g, currentX, iconY);
      currentX += icon.getIconWidth() + GAP;
    }
  }

  @Override
  public int getIconWidth() {
    int width = 0;
    for (Icon icon : icons) {
      width += icon.getIconWidth() + GAP;
    }
    if (!icons.isEmpty()) {
      width -= GAP;
    }
    return width;
  }

  @Override
  public int getIconHeight() {
    int height = 0;
    for (Icon icon : icons) {
      height = Math.max(height, icon.getIconHeight());
    }
    return height;
  }
}

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

package org.tinymediamanager.ui.plaf;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.lang.reflect.Field;
import java.util.Dictionary;
import java.util.Enumeration;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.plaf.ComponentUI;

import org.tinymediamanager.ui.components.slider.RangeSlider;

import com.formdev.flatlaf.ui.FlatSliderUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

public class TmmRangeSliderUI extends FlatSliderUI {

  protected boolean second;

  public static ComponentUI createUI(JComponent c) {
    return new TmmRangeSliderUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    second = false;
    super.paint(g, c);

    Rectangle clip = g.getClipBounds();

    second = true;
    Point p = adjustThumbForHighValue();

    if (clip.intersects(thumbRect)) {
      Object[] oldRenderingHints = FlatUIUtils.setRenderingHints(g);
      paintThumb(g);
      FlatUIUtils.resetRenderingHints(g, oldRenderingHints);
    }

    restoreThumbForLowValue(p);
    second = false;
  }

  protected Point adjustThumbForHighValue() {
    Point p = thumbRect.getLocation();
    if (slider.getOrientation() == JSlider.HORIZONTAL) {
      int valuePosition = xPositionForValue(((RangeSlider) slider).getHighValue());
      thumbRect.x = valuePosition - (thumbRect.width / 2);
    }
    else {
      int valuePosition = yPositionForValue(((RangeSlider) slider).getHighValue());
      thumbRect.y = valuePosition - (thumbRect.height / 2);
    }
    return p;
  }

  protected void restoreThumbForLowValue(Point p) {
    thumbRect.x = p.x;
    thumbRect.y = p.y;
  }

  protected void adjustSnapHighValue() {
    int sliderValue = ((RangeSlider) slider).getHighValue();
    // do not snap the high value away from the slider maximum, otherwise a dynamically raised
    // maximum (e.g. artwork height 1086) would be pulled back to the nearest labeled tick (1080)
    if (sliderValue >= slider.getMaximum()) {
      return;
    }

    int snappedValue = snapToNearestLabeledTick(sliderValue);

    if (snappedValue != sliderValue) {
      ((RangeSlider) slider).setHighValue(snappedValue);
    }
  }

  protected void adjustSnapLowValue() {
    int sliderValue = ((RangeSlider) slider).getLowValue();
    // do not snap the low value away from the slider minimum
    if (sliderValue <= slider.getMinimum()) {
      return;
    }

    int snappedValue = snapToNearestLabeledTick(sliderValue);

    if (snappedValue != sliderValue) {
      ((RangeSlider) slider).setLowValue(snappedValue);
    }
  }

  /**
   * Snap to the nearest tick position (labeled or unlabeled). Checks both labeled ticks and calculated major/minor tick positions.
   *
   * @param value
   *          the value to snap
   * @return the nearest tick value
   */
  private int snapToNearestLabeledTick(int value) {
    int nearest = value;
    int minDistance = Integer.MAX_VALUE;

    // First, check labeled tick positions (custom well-known sizes)
    Dictionary<Integer, ?> labels = slider.getLabelTable();
    if (labels != null && !labels.isEmpty()) {
      Enumeration<Integer> keys = labels.keys();
      while (keys.hasMoreElements()) {
        int tickValue = keys.nextElement();
        int distance = Math.abs(value - tickValue);
        if (distance < minDistance) {
          minDistance = distance;
          nearest = tickValue;
        }
      }
    }

    // Also check major and minor tick spacing positions
    int majorTickSpacing = slider.getMajorTickSpacing();
    int minorTickSpacing = slider.getMinorTickSpacing();
    int tickSpacing = 0;

    if (minorTickSpacing > 0) {
      tickSpacing = minorTickSpacing;
    }
    else if (majorTickSpacing > 0) {
      tickSpacing = majorTickSpacing;
    }

    if (tickSpacing > 0) {
      // Calculate the nearest tick based on spacing
      float temp = (float) (value - slider.getMinimum()) / (float) tickSpacing;
      int whichTick = Math.round(temp);
      int snappedToSpacing = slider.getMinimum() + (whichTick * tickSpacing);

      // Clamp to bounds
      snappedToSpacing = Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), snappedToSpacing));

      int distance = Math.abs(value - snappedToSpacing);
      // Simply snap to whichever is closest - labeled or spacing
      if (distance < minDistance) {
        nearest = snappedToSpacing;
      }
    }

    return nearest;
  }

  public void paintTrack(Graphics g) {
    boolean enabled = this.slider.isEnabled();
    float tw = UIScale.scale((float) this.trackWidth);
    RoundRectangle2D coloredTrack = null;
    RoundRectangle2D trackLow;
    RoundRectangle2D trackHigh;

    if (this.slider.getOrientation() == SwingConstants.HORIZONTAL) {
      float y = (float) this.trackRect.y + ((float) this.trackRect.height - tw) / 2.0F;

      int lowThumbX = xPositionForValue(((RangeSlider) slider).getLowValue());
      int highThumbX = xPositionForValue(((RangeSlider) slider).getHighValue());

      if (enabled) {
        coloredTrack = new RoundRectangle2D.Float((float) lowThumbX, y, (float) (highThumbX - lowThumbX), tw, tw, tw);
        trackLow = new RoundRectangle2D.Float((float) this.trackRect.x, y, (float) (lowThumbX - this.trackRect.x), tw, tw, tw);
        trackHigh = new RoundRectangle2D.Float((float) highThumbX, y, (float) (this.trackRect.x + this.trackRect.width - highThumbX), tw, tw, tw);
      }
      else {
        trackLow = trackHigh = new RoundRectangle2D.Float((float) this.trackRect.x, y, (float) this.trackRect.width, tw, tw, tw);
      }
    }
    else {
      float x = (float) this.trackRect.x + ((float) this.trackRect.width - tw) / 2.0F;

      // not implemented
      // if (enabled && this.isRoundThumb()) {
      // int ch = this.thumbRect.y + this.thumbRect.height / 2 - this.trackRect.y;
      // trackLow = new RoundRectangle2D.Float(x, (float) this.trackRect.y, tw, (float) ch, tw, tw);
      // trackHigh = new RoundRectangle2D.Float(x, (float) this.trackRect.y, tw, (float) ch, tw, tw);
      // coloredTrack = new RoundRectangle2D.Float(x, (float) (this.trackRect.y + ch), tw, (float) (this.trackRect.height - ch), tw, tw);
      // }
      // else {
      trackLow = new RoundRectangle2D.Float(x, (float) this.trackRect.y, tw, (float) this.trackRect.height, tw, tw);
      trackHigh = new RoundRectangle2D.Float(x, (float) this.trackRect.y, tw, (float) this.trackRect.height, tw, tw);
      // }
    }

    if (coloredTrack != null) {
      g.setColor(this.getTrackValueColor());
      ((Graphics2D) g).fill(coloredTrack);
    }

    g.setColor(enabled ? this.getTrackColor() : this.disabledTrackColor);
    ((Graphics2D) g).fill(trackLow);
    ((Graphics2D) g).fill(trackHigh);
  }

  @Override
  public void paintTicks(Graphics g) {
    // Call the parent implementation first to paint all standard ticks
    super.paintTicks(g);

    // Then highlight labeled ticks with thicker lines
    Dictionary<Integer, ?> labels = slider.getLabelTable();
    if (labels == null || labels.isEmpty()) {
      return;
    }

    Graphics2D g2d = (Graphics2D) g;
    g2d.setColor(this.getTrackValueColor());
    g2d.setStroke(new BasicStroke(1.5f));

    if (slider.getOrientation() == SwingConstants.HORIZONTAL) {
      // For horizontal sliders, draw thicker vertical lines at labeled positions
      int y1 = tickRect.y;
      int y2 = tickRect.y + tickRect.height;

      Enumeration<Integer> keys = labels.keys();
      while (keys.hasMoreElements()) {
        int tickValue = keys.nextElement();
        int x = xPositionForValue(tickValue);
        g2d.drawLine(x, y1, x, y2);
      }
    }
    else {
      // For vertical sliders, draw thicker horizontal lines at labeled positions
      int x1 = tickRect.x;
      int x2 = tickRect.x + tickRect.width;

      Enumeration<Integer> keys = labels.keys();
      while (keys.hasMoreElements()) {
        int tickValue = keys.nextElement();
        int y = yPositionForValue(tickValue);
        g2d.drawLine(x1, y, x2, y);
      }
    }
  }

  protected void calculateThumbLocation() {
    // Only snap if snap-to-ticks is enabled
    if (slider.getSnapToTicks()) {
      adjustSnapLowValue();
      adjustSnapHighValue();
    }
    if (slider.getOrientation() == JSlider.HORIZONTAL) {
      int valuePosition = xPositionForValue(slider.getValue());

      thumbRect.x = valuePosition - (thumbRect.width / 2);
      thumbRect.y = trackRect.y;
    }
    else {
      int valuePosition = yPositionForValue(slider.getValue());

      thumbRect.x = trackRect.x;
      thumbRect.y = valuePosition - (thumbRect.height / 2);
    }
  }

  @Override
  protected void calculateTrackBuffer() {
    if (slider.getOrientation() == JSlider.HORIZONTAL) {
      trackBuffer = thumbRect.width / 2;
    }
    else {
      trackBuffer = thumbRect.height / 2;
    }
  }

  @Override
  protected TrackListener createTrackListener(JSlider slider) {
    return new RangeTrackListener(super.createTrackListener(slider));
  }

  protected class RangeTrackListener extends TrackListener {
    int           handle;
    int           handleOffset;
    int           mouseStartLocation;
    TrackListener _listener;

    public RangeTrackListener(TrackListener listener) {
      _listener = listener;
    }

    /**
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    @Override
    public void mousePressed(MouseEvent e) {
      if (!slider.isEnabled()) {
        return;
      }

      if (slider.isRequestFocusEnabled()) {
        slider.requestFocus();
      }

      handle = getMouseHandle(e.getX(), e.getY());
      setMousePressed(handle);

      if (handle == MOUSE_HANDLE_MAX || handle == MOUSE_HANDLE_MIN || handle == MOUSE_HANDLE_MIDDLE || handle == MOUSE_HANDLE_BOTH) {
        handleOffset = (slider.getOrientation() == JSlider.VERTICAL) ? e.getY() - yPositionForValue(((RangeSlider) slider).getLowValue())
            : e.getX() - xPositionForValue(((RangeSlider) slider).getLowValue());

        mouseStartLocation = (slider.getOrientation() == JSlider.VERTICAL) ? e.getY() : e.getX();

        slider.getModel().setValueIsAdjusting(true);
      }
      else if (handle == MOUSE_HANDLE_LOWER || handle == MOUSE_HANDLE_UPPER) {
        _listener.mousePressed(e);
        slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION, null);
      }
    }

    /**
     * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseDragged(MouseEvent e) {
      if (!slider.isEnabled()) {
        return;
      }

      int newLocation = (slider.getOrientation() == JSlider.VERTICAL) ? e.getY() : e.getX();

      int newValue = (slider.getOrientation() == JSlider.VERTICAL) ? valueForYPosition(newLocation) : valueForXPosition(newLocation);

      if (newValue < slider.getModel().getMinimum()) {
        newValue = slider.getModel().getMinimum();
      }

      if (newValue > slider.getModel().getMaximum()) {
        newValue = slider.getModel().getMaximum();
      }

      if (handle == MOUSE_HANDLE_BOTH) {
        if ((newLocation - mouseStartLocation) >= 1) {
          handle = MOUSE_HANDLE_MAX;
        }
        else if ((newLocation - mouseStartLocation) <= -1) {
          handle = MOUSE_HANDLE_MIN;
        }
        else {
          return;
        }
      }

      RangeSlider rangeSlider = (RangeSlider) slider;
      switch (handle) {
        case MOUSE_HANDLE_MIN:
          rangeSlider.setLowValue(Math.min(newValue, rangeSlider.getHighValue()));
          break;

        case MOUSE_HANDLE_MAX:
          rangeSlider.setHighValue(Math.max(rangeSlider.getLowValue(), newValue));
          break;

        case MOUSE_HANDLE_MIDDLE:
          if (((RangeSlider) slider).isRangeDraggable()) {
            int delta = (slider.getOrientation() == JSlider.VERTICAL) ? valueForYPosition(newLocation - handleOffset) - rangeSlider.getLowValue()
                : valueForXPosition(newLocation - handleOffset) - rangeSlider.getLowValue();
            if ((delta < 0) && ((rangeSlider.getLowValue() + delta) < rangeSlider.getMinimum())) {
              delta = rangeSlider.getMinimum() - rangeSlider.getLowValue();
            }

            if ((delta > 0) && ((rangeSlider.getHighValue() + delta) > rangeSlider.getMaximum())) {
              delta = rangeSlider.getMaximum() - rangeSlider.getHighValue();
            }

            if (delta != 0) {
              rangeSlider.setLowValue(rangeSlider.getLowValue() + delta);
              rangeSlider.setHighValue(rangeSlider.getHighValue() + delta);
            }
          }
          break;
      }
    }

    /**
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseReleased(MouseEvent e) {
      slider.getModel().setValueIsAdjusting(false);
      setMouseReleased(handle);
      _listener.mouseReleased(e);
    }

    private void setCursor(int c) {
      Cursor cursor = Cursor.getPredefinedCursor(c);

      if (slider.getCursor() != cursor) {
        slider.setCursor(cursor);
      }
    }

    /**
     * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseMoved(MouseEvent e) {
      if (!slider.isEnabled()) {
        return;
      }

      int handle = getMouseHandle(e.getX(), e.getY());
      setMouseRollover(handle);
      switch (handle) {
        case MOUSE_HANDLE_MIN:
        case MOUSE_HANDLE_MAX:
        case MOUSE_HANDLE_BOTH:
          setCursor(Cursor.DEFAULT_CURSOR);
          break;

        case MOUSE_HANDLE_MIDDLE:
          if (slider instanceof RangeSlider && ((RangeSlider) slider).isRangeDraggable()) {
            setCursor(Cursor.MOVE_CURSOR);
          }
          else {
            setCursor(Cursor.DEFAULT_CURSOR);
          }
          break;

        case MOUSE_HANDLE_NONE:
        default:
          setCursor(Cursor.DEFAULT_CURSOR);
          break;
      }
    }

    /**
     * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2) {
        slider.getModel().setValue(slider.getModel().getMinimum());
        slider.getModel().setExtent(slider.getModel().getMaximum() - slider.getModel().getMinimum());
        slider.repaint();
      }
    }

    /**
     * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseEntered(MouseEvent e) {
      hover = true;
      slider.repaint();
    }

    /**
     * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseExited(MouseEvent e) {
      hover = false;
      slider.repaint();
      setCursor(Cursor.DEFAULT_CURSOR);
    }
  }

  protected static final int MOUSE_HANDLE_NONE   = 0;

  protected static final int MOUSE_HANDLE_MIN    = 1;

  protected static final int MOUSE_HANDLE_MAX    = 2;

  protected static final int MOUSE_HANDLE_MIDDLE = 4;

  protected static final int MOUSE_HANDLE_LOWER  = 5;

  protected static final int MOUSE_HANDLE_UPPER  = 6;

  protected static final int MOUSE_HANDLE_BOTH   = 7;

  protected int getMouseHandle(int x, int y) {
    Rectangle rect = trackRect;

    slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION, null);

    boolean inMin = false;
    boolean inMax = false;
    if (thumbRect.contains(x, y)) {
      inMin = true;
    }
    Point p = adjustThumbForHighValue();
    if (thumbRect.contains(x, y)) {
      inMax = true;
    }
    restoreThumbForLowValue(p);
    if (inMin && inMax) {
      return MOUSE_HANDLE_BOTH;
    }
    else if (inMin) {
      return MOUSE_HANDLE_MIN;
    }
    else if (inMax) {
      return MOUSE_HANDLE_MAX;
    }

    if (slider.getOrientation() == JSlider.VERTICAL) {
      int minY = yPositionForValue(((RangeSlider) slider).getLowValue());
      int maxY = yPositionForValue(((RangeSlider) slider).getHighValue());
      Rectangle midRect = new Rectangle(rect.x, Math.min(minY, maxY) + thumbRect.height / 2, rect.width, Math.abs(maxY - minY) - thumbRect.height);
      if (midRect.contains(x, y)) {
        return MOUSE_HANDLE_MIDDLE;
      }
      int sy = rect.y + Math.max(minY, maxY) + thumbRect.height / 2;
      Rectangle lowerRect = new Rectangle(rect.x, sy, rect.width, rect.y + rect.height - sy);
      if (lowerRect.contains(x, y)) {
        slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION, true);
        return MOUSE_HANDLE_LOWER;
      }
      Rectangle upperRect = new Rectangle(rect.x, rect.y, rect.width, Math.min(maxY, minY) - thumbRect.height / 2);
      if (upperRect.contains(x, y)) {
        slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION, false);
        return MOUSE_HANDLE_UPPER;
      }

      return MOUSE_HANDLE_NONE;
    }
    else {
      int minX = xPositionForValue(((RangeSlider) slider).getLowValue());
      int maxX = xPositionForValue(((RangeSlider) slider).getHighValue());

      Rectangle midRect = new Rectangle(Math.min(minX, maxX) + thumbRect.width / 2, rect.y, Math.abs(maxX - minX) - thumbRect.width, rect.height);
      if (midRect.contains(x, y)) {
        return MOUSE_HANDLE_MIDDLE;
      }
      Rectangle lowerRect = new Rectangle(rect.x, rect.y, Math.min(minX, maxX) - thumbRect.width / 2 - rect.x, rect.height);
      if (lowerRect.contains(x, y)) {
        slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION, true);
        return MOUSE_HANDLE_LOWER;
      }
      int sx = rect.x + Math.abs(maxX - minX) + thumbRect.width / 2;
      Rectangle upperRect = new Rectangle(sx, rect.y, rect.width - sx, rect.height);
      if (upperRect.contains(x, y)) {
        slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION, false);
        return MOUSE_HANDLE_UPPER;
      }
      return MOUSE_HANDLE_NONE;
    }
  }

  protected boolean hover;
  protected boolean rollover1;
  protected boolean pressed1;
  protected boolean rollover2;
  protected boolean pressed2;

  @Override
  public void paintThumb(Graphics g) {
    try {
      Field field = getClass().getSuperclass().getDeclaredField("rollover");
      field.setAccessible(true);
      field.set(this, second ? rollover2 : rollover1);

      field = getClass().getSuperclass().getDeclaredField("pressed");
      field.setAccessible(true);
      field.set(this, second ? pressed2 : pressed1);
    }
    catch (NoSuchFieldException e) {
      // e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      // e.printStackTrace();
    }

    super.paintThumb(g);
  }

  protected void setMouseRollover(int handle) {
    switch (handle) {
      case MOUSE_HANDLE_MIN: {
        rollover1 = true;
        rollover2 = false;
      }
        break;

      case MOUSE_HANDLE_MAX: {
        rollover2 = true;
        rollover1 = false;
      }
        break;

      case MOUSE_HANDLE_MIDDLE:
      case MOUSE_HANDLE_BOTH: {
        rollover1 = true;
        rollover2 = true;
      }
        break;

      case MOUSE_HANDLE_NONE:
        rollover1 = false;
        rollover2 = false;
        break;
    }
    slider.repaint(thumbRect);
    Point p = adjustThumbForHighValue();
    slider.repaint(thumbRect);
    restoreThumbForLowValue(p);
  }

  protected void setMousePressed(int handle) {
    switch (handle) {
      case MOUSE_HANDLE_MIN: {
        pressed1 = true;
        pressed2 = false;
      }
        break;

      case MOUSE_HANDLE_MAX: {
        pressed2 = true;
        pressed1 = false;
      }
        break;

      case MOUSE_HANDLE_MIDDLE:
      case MOUSE_HANDLE_BOTH: {
        pressed1 = true;
        pressed2 = true;
      }
        break;

      case MOUSE_HANDLE_NONE:
        pressed1 = false;
        pressed2 = false;
        break;
    }
    slider.repaint(thumbRect);
    Point p = adjustThumbForHighValue();
    slider.repaint(thumbRect);
    restoreThumbForLowValue(p);
  }

  @SuppressWarnings({ "UnusedDeclaration" })
  protected void setMouseReleased(int handle) {
    pressed1 = false;
    pressed2 = false;
    slider.repaint(thumbRect);
    Point p = adjustThumbForHighValue();
    slider.repaint(thumbRect);
    restoreThumbForLowValue(p);
  }

  @Override
  public void scrollByBlock(int direction) {
    synchronized (slider) {

      int oldValue;
      Object clientProperty = slider.getClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION);
      if (clientProperty == null) {
        oldValue = slider.getValue();
      }
      else if (Boolean.TRUE.equals(clientProperty)) {
        oldValue = ((RangeSlider) slider).getLowValue();
      }
      else {
        oldValue = ((RangeSlider) slider).getHighValue();
      }
      int blockIncrement = (slider.getMaximum() - slider.getMinimum()) / 10;
      if (blockIncrement <= 0 && slider.getMaximum() > slider.getMinimum()) {

        blockIncrement = 1;
      }

      int delta = blockIncrement * ((direction > 0) ? POSITIVE_SCROLL : NEGATIVE_SCROLL);
      slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_ADJUST_ACTION, "scrollByBlock");
      if (clientProperty == null) {
        slider.setValue(Math.max(Math.min(oldValue + delta, slider.getMaximum()), slider.getMinimum()));
      }
      else if (Boolean.TRUE.equals(clientProperty)) {
        ((RangeSlider) slider).setLowValue(Math.max(Math.min(oldValue + delta, slider.getMaximum()), slider.getMinimum()));
      }
      else {
        ((RangeSlider) slider).setHighValue(Math.max(Math.min(oldValue + delta, slider.getMaximum()), slider.getMinimum()));
      }
      slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_ADJUST_ACTION, null);
    }
  }

  @Override
  public void scrollByUnit(int direction) {
    synchronized (slider) {

      int oldValue;
      Object clientProperty = slider.getClientProperty(RangeSlider.CLIENT_PROPERTY_MOUSE_POSITION);
      if (clientProperty == null) {
        oldValue = slider.getValue();
      }
      else if (Boolean.TRUE.equals(clientProperty)) {
        oldValue = ((RangeSlider) slider).getLowValue();
      }
      else {
        oldValue = ((RangeSlider) slider).getHighValue();
      }
      int delta = 1 * ((direction > 0) ? POSITIVE_SCROLL : NEGATIVE_SCROLL);

      slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_ADJUST_ACTION, "scrollByUnit");
      if (clientProperty == null) {
        slider.setValue(Math.max(Math.min(oldValue + delta, slider.getMaximum()), slider.getMinimum()));
      }
      else if (Boolean.TRUE.equals(clientProperty)) {
        ((RangeSlider) slider).setLowValue(Math.max(Math.min(oldValue + delta, slider.getMaximum()), slider.getMinimum()));
      }
      else {
        ((RangeSlider) slider).setHighValue(Math.max(Math.min(oldValue + delta, slider.getMaximum()), slider.getMinimum()));
      }
      slider.putClientProperty(RangeSlider.CLIENT_PROPERTY_ADJUST_ACTION, null);
    }
  }
}

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

package org.tinymediamanager.ui.components.datepicker;

import java.awt.FlowLayout;
import java.util.Calendar;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.ui.IconManager;

/**
 * The class TimePanel is used to display a panel for time choosing with hour and minute spinners.
 *
 * @author Manuel Laggner
 */
class TimePanel extends JPanel implements ChangeListener {
  private final SpinnerNumberModel hourModel;
  private final SpinnerNumberModel minuteModel;

  private boolean                  ignoreChange;

  TimePanel() {
    this(0, 0);
  }

  TimePanel(int hour, int minute) {
    setLayout(new FlowLayout(FlowLayout.CENTER, 6, 4));
    setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    setOpaque(false);

    hourModel = new SpinnerNumberModel(hour, 0, 23, 1);
    JSpinner hourSpinner = new JSpinner(hourModel);
    hourSpinner.setEditor(new JSpinner.NumberEditor(hourSpinner, "00"));
    hourSpinner.addChangeListener(this);

    minuteModel = new SpinnerNumberModel(minute, 0, 59, 1);
    JSpinner minuteSpinner = new JSpinner(minuteModel);
    minuteSpinner.setEditor(new JSpinner.NumberEditor(minuteSpinner, "00"));
    minuteSpinner.addChangeListener(this);

    JLabel separator = new JLabel(":");

    JButton clearButton = new JButton(IconManager.CANCEL_INV);
    clearButton.setToolTipText(TmmResourceBundle.getString("Button.cleartime"));
    clearButton.setFocusable(false);
    clearButton.addActionListener(e -> {
      ignoreChange = true;
      try {
        hourModel.setValue(0);
        minuteModel.setValue(0);
      }
      finally {
        ignoreChange = false;
      }
      firePropertyChange("time", null, this);
    });

    add(hourSpinner);
    add(separator);
    add(minuteSpinner);
    add(clearButton);
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    if (ignoreChange) {
      return;
    }
    firePropertyChange("time", null, this);
  }

  /**
   * Gets the current hour value (0-23).
   *
   * @return the current hour
   */
  public int getHour() {
    return hourModel.getNumber().intValue();
  }

  /**
   * Sets the hour value (0-23).
   *
   * @param hour
   *          the hour to set
   */
  public void setHour(int hour) {
    ignoreChange = true;
    try {
      hourModel.setValue(Math.max(0, Math.min(23, hour)));
    }
    finally {
      ignoreChange = false;
    }
  }

  /**
   * Gets the current minute value (0-59).
   *
   * @return the current minute
   */
  public int getMinute() {
    return minuteModel.getNumber().intValue();
  }

  /**
   * Sets the minute value (0-59).
   *
   * @param minute
   *          the minute to set
   */
  public void setMinute(int minute) {
    ignoreChange = true;
    try {
      minuteModel.setValue(Math.max(0, Math.min(59, minute)));
    }
    finally {
      ignoreChange = false;
    }
  }

  /**
   * Sets the time from a Calendar.
   *
   * @param calendar
   *          the calendar to extract time from
   */
  public void setTime(Calendar calendar) {
    setHour(calendar.get(Calendar.HOUR_OF_DAY));
    setMinute(calendar.get(Calendar.MINUTE));
  }
}

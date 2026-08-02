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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpinnerNumberModel;

import org.tinymediamanager.core.TmmResourceBundle;

/**
 * The class CalendarPanel is used to display a calendar like panel for date choosing
 * 
 * @author Manuel Laggner
 */
class CalendarPanel extends JPanel implements PropertyChangeListener {
  private Calendar calendar;
  private boolean  noDate;
  private boolean  allowNull = false;
  protected Locale locale;

  MonthComboBox    monthComboBox;
  YearSpinner      yearSpinner;
  DayPanel         dayPanel;
  TimePanel        timePanel;

  private JButton  noDateButton;

  public CalendarPanel(Date date) {
    this(date, false);
  }

  /**
   * Creates a new CalendarPanel with the given date and optional time display.
   *
   * @param date
   *          the initial date or null
   * @param showTime
   *          true to show time spinners, false for date only
   */
  public CalendarPanel(Date date, boolean showTime) {
    setLayout(new BorderLayout());
    // transparent, so the popup menu background is used (looks like a popup menu)
    setOpaque(false);

    locale = Locale.getDefault();
    calendar = Calendar.getInstance(this.locale);

    JPanel monthYearPanel = new JPanel();
    monthYearPanel.setLayout(new BorderLayout());
    monthYearPanel.setOpaque(false);

    monthComboBox = new MonthComboBox();
    yearSpinner = new YearSpinner();

    monthYearPanel.add(monthComboBox, BorderLayout.WEST);
    monthYearPanel.add(yearSpinner, BorderLayout.CENTER);
    monthYearPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));

    dayPanel = new DayPanel();
    dayPanel.setMonth(monthComboBox.getSelectedIndex());
    dayPanel.setYear((int) yearSpinner.getValue());
    dayPanel.addPropertyChangeListener(this);
    dayPanel.setLocale(this.locale);

    monthComboBox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        int index = monthComboBox.getSelectedIndex();
        Calendar c = (Calendar) calendar.clone();
        c.set(Calendar.MONTH, index);
        setCalendar(c, false);
        dayPanel.setMonth(index);
      }
    });

    yearSpinner.addChangeListener(e -> {
      SpinnerNumberModel model = (SpinnerNumberModel) yearSpinner.getModel();
      int value = model.getNumber().intValue();
      Calendar c = (Calendar) calendar.clone();
      c.set(Calendar.YEAR, value);
      setCalendar(c, false);
      dayPanel.setYear(value);
    });
    add(monthYearPanel, BorderLayout.NORTH);

    // build center panel with day grid and optionally time panel
    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
    centerPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    centerPanel.setOpaque(false);
    centerPanel.add(dayPanel);

    if (showTime) {
      timePanel = new TimePanel();
      timePanel.addPropertyChangeListener(this);
      centerPanel.add(timePanel);
    }

    add(centerPanel, BorderLayout.CENTER);

    JPanel specialButtonPanel = new JPanel();

    JButton todayButton = new JButton();
    todayButton.addActionListener(e -> {
      if (showTime) {
        setDate(new Date());
      }
      else {
        // preserve current time components, just set today's date
        Calendar now = Calendar.getInstance();
        Calendar c = (Calendar) calendar.clone();
        c.set(Calendar.YEAR, now.get(Calendar.YEAR));
        c.set(Calendar.MONTH, now.get(Calendar.MONTH));
        c.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));
        setDate(c.getTime());
      }
    });

    noDateButton = new JButton();
    noDateButton.addActionListener(e -> setDate(null));
    noDateButton.setVisible(allowNull);

    specialButtonPanel.setLayout(new GridLayout(1, 3));
    specialButtonPanel.setOpaque(false);
    todayButton.setText(TmmResourceBundle.getString("Button.today"));
    specialButtonPanel.add(todayButton);

    specialButtonPanel.add(new JLabel(""));

    noDateButton.setText(TmmResourceBundle.getString("Button.nodate"));
    specialButtonPanel.add(noDateButton);
    specialButtonPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

    add(specialButtonPanel, BorderLayout.SOUTH);

    noDate = (date == null) && allowNull;

    if (date != null) {
      calendar.setTime(date);
    }

    setCalendar(calendar);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals("day")) {
      noDate = false;
      Calendar c = (Calendar) calendar.clone();
      c.set(Calendar.DAY_OF_MONTH, (Integer) evt.getNewValue());
      setCalendar(c, false);
      firePropertyChange("day", evt.getOldValue(), evt.getNewValue());
    }
    else if (evt.getPropertyName().equals("date")) {
      setDate((Date) evt.getNewValue());
    }
    else if (evt.getPropertyName().equals("time") && timePanel != null) {
      // update time from TimePanel spinners - do NOT fire "day" to keep the popup open
      Calendar c = (Calendar) calendar.clone();
      c.set(Calendar.HOUR_OF_DAY, timePanel.getHour());
      c.set(Calendar.MINUTE, timePanel.getMinute());
      calendar = c;
      firePropertyChange("time", null, c.getTime());
    }
  }

  /**
   * Returns the calendar, or null if no date is selected.
   *
   * @return the value of the calendar, or null
   */
  public Calendar getCalendar() {
    if (noDate) {
      return null;
    }
    return calendar;
  }

  /**
   * Sets the calendar
   * 
   * @param calendar
   *          the new calendar
   */
  public void setCalendar(Calendar calendar) {
    setCalendar(calendar, true);
  }

  /**
   * Sets the calendar attribute of the JCalendar object
   * 
   * @param newCalendar
   *          the new calendar value (or null for no date)
   * @param update
   *          also update the UI controls
   */
  private void setCalendar(Calendar newCalendar, boolean update) {
    if (newCalendar == null) {
      if (!allowNull || noDate) {
        return;
      }
      noDate = true;
      dayPanel.clearSelection();
      if (timePanel != null) {
        timePanel.setHour(0);
        timePanel.setMinute(0);
      }
      firePropertyChange("calendar", calendar, null);
      return;
    }

    noDate = false;
    Calendar oldCalendar = calendar;
    calendar = newCalendar;

    if (update) {
      yearSpinner.setValue(newCalendar.get(Calendar.YEAR));
      monthComboBox.setSelectedIndex(newCalendar.get(Calendar.MONTH));
      dayPanel.setCalendar(newCalendar);
      dayPanel.setDay(newCalendar.get(Calendar.DATE));
      if (timePanel != null) {
        timePanel.setHour(newCalendar.get(Calendar.HOUR_OF_DAY));
        timePanel.setMinute(newCalendar.get(Calendar.MINUTE));
      }
    }

    firePropertyChange("calendar", oldCalendar, calendar);
  }

  /**
   * Returns a Date object, or null if no date is selected.
   * 
   * @return a date object constructed from the calendar, or null
   */
  public Date getDate() {
    if (noDate) {
      return null;
    }
    return new Date(calendar.getTimeInMillis());
  }

  /**
   * Sets whether null values (no date) are allowed. When disabled, the "No date" button is hidden and setting a null date is ignored.
   *
   * @param allowNull
   *          true if null values are allowed (default), false otherwise
   */
  public void setAllowNull(boolean allowNull) {
    boolean changed = this.allowNull != allowNull;
    this.allowNull = allowNull;
    noDateButton.setVisible(allowNull);
    revalidate();
    repaint();

    if (!allowNull && noDate) {
      setDate(calendar.getTime());
    }
  }

  /**
   * Sets the date. Fires the property change "date". A null value clears the date (no date selected).
   * 
   * @param date
   *          the new date (or null for no date).
   */
  public void setDate(Date date) {
    if (date == null) {
      if (!allowNull || noDate) {
        return;
      }
      Date oldDate = getDate();
      noDate = true;
      dayPanel.clearSelection();
      if (timePanel != null) {
        timePanel.setHour(0);
        timePanel.setMinute(0);
      }
      firePropertyChange("date", oldDate, null);
      firePropertyChange("day", 0, -1);
      return;
    }

    noDate = false;
    Date oldDate = calendar.getTime();
    calendar.setTime(date);
    int year = calendar.get(Calendar.YEAR);
    int month = calendar.get(Calendar.MONTH);
    int day = calendar.get(Calendar.DAY_OF_MONTH);

    yearSpinner.setValue(year);
    monthComboBox.setSelectedIndex(month);
    dayPanel.setCalendar(calendar);
    dayPanel.setDay(day);

    if (timePanel != null) {
      timePanel.setHour(calendar.get(Calendar.HOUR_OF_DAY));
      timePanel.setMinute(calendar.get(Calendar.MINUTE));
    }

    firePropertyChange("date", oldDate, date);
  }
}

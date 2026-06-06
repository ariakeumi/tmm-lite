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

  private JButton  noDateButton;

  public CalendarPanel(Date date) {
    setLayout(new BorderLayout());

    locale = Locale.getDefault();
    calendar = Calendar.getInstance(this.locale);

    JPanel monthYearPanel = new JPanel();
    monthYearPanel.setLayout(new BorderLayout());

    monthComboBox = new MonthComboBox();
    yearSpinner = new YearSpinner();

    monthYearPanel.add(monthComboBox, BorderLayout.WEST);
    monthYearPanel.add(yearSpinner, BorderLayout.CENTER);
    monthYearPanel.setBorder(BorderFactory.createEmptyBorder());

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
    add(dayPanel, BorderLayout.CENTER);

    JPanel specialButtonPanel = new JPanel();

    JButton todayButton = new JButton();
    todayButton.addActionListener(e -> setDate(new Date()));

    noDateButton = new JButton();
    noDateButton.addActionListener(e -> setDate(null));
    noDateButton.setVisible(allowNull);

    specialButtonPanel.setLayout(new GridLayout(1, 3));
    todayButton.setText(TmmResourceBundle.getString("Button.today"));
    specialButtonPanel.add(todayButton);

    specialButtonPanel.add(new JLabel(""));

    noDateButton.setText(TmmResourceBundle.getString("Button.nodate"));
    specialButtonPanel.add(noDateButton);

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

    firePropertyChange("date", oldDate, date);
  }
}

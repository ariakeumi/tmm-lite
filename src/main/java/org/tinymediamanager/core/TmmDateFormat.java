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

package org.tinymediamanager.core;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

/**
 * the class {@link TmmDateFormat} is a utility class to get the best possible date format:<br />
 * it tries to parse the preferred formats out of the system settings in Windows, macOS and Linux.<br />
 * Inspired by IntelliJ
 * {@see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/util/text/DateFormatUtil.java#L269}
 *
 * @author Manuel Laggner
 */
public class TmmDateFormat {
  public static final Logger LOGGER = LoggerFactory.getLogger(TmmDateFormat.class);

  public enum DateFormatStyle {
    NATIVE("Settings.ui.date.system"),
    INTERNATIONAL("yyyy-MM-dd (2024-12-31)"),
    US_LONG("MM/dd/yyyy (12/31/2024)"),
    UK_LONG("dd/MM/yyyy (31/12/2024)"),
    DE_LONG("dd.MM.yyyy (31.12.2024)"),
    HR_LONG("dd.MM.yyyy. (31.12.2024.)"),
    CN_LONG("yyyy/MM/dd (2024/12/31)"),

    US_SHORT("MM/dd/yy (12/31/24)"),
    UK_SHORT("dd/MM/yy (31/12/24)"),
    DE_SHORT("dd.MM.yy (31.12.24)"),
    HR_SHORT("dd.MM.yy (31.12.24.)"),
    CN_SHORT("yy/MM/dd (24/12/31)");

    private final String displayName;

    DateFormatStyle(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      String result = TmmResourceBundle.getString(displayName);
      if ("???".equalsIgnoreCase(result)) {
        return displayName;
      }
      return result;
    }
  }

  public enum TimeFormatStyle {
    NATIVE("Settings.ui.time.system"),
    TWENTY_FOUR_HOURS("hh:mm (23:59)"),
    AM_PM("HH:mm a (11:59 PM)");

    private final String displayName;

    TimeFormatStyle(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      String result = TmmResourceBundle.getString(displayName);
      if ("???".equalsIgnoreCase(result)) {
        return displayName;
      }
      return result;
    }
  }

  private static final String                               NATIVE_DATE_MEDIUM;

  private static final String                               NATIVE_TIME_SHORT;
  private static final String                               NATIVE_TIME_MEDIUM;

  private static final Map<String, ThreadLocal<DateFormat>> DATEFORMAT_CACHE = new ConcurrentHashMap<>();

  static {
    DateFormat[] formats = getDateTimeFormats();

    if (formats[1] instanceof SimpleDateFormat simpleDateFormat) {
      NATIVE_DATE_MEDIUM = simpleDateFormat.toPattern();
    }
    else {
      NATIVE_DATE_MEDIUM = getNativeDatePattern(Locale.getDefault());
    }

    if (formats[3] instanceof SimpleDateFormat simpleDateFormat) {
      NATIVE_TIME_SHORT = simpleDateFormat.toPattern();
    }
    else {
      NATIVE_TIME_SHORT = ((SimpleDateFormat) DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())).toPattern();
    }

    if (formats[4] instanceof SimpleDateFormat simpleDateFormat) {
      NATIVE_TIME_MEDIUM = simpleDateFormat.toPattern();
    }
    else {
      NATIVE_TIME_MEDIUM = ((SimpleDateFormat) DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault())).toPattern();
    }
  }

  /**
   * Get date/time formats from the system settings
   * 
   * @return the date/time formats
   */
  private static DateFormat[] getDateTimeFormats() {
    boolean jnaAvailable = false;

    try {
      int ptrSize = Native.POINTER_SIZE;
      jnaAvailable = true;
    }
    catch (Throwable e) {
      LOGGER.error("Could not load JNA - '{}'", e.getMessage());
    }

    DateFormat[] formats = null;
    try {
      if (SystemUtils.IS_OS_MAC && jnaAvailable) {
        formats = getMacFormats();
      }
      else if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_UNIX) {
        formats = getUnixFormats();
      }
      else if (SystemUtils.IS_OS_WINDOWS && jnaAvailable) {
        formats = getWindowsFormats();
      }
    }
    catch (Throwable e) {
      LOGGER.error("Could not load native date formats -'{}'", e.getMessage());
    }

    if (formats == null || formats.length < 11) {
      // @formatter:off
      formats = new DateFormat[] {
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()),
        new SimpleDateFormat(getNativeDatePattern(Locale.getDefault())),
        DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()),
              
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()),
        DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault()),
              
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()),
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault()), 
        new SimpleDateFormat(getNativeDatePattern(Locale.getDefault()) + " " + ((SimpleDateFormat)DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())).toPattern()),
        new SimpleDateFormat(getNativeDatePattern(Locale.getDefault()) + " " + ((SimpleDateFormat)DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault())).toPattern()),
        DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, Locale.getDefault()),
        DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM, Locale.getDefault())
      };
      // @formatter:on
    }

    return formats;
  }

  /**
   * Get a medium date pattern for the given locale. This is a fallback if no native pattern could be determined.
   * 
   * @param locale
   *          the locale to get the pattern for
   * @return the medium date pattern
   */
  private static String getNativeDatePattern(Locale locale) {
    String country = locale.getCountry().toUpperCase();

    // return hardcoded patterns for some major countries
    return switch (country) {
      case "DE", "AT", "CH", "PL", "CZ", "SK", "HU" -> "dd.MM.yyyy";
      case "FR", "BE", "IT", "ES", "PT", "GB", "IE" -> "dd/MM/yyyy";
      case "HR", "BA", "SI", "RS", "ME", "MK" -> "dd.MM.yyyy.";
      case "US" -> "MM/dd/yyyy";
      case "CN", "JP", "KR" -> "yyyy/MM/dd";
      default -> "yyyy-MM-dd";
    };
  }

  private interface CF extends Library {
    // https://developer.apple.com/documentation/corefoundation/cfdateformatter/date_formatter_styles
    long kCFDateFormatterNoStyle     = 0;
    long kCFDateFormatterShortStyle  = 1;
    long kCFDateFormatterMediumStyle = 2;
    long kCFDateFormatterLongStyle   = 3;

    class CFRange extends Structure implements Structure.ByValue {
      @Override
      protected List<String> getFieldOrder() {
        return Arrays.asList("location", "length");
      }

      public long location;
      public long length;

      public CFRange(long location, long length) {
        this.location = location;
        this.length = length;
      }
    }

    Pointer CFDateFormatterCreate(Pointer allocator, Pointer locale, long dateStyle, long timeStyle);

    Pointer CFDateFormatterGetFormat(Pointer formatter);

    long CFStringGetLength(Pointer str);

    void CFStringGetCharacters(Pointer str, CFRange range, char[] buffer);

    void CFRelease(Pointer p);
  }

  // platform-specific patterns: http://www.unicode.org/reports/tr35/tr35-31/tr35-dates.html#Date_Format_Patterns

  /**
   * Get date/time formats from macOS settings
   * 
   * @return the date/time formats
   */
  private static DateFormat[] getMacFormats() {
    CF cf = Native.load("CoreFoundation", CF.class);
    // @formatter:off
    return new DateFormat[] { 
      getMacFormat(cf, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterNoStyle), // short date
      getMacFormat(cf, CF.kCFDateFormatterMediumStyle, CF.kCFDateFormatterNoStyle), // medium date
      getMacFormat(cf, CF.kCFDateFormatterLongStyle, CF.kCFDateFormatterNoStyle), // long date     
            
      getMacFormat(cf, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterShortStyle), // short time
      getMacFormat(cf, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterLongStyle), // long time (medium not available acc. to docs)
            
      getMacFormat(cf, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterShortStyle), // short date short time
      getMacFormat(cf, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterMediumStyle), // short date medium time
      getMacFormat(cf, CF.kCFDateFormatterMediumStyle, CF.kCFDateFormatterShortStyle), // medium date short time
      getMacFormat(cf, CF.kCFDateFormatterMediumStyle, CF.kCFDateFormatterMediumStyle), // medium date medium time
      getMacFormat(cf, CF.kCFDateFormatterLongStyle, CF.kCFDateFormatterShortStyle), // long date short time
      getMacFormat(cf, CF.kCFDateFormatterLongStyle, CF.kCFDateFormatterMediumStyle) // long date medium time
    };
  }

  /**
   * Get a date format from macOS settings
   * @param cf the CF library
   * @param dateStyle the date style
   * @param timeStyle the time style
   * @return the date format
   */
  private static DateFormat getMacFormat(CF cf, long dateStyle, long timeStyle) {
    Pointer formatter = cf.CFDateFormatterCreate(null, null, dateStyle, timeStyle);
    if (formatter == null)
      throw new IllegalStateException("CFDateFormatterCreate: null");
    try {
      Pointer format = cf.CFDateFormatterGetFormat(formatter);
      int length = (int) cf.CFStringGetLength(format);
      char[] buffer = new char[length];
      cf.CFStringGetCharacters(format, new CF.CFRange(0, length), buffer);
      return formatFromString(new String(buffer));
    }
    finally {
      cf.CFRelease(formatter);
    }
  }

  /**
   * Get date/time formats from Unix/Linux settings
   * @return the date/time formats
   */
  private static DateFormat[] getUnixFormats() {
    String localeStr = System.getenv("LC_TIME");
    if (localeStr == null) {
      return null;
    }

    localeStr = localeStr.strip();
    int p = localeStr.indexOf('.');
    if (p > 0)
      localeStr = localeStr.substring(0, p);
    p = localeStr.indexOf('@');
    if (p > 0)
      localeStr = localeStr.substring(0, p);

    Locale locale;
    p = localeStr.indexOf('_');
    if (p < 0) {
      locale = new Locale(localeStr);
    }
    else {
      locale = new Locale(localeStr.substring(0, p), localeStr.substring(p + 1));
    }
    // @formatter:off
    return new DateFormat[] {
      DateFormat.getDateInstance(DateFormat.SHORT, locale),
      DateFormat.getDateInstance(DateFormat.MEDIUM, locale),
      DateFormat.getDateInstance(DateFormat.LONG, locale),

      DateFormat.getTimeInstance(DateFormat.SHORT, locale),
      DateFormat.getTimeInstance(DateFormat.MEDIUM, locale),

      DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale),
      DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale),
      DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale),
      DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale),
      DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale),
      DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM, locale),
    };
    // @formatter:on
  }

  @SuppressWarnings("SpellCheckingInspection")
  private interface Kernel32 extends StdCallLibrary {
    int LOCALE_SSHORTDATE  = 0x0000001F;
    int LOCALE_SLONGDATE   = 0x00000020;

    int LOCALE_SSHORTTIME  = 0x00000079;
    int LOCALE_STIMEFORMAT = 0x00001003;

    int GetLocaleInfoEx(String localeName, int lcType, char[] lcData, int dataSize);

    int GetLastError();
  }

  /**
   * Get date/time formats from Windows settings
   * 
   * @return the date/time formats
   */
  private static DateFormat[] getWindowsFormats() {
    Kernel32 kernel32 = Native.load("Kernel32", Kernel32.class);
    int bufferSize = 128, rv;
    char[] buffer = new char[bufferSize];

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_SSHORTDATE, buffer, bufferSize);
    if (rv < 2) {
      throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    }
    String shortDate = fixWindowsFormat(new String(buffer, 0, rv - 1));

    // no medium date available in windows
    String mediumDate = shortDate;

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_SLONGDATE, buffer, bufferSize);
    if (rv < 2) {
      throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    }
    String longDate = fixWindowsFormat(new String(buffer, 0, rv - 1));

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_SSHORTTIME, buffer, bufferSize);
    if (rv < 2) {
      throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    }
    String shortTime = fixWindowsFormat(new String(buffer, 0, rv - 1));

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_STIMEFORMAT, buffer, bufferSize);
    if (rv < 2) {
      throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    }
    String mediumTime = fixWindowsFormat(new String(buffer, 0, rv - 1));

    // @formatter:off
    return new DateFormat[] {
      formatFromString(shortDate),
      formatFromString(mediumDate),
      formatFromString(longDate),

      formatFromString(shortTime),
      formatFromString(mediumTime),

      formatFromString(shortDate + " " + shortTime),
      formatFromString(shortDate + " " + mediumTime),
      formatFromString(mediumDate + " " + shortTime),
      formatFromString(mediumDate + " " + mediumTime),
      formatFromString(longDate + " " + shortTime),
      formatFromString(longDate + " " + mediumTime)
    };
    // @formatter:on
  }

  /**
   * Fix some known issues with Windows date/time patterns
   * 
   * @param format
   *          the format to fix
   * @return the fixed format
   */
  private static String fixWindowsFormat(String format) {
    format = format.replaceAll("g+", "G");
    format = Strings.CS.replace(format, "tt", "a");
    return format;
  }

  /**
   * Create a DateFormat from a format string
   * 
   * @param format
   *          the format string
   * @return the DateFormat
   */
  private static DateFormat formatFromString(String format) {
    try {
      return new SimpleDateFormat(format.strip());
    }
    catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("unrecognized format string '" + format + "'");
    }
  }

  /**
   * Get the date pattern according to the settings
   * 
   * @return the date pattern
   */
  private static String getDatePattern() {
    return switch (Settings.getInstance().getDateFormatStyle()) {
      case NATIVE -> NATIVE_DATE_MEDIUM;
      case US_LONG -> "MM/dd/yyyy";
      case UK_LONG -> "dd/MM/yyyy";
      case DE_LONG -> "dd.MM.yyyy";
      case HR_LONG -> "dd.MM.yyyy.";
      case CN_LONG -> "yyyy/MM/dd";
      case US_SHORT -> "MM/dd/yy";
      case UK_SHORT -> "dd/MM/yy";
      case DE_SHORT -> "dd.MM.yy";
      case HR_SHORT -> "dd.MM.yy.";
      case CN_SHORT -> "yy/MM/dd";
      default -> "yyyy-MM-dd";
    };
  }

  /**
   * Get the short time pattern
   * 
   * @return the short time pattern
   */
  private static String getTimeShortPattern() {
    return switch (Settings.getInstance().getTimeFormatStyle()) {
      case NATIVE -> NATIVE_TIME_SHORT;
      case AM_PM -> "hh:mm a";
      default -> "HH:mm";
    };
  }

  /**
   * Get the medium time pattern
   * 
   * @return the medium time pattern
   */
  private static String getTimeMediumPattern() {
    return switch (Settings.getInstance().getTimeFormatStyle()) {
      case NATIVE -> NATIVE_TIME_MEDIUM;
      case AM_PM -> "hh:mm:ss a";
      default -> "HH:mm:ss";
    };
  }

  /**
   * Get a cached date format for the given pattern
   * 
   * @param pattern
   *          the pattern
   * @return the date format
   */
  private static DateFormat getCachedDateFormat(String pattern) {
    return DATEFORMAT_CACHE.computeIfAbsent(pattern, p -> ThreadLocal.withInitial(() -> formatFromString(p))).get();
  }

  /**
   * Get the date format
   * 
   * @return the date format
   */
  public static DateFormat getDateFormat() {
    return getCachedDateFormat(getDatePattern());
  }

  /**
   * Get the date and short time format
   * 
   * @return the date and short time format
   */
  public static DateFormat getDateShortTimeFormat() {
    return getCachedDateFormat(getDatePattern() + " " + getTimeShortPattern());
  }

  /**
   * Get the date and medium time format
   * 
   * @return the date and medium time format
   */
  public static DateFormat getDateMediumTimeFormat() {
    return getCachedDateFormat(getDatePattern() + " " + getTimeMediumPattern());
  }
}

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.logcat;

import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatLongEpochMessageParser;
import com.android.ddmlib.logcat.LogCatMessage;
import com.intellij.diagnostic.logging.DefaultLogFormatter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Class which handles parsing the output from logcat and reformatting it to its final form before
 * displaying it to the user.
 *
 * <p>By default, this formatter prints a full header + message to the console, but you can modify
 * {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING} (setting it to a value returned by
 * {@link #createCustomFormat(boolean, boolean, boolean, boolean)}) to hide parts of the header.
 */
public final class AndroidLogcatFormatter extends DefaultLogFormatter {
  private static final String CONTINUATION_LINE = "\n    ";
  private static final String FULL_FORMAT = createCustomFormat(true, true, true, true);
  private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral(' ')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
    .toFormatter(Locale.ROOT);

  @NotNull private final ZoneId myTimeZone;
  private final AndroidLogcatPreferences myPreferences;

  public AndroidLogcatFormatter(@NotNull ZoneId timeZone, @NotNull AndroidLogcatPreferences preferences) {
    myTimeZone = timeZone;
    myPreferences = preferences;
  }

  /**
   * Creates a format string for the formatMessage methods that take one.
   */
  @NotNull
  public static String createCustomFormat(boolean showTime, boolean showPid, boolean showPackage, boolean showTag) {
    StringBuilder builder = new StringBuilder();
    if (showTime) {
      builder.append("%1$s ");
    }
    if (showPid) {
      // Slightly different formatting if we show BOTH PID and package instead of one or the other
      builder.append("%2$s").append(showPackage ? '/' : ' ');
    }
    if (showPackage) {
      builder.append("%3$s ");
    }
    builder.append("%4$c");
    if (showTag) {
      builder.append("/%5$s");
    }
    builder.append(": %6$s");
    return builder.toString();
  }

  @NotNull
  @Override
  public String formatMessage(@NotNull String message) {
    return formatMessage(LogcatJson.fromJson(message));
  }

  @NotNull
  String formatMessage(@NotNull LogCatMessage message) {
    String format = myPreferences.LOGCAT_FORMAT_STRING.isEmpty() ? FULL_FORMAT : myPreferences.LOGCAT_FORMAT_STRING;
    return formatMessage(format, message.getHeader(), message.getMessage());
  }

  @NotNull
  public String formatMessage(@NotNull String format, @NotNull LogCatHeader header, @NotNull String message) {
    Instant timestamp = header.getTimestamp();

    Object timestampString = myPreferences.SHOW_AS_SECONDS_SINCE_EPOCH
                             ? LogCatLongEpochMessageParser.EPOCH_TIME_FORMATTER.format(timestamp)
                             : DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(timestamp, myTimeZone));

    Object processIdThreadId = header.getPid() + "-" + header.getTid();
    Object priority = header.getLogLevel().getPriorityLetter();

    // Replacing spaces with non breaking spaces makes parsing easier later
    Object tag = header.getTag().replace(' ', '\u00A0');

    return String.format(
      Locale.ROOT,
      format,
      timestampString,
      processIdThreadId,
      header.getAppName(),
      priority,
      tag,
      message.replaceAll("\n", CONTINUATION_LINE));
  }
}

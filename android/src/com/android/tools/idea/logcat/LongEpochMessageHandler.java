/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatLongEpochMessageParser;
import com.android.ddmlib.logcat.LogCatMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link MessageParser} dedicated to the long epoch logcat header format that also supports a {@link #format} method for display
 * purposes.
 */
final class LongEpochMessageHandler implements MessageParser {
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

  private static final Pattern DATE_TIME = Pattern.compile("\\d+-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d.\\d\\d\\d");

  private static final Pattern EPOCH_TIME_HEADER_MESSAGE = Pattern.compile("^("
                                                                           + LogCatLongEpochMessageParser.EPOCH_TIME
                                                                           + ") +("
                                                                           + PROCESS_ID
                                                                           + ")-("
                                                                           + THREAD_ID
                                                                           + ")/("
                                                                           + PACKAGE
                                                                           + ") ("
                                                                           + PRIORITY
                                                                           + ")/("
                                                                           + TAG
                                                                           + "): ("
                                                                           + MESSAGE
                                                                           + ")$");

  private static final Pattern DATE_TIME_HEADER_MESSAGE = Pattern.compile("^("
                                                                          + DATE_TIME
                                                                          + ") +("
                                                                          + PROCESS_ID
                                                                          + ")-("
                                                                          + THREAD_ID
                                                                          + ")/("
                                                                          + PACKAGE
                                                                          + ") ("
                                                                          + PRIORITY
                                                                          + ")/("
                                                                          + TAG
                                                                          + "): ("
                                                                          + MESSAGE
                                                                          + ")$");

  private final AndroidLogcatPreferences myPreferences;
  private final ZoneId myTimeZone;

  LongEpochMessageHandler(@NotNull AndroidLogcatPreferences preferences, @NotNull ZoneId timeZone) {
    myPreferences = preferences;
    myTimeZone = timeZone;
  }

  @NotNull
  public String format(@NotNull String format, @NotNull LogCatHeader header, @NotNull String message) {
    Instant timestamp = header.getTimestamp();

    Object timestampString = myPreferences.SHOW_AS_SECONDS_SINCE_EPOCH
                             ? LogCatLongEpochMessageParser.EPOCH_TIME_FORMATTER.format(timestamp)
                             : DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(timestamp, myTimeZone));

    Object processIdThreadId = header.getPid() + "-" + header.getTid();
    Object priority = header.getLogLevel().getPriorityLetter();

    // Replacing spaces with non breaking spaces makes parsing easier later
    Object tag = header.getTag().replace(' ', '\u00A0');

    return String.format(Locale.ROOT, format, timestampString, processIdThreadId, header.getAppName(), priority, tag, message);
  }

  @Nullable
  @Override
  public LogCatMessage tryParse(@NotNull String message) {
    Matcher matcher =
      myPreferences.SHOW_AS_SECONDS_SINCE_EPOCH ? EPOCH_TIME_HEADER_MESSAGE.matcher(message) : DATE_TIME_HEADER_MESSAGE.matcher(message);

    if (!matcher.matches()) {
      return null;
    }

    LogLevel priority = LogLevel.getByLetterString(matcher.group(5));
    assert priority != null;

    int processId = Integer.parseInt(matcher.group(2));
    int threadId = Integer.parseInt(matcher.group(3));
    String tag = matcher.group(6);

    Instant timestampInstant;

    if (myPreferences.SHOW_AS_SECONDS_SINCE_EPOCH) {
      timestampInstant = LogCatLongEpochMessageParser.EPOCH_TIME_FORMATTER.parse(matcher.group(1), Instant::from);
    }
    else {
      LocalDateTime timestampDateTime = LocalDateTime.parse(matcher.group(1), DATE_TIME_FORMATTER);
      timestampInstant = timestampDateTime.toInstant(myTimeZone.getRules().getOffset(timestampDateTime));
    }

    LogCatHeader header = new LogCatHeader(priority, processId, threadId, /* package= */ matcher.group(4), tag, timestampInstant);
    return new LogCatMessage(header, /* message= */ matcher.group(7));
  }
}

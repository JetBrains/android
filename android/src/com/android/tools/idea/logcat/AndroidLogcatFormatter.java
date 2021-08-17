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
import com.android.ddmlib.logcat.LogCatMessage;
import com.intellij.diagnostic.logging.DefaultLogFormatter;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class which handles parsing the output from logcat and reformatting it to its final form before
 * displaying it to the user.
 *
 * <p>By default, this formatter prints a full header + message to the console, but you can modify
 * {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING} (setting it to a value returned by
 * {@link #createCustomFormat(boolean, boolean, boolean, boolean)}) to hide parts of the header.
 */
public final class AndroidLogcatFormatter extends DefaultLogFormatter {
  private static final String FULL_FORMAT = createCustomFormat(true, true, true, true);

  /**
   * If a logcat message has more than one line, all followup lines are marked with this pattern.
   * The user will not see this formatting, however; the continuation character will be removed and
   * replaced with an indent.
   */
  @NonNls private static final Pattern CONTINUATION_PATTERN = Pattern.compile("^\\+ (.*)$");

  public static final CharSequence CONTINUATION_INDENT = "    ";

  @NotNull private final LongEpochMessageHandler myLongEpochHandler;
  @NotNull private final LongMessageParser myLongFormatterParser;

  private final AndroidLogcatPreferences myPreferences;

  public AndroidLogcatFormatter(@NotNull ZoneId timeZone, @NotNull AndroidLogcatPreferences preferences) {
    myLongEpochHandler = new LongEpochMessageHandler(preferences, timeZone);
    myLongFormatterParser = new LongMessageParser();

    myPreferences = preferences;
  }

  /**
   * Given data parsed from a line of logcat, return a final, formatted string that represents a
   * line of text we should show to the user in the logcat console. (However, this line may be
   * further processed if {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING} is set.)
   *
   * <p>Note: If a logcat message contains multiple lines, you should only use this for the first
   * line, and {@link #formatContinuation(String)} for each additional line.
   */
  @NotNull
  String formatMessageFull(@NotNull LogCatHeader header, @NotNull String message) {
    return formatMessage(FULL_FORMAT, header, message);
  }

  /**
   * When parsing a multi-line message from logcat, you should format all lines after the first as
   * a continuation. This marks the line in a special way so this formatter is aware that it is a
   * part of a larger whole.
   */
  @NotNull
  public static String formatContinuation(@NotNull String message) {
    return String.format("+ %s", message);
  }

  /**
   * Creates a format string for the formatMessage methods that take one.
   */
  @NotNull
  public static String createCustomFormat(boolean showTime, boolean showPid, boolean showPackage, boolean showTag) {
    // Note: if all values are true, the format returned must be matchable by MESSAGE_WITH_HEADER
    // or else parseMessage will fail later.
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
  public String formatMessage(@NotNull String format, @NotNull LogCatHeader header, @NotNull String message) {
    return myLongEpochHandler.format(format, header, message);
  }

  /**
   * Parse a message that was encoded using {@link #formatMessageFull(LogCatHeader, String)}
   */
  @NotNull
  LogCatMessage parseMessage(@NotNull String message) {
    LogCatMessage result = tryParseMessage(message);
    if (result == null) {
      throw new IllegalArgumentException("Invalid message doesn't match expected logcat pattern: " + message);
    }

    return result;
  }

  /**
   * Returns the result of {@link #parseMessage(String)} or {@code null} if the format of the input
   * text doesn't match.
   */
  @Nullable
  LogCatMessage tryParseMessage(@NotNull String message) {
    LogCatMessage logcatMessage = myLongEpochHandler.tryParse(message);

    if (logcatMessage != null) {
      return logcatMessage;
    }

    logcatMessage = myLongFormatterParser.tryParse(message);

    return logcatMessage;
  }

  /**
   * Parse a message that was encoded using {@link #formatContinuation(String)}, returning the
   * message within or {@code null} if the format of the input text doesn't match.
   */
  @Nullable
  public static String tryParseContinuation(@NotNull String msg) {
    Matcher matcher = CONTINUATION_PATTERN.matcher(msg);
    if (!matcher.matches()) {
      return null;
    }

    return matcher.group(1);
  }

  @Override
  public String formatPrefix(String prefix) {
    if (prefix.isEmpty()) {
      return prefix;
    }

    // If the prefix is set, it contains log lines which were initially skipped over by our
    // filter but were belatedly accepted later.
    return Arrays.stream(prefix.split("\n", -1))
      .map(this::formatMessage)
      .collect(Collectors.joining("\n"));
  }

  @NotNull
  @Override
  public String formatMessage(@NotNull String message) {
    String continuation = tryParseContinuation(message);

    if (continuation != null) {
      return CONTINUATION_INDENT + continuation;
    }

    LogCatMessage logcatMessage = tryParseMessage(message);

    if (logcatMessage == null || myPreferences.LOGCAT_FORMAT_STRING.isEmpty()) {
      return message;
    }

    return formatMessage(logcatMessage);
  }

  @NotNull
  String formatMessage(@NotNull LogCatMessage message) {
    String format = myPreferences.LOGCAT_FORMAT_STRING.isEmpty() ? FULL_FORMAT : myPreferences.LOGCAT_FORMAT_STRING;
    return formatMessage(format, message.getHeader(), message.getMessage());
  }
}

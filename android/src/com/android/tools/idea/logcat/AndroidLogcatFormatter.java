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

import com.android.ddmlib.Log;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatTimestamp;
import com.google.common.base.Strings;
import com.intellij.diagnostic.logging.DefaultLogFormatter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class which handles parsing the output from logcat and reformatting it to its final form before
 * displaying it to the user.
 *
 * By default, this formatter prints a full header + message to the console, but you can modify
 * {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING} (setting it to a value returned by
 * {@link #createCustomFormat(boolean, boolean, boolean, boolean)}) to hide parts of the header.
 */
public final class AndroidLogcatFormatter extends DefaultLogFormatter {
  private final AndroidLogcatPreferences myPreferences;

  public AndroidLogcatFormatter(@NotNull AndroidLogcatPreferences preferences) {
    myPreferences = preferences;
  }

  @NonNls private static final Pattern MESSAGE_WITH_HEADER = Pattern.compile(
    "^(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d.\\d+)\\s+" +   // time
    "(\\d+)-(\\d+)/" +                                     // pid-tid
    "(\\S+)\\s+" +                                         // package
    "([A-Z])/" +                                           // log level
    "([^ ]+): " +                                          // tag (be sure it has no spaces)
    "(.*)$"                                                // message
  );

  /**
   * If a logcat message has more than one line, all followup lines are marked with this pattern.
   * The user will not see this formatting, however; the continuation character will be removed and
   * replaced with an indent.
   */
  @NonNls private static final Pattern CONTINUATION_PATTERN = Pattern.compile("^\\+ (.*)$");

  private static final String FULL_FORMAT = createCustomFormat(true, true, true, true);

  // Remember the length of the last header, which we will use to indent any continuation lines
  private int myLastHeaderLength = 0;

  /**
   * Given data parsed from a line of logcat, return a final, formatted string that represents a
   * line of text we should show to the user in the logcat console. (However, this line may be
   * further processed if {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING} is set.)
   *
   * Note: If a logcat message contains multiple lines, you should only use this for the first
   * line, and {@link #formatContinuation(String)} for each additional line.
   */
  @NotNull
  public static String formatMessageFull(@NotNull LogCatHeader header, @NotNull String message) {
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
   * Create a format string for use with {@link #formatMessage(String, String)}. Most commonly you
   * should just assign its result to {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING}, which
   * is checked against to determine what the final format of any logcat line will be.
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
      builder.append("%2$s").append(showPackage?'/':' ');
    }
    if (showPackage) {
      builder.append("%3$s ");
    }
    builder.append("%4$c");
    if (showTag) {
      builder.append("/%5$s");
    }
    builder.append(": %6$s");
    return builder.toString();  }

  /**
   * Helper method useful for previewing what final output will look like given a custom formatter.
   */
  @NotNull
  static String formatMessage(@NotNull String format, @NotNull String msg) {
    if (format.isEmpty()) {
      return msg;
    }

    LogCatMessage message = parseMessage(msg);
    return formatMessage(format, message.getHeader(), message.getMessage());
  }

  @NotNull
  private static String formatMessage(@NotNull String format, @NotNull LogCatHeader header, @NotNull String message) {
    String ids = String.format(Locale.US, "%s-%s", header.getPid(), header.getTid());

    // For parsing later, tags should not have spaces in them. Replace spaces with
    // "no break" spaces, which looks like whitespace but doesn't act like it.
    String tag = header.getTag().replace(' ', '\u00A0');

    return String.format(Locale.US, format,
                         header.getTimestamp(),
                         ids,
                         header.getAppName(),
                         header.getLogLevel().getPriorityLetter(),
                         tag,
                         message);
  }

  /**
   * Parse a message that was encoded using {@link #formatMessageFull(LogCatHeader, String)}
   */
  @NotNull
  public static LogCatMessage parseMessage(@NotNull String msg) {
    LogCatMessage result = tryParseMessage(msg);
    if (result == null) {
      throw new IllegalArgumentException("Invalid message doesn't match expected logcat pattern: " + msg);
    }

    return result;
  }

  /**
   * Returns the result of {@link #parseMessage(String)} or {@code null} if the format of the input
   * text doesn't match.
   */
  @Nullable
  public static LogCatMessage tryParseMessage(@NotNull String msg) {
    final Matcher matcher = MESSAGE_WITH_HEADER.matcher(msg);
    if (!matcher.matches()) {
      return null;
    }

    @SuppressWarnings("ConstantConditions") // matcher.matches verifies all groups below are non-null
    LogCatHeader header = new LogCatHeader(
      Log.LogLevel.getByLetter(matcher.group(5).charAt(0)),
      Integer.parseInt(matcher.group(2)),
      Integer.parseInt(matcher.group(3)),
      matcher.group(4),
      matcher.group(6),
      LogCatTimestamp.fromString(matcher.group(1)));

    String message = matcher.group(7);

    return new LogCatMessage(header, message);
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
    String[] lines = prefix.split("\n");
    StringBuilder sb = new StringBuilder(prefix.length() + (lines.length - 1) * myLastHeaderLength); // Extra space for indents
    for (String line : lines) {
      sb.append(formatMessage(line));
      sb.append('\n');
    }

    return sb.toString();
  }

  @Override
  public String formatMessage(String msg) {
    String continuation = tryParseContinuation(msg);
    if (continuation != null) {
      return Strings.repeat(" ", myLastHeaderLength) + continuation;
    }
    else {
      LogCatMessage message = tryParseMessage(msg);
      if (message != null) {
        String formatted = formatMessage(myPreferences.LOGCAT_FORMAT_STRING, msg);
        myLastHeaderLength = formatted.indexOf(message.getMessage());
        return formatted;
      }
    }

    return msg; // Unknown message format, return as is
  }
}

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

  @NonNls private static final Pattern LOGMESSAGE_PATTERN = Pattern.compile(
    "^(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d.\\d+)\\s+" +   // time
    "(\\d+)-(\\d+)/" +                                     // pid-tid
    "(\\S+)\\s+" +                                         // package
    "([A-Z])/" +                                           // log level
    "([^ ]+): " +                                          // tag (be sure it has no spaces)
    "(.*)$"                                                // message
  );

  private static final String FULL_FORMAT = createCustomFormat(true, true, true, true);

  /**
   * Given data parsed from a line of logcat, return a final, formatted string that represents a
   * line of text we should show to the user in the logcat console. (However, this line may be
   * further processed if {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING} is set.)
   */
  @NotNull
  public static String formatMessageFull(@NotNull LogCatHeader header, @NotNull String message) {
    return formatMessage(FULL_FORMAT, header, message);
  }

  /**
   * Create a format string for use with {@link #formatMessage(String, String)}. Most commonly you
   * should just assign its result to {@link AndroidLogcatPreferences#LOGCAT_FORMAT_STRING}, which
   * is checked against to determine what the final format of any logcat line will be.
   */
  @NotNull
  public static String createCustomFormat(boolean showTime, boolean showPid, boolean showPackage, boolean showTag) {
    // Note: if all values are true, the format returned must be matchable by LOGMESSAGE_PATTERN
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
   * Construct a fake logcat message at the specified level.
   */
  public static String formatMessage(@NotNull Log.LogLevel level, @NotNull String message) {
    LogCatHeader fakeHeader = new LogCatHeader(level, 0, 0, "?", "Internal", LogCatTimestamp.ZERO);
    return formatMessageFull(fakeHeader, message);
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
    final Matcher matcher = LOGMESSAGE_PATTERN.matcher(msg);
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

  @Override
  public String formatMessage(String msg) {
    return formatMessage(myPreferences.LOGCAT_FORMAT_STRING, msg);
  }
}

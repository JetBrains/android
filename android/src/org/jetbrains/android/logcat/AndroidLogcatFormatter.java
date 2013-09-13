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

package org.jetbrains.android.logcat;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.Log;
import com.google.common.primitives.Ints;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.logcat.AndroidLogcatReceiver.LogMessageHeader;
import org.jetbrains.annotations.NonNls;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidLogcatFormatter {
  /**
   * The separator printed out after the tag.
   * The output of {@link #formatMessage(String, org.jetbrains.android.logcat.AndroidLogcatReceiver.LogMessageHeader)} is displayed
   * to users, but is also parsed back by {@link #parseMessage(String)}. In particular, the tag and message strings come directly
   * from the user, and can contain any sequence of characters. So we the unicode colon character to distinguish between
   * where the tag ends and where the message begins. The character could really be anything as long as it is both displayable and
   * meaningful to the user, yet is unlikely to occur in user strings.
   */
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  static final String TAG_SEPARATOR = "\ufe55"; // unicode small colon

  @NonNls
  private static final Pattern LOGMESSAGE_PATTERN =
    Pattern.compile("(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d.\\d+)\\s+" +   // time
                    "(\\d+)-(\\d+)/" +                // pid-tid
                    "(\\S+)\\s+" +                    // package
                    "([A-Z])/" +                      // log level
                    "(.*)" + TAG_SEPARATOR + " " +    // tag
                    "(.*)"                            // message
    );

  public static String formatMessage(String message, LogMessageHeader header) {
    String ids = String.format(Locale.US, "%d-%s", header.myPid, header.myTid);
    return String.format(Locale.US,
                         "%1$s %2$12s/%3$s %4$c/%5$s%6$s %7$s",
                         header.myTime,
                         ids,
                         header.myAppPackage.isEmpty() ? "?" : header.myAppPackage,
                         header.myLogLevel.getPriorityLetter(),
                         header.myTag,
                         TAG_SEPARATOR,
                         message);
  }

  /** Parse a message that was encoded using {@link #formatMessage(String, LogMessageHeader)}. */
  public static Pair<LogMessageHeader,String> parseMessage(String msg) {
    final Matcher matcher = LOGMESSAGE_PATTERN.matcher(msg);
    if (!matcher.matches()) {
      return Pair.create(null, msg);
    }

    LogMessageHeader header = new LogMessageHeader();
    header.myTime = matcher.group(1).trim();
    Integer pid = Ints.tryParse(matcher.group(2));
    header.myPid = pid == null ? 0 : pid;
    header.myTid = matcher.group(3).trim();
    header.myAppPackage = matcher.group(4).trim();
    header.myLogLevel = Log.LogLevel.getByLetter(matcher.group(5).trim().charAt(0));
    header.myTag = matcher.group(6).trim();
    String message = matcher.group(7);

    return Pair.create(header, message);
  }
}

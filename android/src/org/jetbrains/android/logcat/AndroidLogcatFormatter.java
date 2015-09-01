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

import com.android.ddmlib.Log;
import com.google.common.primitives.Ints;
import org.jetbrains.android.logcat.AndroidLogcatReceiver.LogMessageHeader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for interacting with messages from Android Logcat.
 */
public final class AndroidLogcatFormatter {

  @NonNls private static final Pattern LOGMESSAGE_PATTERN = Pattern.compile(
    "^(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d.\\d+)\\s+" +   // time
    "(\\d+)-(\\d+)/" +                                     // pid-tid
    "(\\S+)\\s+" +                                         // package
    "([A-Z])/" +                                           // log level
    "([^ ]+): " +                                          // tag (after any spaces have been stripped out)
    "(.*)$"                                                // message
  );

  /**
   * Convert data from logcat into a final message that can be displayed on a console.
   */
  public static String formatMessage(@NotNull LogMessageHeader header, @NotNull String message) {
    String ids = String.format(Locale.US, "%d-%s", header.myPid, header.myTid);
    return String.format(Locale.US, "%1$s %2$12s/%3$s %4$c/%5$s: %6$s",
                         header.myTime,
                         ids,
                         header.myAppPackage.isEmpty() ? "?" : header.myAppPackage,
                         header.myLogLevel.getPriorityLetter(),
                         header.myTag,
                         message);
  }

  /**
   * Parse a message that was encoded using {@link #formatMessage(LogMessageHeader, String)}
   */
  public static Message parseMessage(@NotNull String msg) {
    final Matcher matcher = LOGMESSAGE_PATTERN.matcher(msg);
    if (!matcher.matches()) {
      return new Message(null, msg);
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

    return new Message(header, message);
  }

  /**
   * Wrapped message data returned by {@link #parseMessage(String)}.
   */
  public static final class Message {
    @Nullable LogMessageHeader myHeader;
    @NotNull String myMessage;

    public Message(@Nullable LogMessageHeader logHeader, @NotNull String message) {
      myHeader = logHeader;
      myMessage = message;
    }

    @Nullable
    public LogMessageHeader getHeader() {
      return myHeader;
    }

    @NotNull
    public String getMessage() {
      return myMessage;
    }
  }
}

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
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatTimestamp;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LongMessageParser implements MessageParser {
  private static final Pattern DATE_TIME = Pattern.compile("\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d.\\d\\d\\d");

  private static final Pattern HEADER_MESSAGE = Pattern.compile("^("
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

  @Nullable
  @Override
  public LogCatMessage tryParse(@NotNull String message) {
    Matcher matcher = HEADER_MESSAGE.matcher(message);

    if (!matcher.matches()) {
      return null;
    }

    LogLevel priority = LogLevel.getByLetterString(matcher.group(5));
    assert priority != null;

    int processId = Integer.parseInt(matcher.group(2));
    int threadId = Integer.parseInt(matcher.group(3));
    String tag = matcher.group(6);
    Instant timestamp = LogCatTimestamp.parse(matcher.group(1));

    LogCatHeader header = new LogCatHeader(priority, processId, threadId, /* package= */ matcher.group(4), tag, timestamp);

    return new LogCatMessage(header, /* message= */ matcher.group(7));
  }
}

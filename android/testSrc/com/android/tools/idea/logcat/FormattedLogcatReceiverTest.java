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
import com.android.tools.idea.logcat.AndroidLogcatService.LogcatListener;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

public final class FormattedLogcatReceiverTest {
  @Test
  public void onLogLineReceived() {
    LogcatListener listener = new TestFormattedLogcatReceiver();

    Instant timestamp = Instant.parse("2018-04-17T20:33:25.907Z");
    LogCatHeader header1 = new LogCatHeader(LogLevel.INFO, 28740, 28740, "com.google.myapplication", "MainActivity", timestamp);
    listener.onLogLineReceived(new LogCatMessage(header1, "Log 1"));

    LogCatHeader header2 = new LogCatHeader(LogLevel.INFO, 28740, 28740, "com.google.myapplication", "MainActivity", timestamp);
    listener.onLogLineReceived(new LogCatMessage(header2, "Log 2"));

    Object expected = "2018-04-17 13:33:25.907 28740-28740/com.google.myapplication I/MainActivity: Log 1\n" +
                      "2018-04-17 13:33:25.907 28740-28740/com.google.myapplication I/MainActivity: Log 2\n";

    assertEquals(expected, listener.toString());
  }
}
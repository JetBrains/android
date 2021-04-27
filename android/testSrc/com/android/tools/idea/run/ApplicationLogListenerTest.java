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
package com.android.tools.idea.run;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.AndroidLogcatFormatter;
import com.android.tools.idea.logcat.AndroidLogcatPreferences;
import com.android.tools.idea.logcat.AndroidLogcatService.LogcatListener;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;

public final class ApplicationLogListenerTest {
  @Test
  public void onLogLineReceived() {
    LogcatListener listener = new TestApplicationLogListener("com.google.myapplication", 24314);

    Instant timestamp = Instant.parse("2018-04-03T20:27:49.161Z");
    LogCatHeader header = new LogCatHeader(LogLevel.INFO, 24314, 24314, "com.google.myapplication", "MainActivity", timestamp);

    listener.onLogLineReceived(new LogCatMessage(header, "Line 1\nLine 2\nLine 3"));

    Object expected = "I/MainActivity: Line 1\n" +
                      "    Line 2\n" +
                      "    Line 3\n";

    assertEquals(expected, listener.toString());
  }

  private static final class TestApplicationLogListener extends ApplicationLogListener {
    private static final String FORMAT = AndroidLogcatFormatter.createCustomFormat(false, false, false, true);

    private final AndroidLogcatFormatter myFormatter;
    private final StringBuilder myBuilder;

    private TestApplicationLogListener(@NotNull String p, int processId) {
      super(p, processId);

      myFormatter = new AndroidLogcatFormatter(ZoneId.of("America/Los_Angeles"), new AndroidLogcatPreferences());
      myBuilder = new StringBuilder();
    }

    @NotNull
    @Override
    protected String formatLogLine(@NotNull LogCatMessage line) {
      return myFormatter.formatMessage(FORMAT, line.getHeader(), line.getMessage());
    }

    @Override
    protected void notifyTextAvailable(@NotNull String message, @NotNull Key key) {
      myBuilder.append(message);
    }

    @NotNull
    @Override
    public String toString() {
      return myBuilder.toString();
    }
  }
}

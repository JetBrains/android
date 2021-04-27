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

import static com.android.ddmlib.Log.LogLevel.DEBUG;
import static com.intellij.testFramework.UsefulTestCase.assertThrows;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;

import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.intellij.diagnostic.logging.LogFormatter;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Before;
import org.junit.Test;

public class AndroidLogcatFormatterTest {
  private static final ZoneId TIME_ZONE = ZoneId.of("America/Los_Angeles");

  private AndroidLogcatPreferences myPreferences;
  private AndroidLogcatFormatter myFormatter;

  @Before
  public void setUp() {
    myPreferences = new AndroidLogcatPreferences();
    myFormatter = new AndroidLogcatFormatter(TIME_ZONE, myPreferences);
  }

  @Test
  public void formatMessage_invalidMessage_throws() {
    String message = "Messages must be Json encodings of a LogCatMessage";

    assertThrows(RuntimeException.class, () -> myFormatter.formatMessage(message));
  }

  @Test
  public void formatMessage_emptyFormat() {
    myPreferences.LOGCAT_FORMAT_STRING = "";
    LogCatMessage logCatMessage = new LogCatMessage(
      new LogCatHeader(DEBUG, 1234, 56, "com.app.test", "test", Instant.ofEpochSecond(1534635551, MILLISECONDS.toNanos(789))),
      "Test message");

    String formattedMessage = myFormatter.formatMessage(LogcatJson.toJson(logCatMessage));

    assertEquals("2018-08-18 16:39:11.789 1234-56/com.app.test D/test: Test message", formattedMessage);
  }

  @Test
  public void formatMessage_multilineIndent() {
    myPreferences.LOGCAT_FORMAT_STRING = "";
    LogCatMessage logCatMessage = new LogCatMessage(
      new LogCatHeader(DEBUG, 1234, 56, "com.app.test", "test", Instant.ofEpochSecond(1534635551, MILLISECONDS.toNanos(789))),
      "Line1\nLine2");

    String formattedMessage = myFormatter.formatMessage(LogcatJson.toJson(logCatMessage));

    assertEquals(
      "2018-08-18 16:39:11.789 1234-56/com.app.test D/test: Line1\n"
      + "    Line2",
      formattedMessage);
  }

  @Test
  public void variousFormatsWorkAsExpected() {
    LogCatMessage logCatMessage = new LogCatMessage(
      new LogCatHeader(DEBUG, 1234, 56, "com.app.test", "test", Instant.ofEpochSecond(1534635551, MILLISECONDS.toNanos(789))),
      "Test message");

    String message = LogcatJson.toJson(logCatMessage);

    assertExpected(true, true, false, false, message, "2018-08-18 16:39:11.789 1234-56 D: Test message");
    assertExpected(false, true, false, false, message, "1234-56 D: Test message");
    assertExpected(false, false, true, true, message, "com.app.test D/test: Test message");
    assertExpected(false, false, false, true, message, "D/test: Test message");
    assertExpected(false, false, false, false, message, "D: Test message");
  }

  private static void assertExpected(boolean showTime, boolean showPid, boolean showPackage, boolean showTag,
                                     String message, String expected) {
    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    LogFormatter formatter = new AndroidLogcatFormatter(TIME_ZONE, preferences);
    preferences.LOGCAT_FORMAT_STRING = AndroidLogcatFormatter.createCustomFormat(showTime, showPid, showPackage, showTag);
    String formattedMessage = formatter.formatMessage(message);
    assertEquals(expected, formattedMessage);
  }
}

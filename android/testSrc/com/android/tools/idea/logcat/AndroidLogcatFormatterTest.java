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

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatTimestamp;
import com.intellij.diagnostic.logging.LogFormatter;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class AndroidLogcatFormatterTest {

  @Test
  public void formatMessageToParseMessageKeepsAllInformation() {
    LogCatHeader header =
      new LogCatHeader(LogLevel.DEBUG, 13, 123, "system_process", "ConnectivityService", LogCatTimestamp.fromString("02-12 14:32:46.526"));
    String output = AndroidLogcatFormatter.formatMessageFull(header, "xyz");
    LogCatMessage message = AndroidLogcatFormatter.parseMessage(output);

    LogCatHeader header2 = message.getHeader();

    assertEquals(header.getTimestamp(), header2.getTimestamp());
    assertEquals(header.getLogLevel(), header2.getLogLevel());
    assertEquals(header.getPid(), header2.getPid());
    assertEquals(header.getTid(), header2.getTid());
    assertEquals(header.getAppName(), header2.getAppName());
    assertEquals(header.getTag(), header2.getTag());

    assertEquals("xyz", message.getMessage());
  }

  @Test
  public void formatMessageToParseMessageWorksInOtherLocales() {
    // make sure that encode and decode works together in other locales
    Locale defaultLocale = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
    try {
      formatMessageToParseMessageKeepsAllInformation();
    }
    finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  public void parseMessageForTagAndLogLevel() {
    String message = "02-12 17:04:44.005   1282-12/com.google.android.apps" +
                     ".maps:GoogleLocationService D/dalvikvm: Debugger has detached; object " +
                     "registry had 1 entries";

    LogCatHeader header = AndroidLogcatFormatter.parseMessage(message).getHeader();

    assertEquals(LogLevel.DEBUG, header.getLogLevel());
    assertEquals("dalvikvm", header.getTag());
  }

  @Test
  public void unknownFormatMessageRemainsSame() {
    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    AndroidLogcatFormatter formatter = new AndroidLogcatFormatter(preferences);

    String message = "some message that doesn't match any known AndroidLogcatFormatter format";
    String formattedMessage = formatter.formatMessage(message);

    assertEquals(message, formattedMessage);
  }

  @Test
  public void emptyFormatMessageLeavesTheSame() {
    String message = "01-23 12:34:56.789      1234-56/com.dummy.test D/test: Test message";

    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    AndroidLogcatFormatter formatter = new AndroidLogcatFormatter(preferences);
    assertEquals("", preferences.LOGCAT_FORMAT_STRING);
    String formattedMessage = formatter.formatMessage(message);
    assertEquals(message, formattedMessage);
  }

  @Test
  public void formatMessageIndentsContinuationIndentLengthSpaces() {
    LogFormatter formatter = new AndroidLogcatFormatter(new AndroidLogcatPreferences());

    formatter.formatMessage(
      "12-18 14:13:55.926 1620-1640/system_process V/StorageManagerService: Found primary storage at VolumeInfo{emulated}:");

    assertEquals(
      "        type=EMULATED diskId=null partGuid=null mountFlags=0 mountUserId=-1 ",
      formatter.formatMessage("+     type=EMULATED diskId=null partGuid=null mountFlags=0 mountUserId=-1 "));
  }

  @Test
  public void variousFormatsWorkAsExpected() {
    String message = "01-23 12:34:56.789      1234-56/com.dummy.test D/test: Test message";

    assertExpected(true, true, false, false, message, "01-23 12:34:56.789 1234-56 D: Test message");
    assertExpected(false, true, false, false, message, "1234-56 D: Test message");
    assertExpected(false, false, true, true, message, "com.dummy.test D/test: Test message");
    assertExpected(false, false, false, true, message, "D/test: Test message");
    assertExpected(false, false, false, false, message, "D: Test message");
  }

  private static void assertExpected(boolean showTime, boolean showPid, boolean showPackage, boolean showTag,
                                     String message, String expected) {
    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    AndroidLogcatFormatter formatter = new AndroidLogcatFormatter(preferences);
    preferences.LOGCAT_FORMAT_STRING = AndroidLogcatFormatter.createCustomFormat(showTime, showPid, showPackage, showTag);
    String formattedMessage = formatter.formatMessage(message);
    assertEquals(expected, formattedMessage);
  }
}

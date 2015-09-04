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
import com.android.tools.idea.logcat.AndroidLogcatReceiver.LogMessageHeader;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AndroidLogcatFormatterTest {

  @Test
  public void formatMessageToParseMessageKeepsAllInformation() {
    String message = "xyz";

    LogMessageHeader header = new LogMessageHeader();
    header.myTime = "02-12 14:32:46.526";
    header.myLogLevel = Log.LogLevel.DEBUG;
    header.myPid = 13;
    header.myTid = "123";
    header.myAppPackage = "system_process";
    header.myTag = "ConnectivityService";

    String output = AndroidLogcatFormatter.formatMessageFull(header, message);
    AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(output);

    assertNotNull(result.getHeader());

    LogMessageHeader header2 = result.getHeader();

    assertEquals(header.myTime, header2.myTime);
    assertEquals(header.myLogLevel, header2.myLogLevel);
    assertEquals(header.myPid, header2.myPid);
    assertEquals(header.myTid, header2.myTid);
    assertEquals(header.myAppPackage, header2.myAppPackage);
    assertEquals(header.myTag, header2.myTag);

    assertEquals(message, result.getMessage());
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

    LogMessageHeader header = AndroidLogcatFormatter.parseMessage(message).getHeader();
    assertNotNull(header);

    assertEquals(Log.LogLevel.DEBUG, header.myLogLevel);
    assertEquals("dalvikvm", header.myTag);
  }

  @Test
  public void unknownFormatMessageRemainsSame(){
    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    AndroidLogcatFormatter formatter = new AndroidLogcatFormatter(preferences);

    String message = "some message that doesn't match any known AndroidLogcatFormatter format";
    String formattedMessage = formatter.formatMessage(message);

    assertEquals(message, formattedMessage);
  }

  @Test
  public void emptyFormatMessageLeavesTheSame(){
    String message = "01-23 45:67:89.000      1234-56/com.dummy.test D/test: Test message";

    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    AndroidLogcatFormatter formatter = new AndroidLogcatFormatter(preferences);
    assertEquals(preferences.LOGCAT_FORMAT_STRING, "");
    String formattedMessage = formatter.formatMessage(message);
    assertEquals(message, formattedMessage);
  }

  @Test
  public void variousFormatsWorkAsExpected(){
    String message = "01-23 45:67:89.000      1234-56/com.dummy.test D/test: Test message";

    assertExpected(true, true, false, false, message, "01-23 45:67:89.000 1234-56 D: Test message");
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

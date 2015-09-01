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
import org.jetbrains.android.logcat.AndroidLogcatReceiver.LogMessageHeader;
import org.junit.Assert;
import org.junit.Test;

import java.util.Locale;

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

    String output = AndroidLogcatFormatter.formatMessage(header, message);
    AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(output);

    Assert.assertNotNull(result.getHeader());

    LogMessageHeader header2 = result.getHeader();

    Assert.assertEquals(header.myTime, header2.myTime);
    Assert.assertEquals(header.myLogLevel, header2.myLogLevel);
    Assert.assertEquals(header.myPid, header2.myPid);
    Assert.assertEquals(header.myTid, header2.myTid);
    Assert.assertEquals(header.myAppPackage, header2.myAppPackage);
    Assert.assertEquals(header.myTag, header2.myTag);

    Assert.assertEquals(message, result.getMessage());
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
    Assert.assertNotNull(header);

    Assert.assertEquals(Log.LogLevel.DEBUG, header.myLogLevel);
    Assert.assertEquals("dalvikvm", header.myTag);
  }
}

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
import com.intellij.openapi.util.Pair;
import junit.framework.TestCase;
import org.jetbrains.android.logcat.AndroidLogcatReceiver.LogMessageHeader;

import java.util.Locale;

public class AndroidLogcatFormatterTest extends TestCase {
  public void testDeserialize() {
    String message = "xyz";

    LogMessageHeader header = new LogMessageHeader();
    header.myTime = "02-12 14:32:46.526";
    header.myLogLevel = Log.LogLevel.DEBUG;
    header.myPid = 13;
    header.myTid = "123";
    header.myAppPackage = "system_process";
    header.myTag = "ConnectivityService";

    String output = AndroidLogcatFormatter.formatMessage(message, header);
    Pair<LogMessageHeader, String> result = AndroidLogcatFormatter.parseMessage(output);

    assertNotNull(result.getFirst());
    assertNotNull(result.getSecond());

    LogMessageHeader header2 = result.getFirst();

    assertEquals(header.myTime, header2.myTime);
    assertEquals(header.myLogLevel, header2.myLogLevel);
    assertEquals(header.myPid, header2.myPid);
    assertEquals(header.myAppPackage, header2.myAppPackage);
    assertEquals(header.myTag, header2.myTag);

    assertEquals(message, result.getSecond());
  }

  public void testLocale() {
    // make sure that encode and decode works together in other locales
    Locale defaultLocale = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
    try {
      testDeserialize();
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  public void test1() {
    String message = "02-12 17:04:44.005   1282-12/com.google.android.apps" +
                     ".maps:GoogleLocationService D/dalvikvm: Debugger has detached; object " +
                     "registry had 1 entries";

    LogMessageHeader header = AndroidLogcatFormatter.parseMessage(message).getFirst();
    assertNotNull(header);

    assertEquals(Log.LogLevel.DEBUG, header.myLogLevel);
    assertEquals("dalvikvm", header.myTag);
  }
}

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

import com.android.ddmlib.IDevice;
import com.intellij.openapi.util.text.StringUtil;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.StringWriter;

import static org.fest.assertions.Assertions.assertThat;

public class AndroidLogcatReceiverTest {
  private AndroidConsoleWriter myWriter;
  private AndroidLogcatReceiver myReceiver;

  /**
   * Helper method that creates a mock device.
   */
  static IDevice createMockDevice() {
    IDevice d = EasyMock.createMock(IDevice.class);
    EasyMock.expect(d.getClientName(1493)).andStubReturn("dummy.client.name");
    EasyMock.expect(d.getClientName(11698)).andStubReturn("com.android.chattylogger");
    EasyMock.expect(d.getClientName(EasyMock.anyInt())).andStubReturn("?");
    EasyMock.replay(d);
    return d;
  }

  @Before
  public void setUp() {
    myWriter = new AndroidConsoleWriter() {
      private final StringWriter myInnerWriter = new StringWriter();

      @Override
      public String toString() {
        return myInnerWriter.getBuffer().toString();
      }

      @Override
      public void clear() {
        myInnerWriter.flush();
        myInnerWriter.getBuffer().setLength(0);
      }

      @Override
      public void addMessage(@NotNull String text) {
        myInnerWriter.append(text).append('\n');
      }
    };
    myReceiver = new AndroidLogcatReceiver(createMockDevice(), myWriter);
  }

  @Test
  public void processNewLineWorksOnSimpleLogEntry() {
    // the following line is sample output from 'logcat -v long'
    myReceiver.processNewLine("[ 08-18 16:39:11.439 1493:1595 W/EDMNativeHelper     ]");
    assertThat("").isEqualTo(myWriter.toString()); // Nothing written until message is received

    myReceiver.processNewLine("EDMNativeHelperService is published");
    String expected = "08-18 16:39:11.439 1493-1595/dummy.client.name W/EDMNativeHelper: EDMNativeHelperService is published\n";
    assertThat(myWriter.toString()).isEqualTo(expected);
  }

  @Test
  public void processNewLineUsesQuestionMarkForUnknownClientIds() {
    myReceiver.processNewLine("[ 01-23 45:67:89.000 99:99 V/UnknownClient     ]");
    myReceiver.processNewLine("Dummy Message");

    String expected = "01-23 45:67:89.000 99-99/? V/UnknownClient: Dummy Message\n";
    assertThat(myWriter.toString()).isEqualTo(expected);
  }

  @Test
  public void processNewLineHandlesException() {
    myReceiver.processNewLine("[ 08-18 18:59:48.771 11698:11811 E/AndroidRuntime ]");

    myReceiver.processNewLine("FATAL EXCEPTION: Timer-0");
    myReceiver.processNewLine("Process: com.android.chattylogger, PID: 11698");
    myReceiver.processNewLine("java.lang.RuntimeException: Bad response");
    myReceiver.processNewLine("       at com.android.chattylogger.MainActivity$1.run(MainActivity.java:64)");
    myReceiver.processNewLine("       at java.util.Timer$TimerImpl.run(Timer.java:284)");


    String expected = "08-18 18:59:48.771 11698-11811/com.android.chattylogger E/AndroidRuntime: FATAL EXCEPTION: Timer-0\n" +
                      "08-18 18:59:48.771 11698-11811/com.android.chattylogger E/AndroidRuntime: Process: com.android.chattylogger, PID: 11698\n" +
                      "08-18 18:59:48.771 11698-11811/com.android.chattylogger E/AndroidRuntime: java.lang.RuntimeException: Bad response\n" +
                      "08-18 18:59:48.771 11698-11811/com.android.chattylogger E/AndroidRuntime:     at com.android.chattylogger.MainActivity$1.run(MainActivity.java:64)\n" +
                      "08-18 18:59:48.771 11698-11811/com.android.chattylogger E/AndroidRuntime:     at java.util.Timer$TimerImpl.run(Timer.java:284)\n";

    assertThat(myWriter.toString()).isEqualTo(expected);
  }

  @Test
  public void testParseAllLogLevelsAndHexThreadIds() {
    String[] messages = new String[] {
      "[ 08-11 19:11:07.132   495:0x1ef D/dtag     ]",
      "debug message",
      "[ 08-11 19:11:07.132   495:  234 E/etag     ]",
      "error message",
      "[ 08-11 19:11:07.132   495:0x1ef I/itag     ]",
      "info message",
      "[ 08-11 19:11:07.132   495:0x1ef V/vtag     ]",
      "verbose message",
      "[ 08-11 19:11:07.132   495:0x1ef W/wtag     ]",
      "warning message",
      "[ 08-11 19:11:07.132   495:0x1ef F/wtftag   ]",
      "wtf message",
      "[ 08-11 21:15:35.7524  540:0x21c D/debug tag    ]",
      "debug message",
      "[ 08-11 21:15:35.7524  540:0x21c I/tag:with:colons ]",
      "message:with:colons",
    };

    for (String message : messages) {
      myReceiver.processNewLine(message);
    }

    String expected = "08-11 19:11:07.132 495-495/? D/dtag: debug message\n" +
                      "08-11 19:11:07.132 495-234/? E/etag: error message\n" +
                      "08-11 19:11:07.132 495-495/? I/itag: info message\n" +
                      "08-11 19:11:07.132 495-495/? V/vtag: verbose message\n" +
                      "08-11 19:11:07.132 495-495/? W/wtag: warning message\n" +
                      "08-11 19:11:07.132 495-495/? A/wtftag: wtf message\n" +
                      // NOTE: "debug tag" uses a special-case "no break" space character
                      "08-11 21:15:35.7524 540-540/? D/debug tag: debug message\n" +
                      "08-11 21:15:35.7524 540-540/? I/tag:with:colons: message:with:colons\n";

    assertThat(myWriter.toString()).isEqualTo(expected);
  }
}

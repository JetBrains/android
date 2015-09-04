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

import com.android.ddmlib.IDevice;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;

import java.io.StringWriter;

public class AndroidLogcatReceiverTest extends TestCase {
  private AndroidConsoleWriter mySink;
  private AndroidLogcatReceiver myReceiver;

  /** Helper method that creates a mock device. */
  static IDevice createMockDevice() {
    IDevice mockDevice = EasyMock.createMock(IDevice.class);
    EasyMock.expect(mockDevice.getClientName(EasyMock.anyInt())).andStubReturn("?");
    EasyMock.replay(mockDevice);
    return mockDevice;
  }

  @Override
  public void setUp() {
    mySink = new AndroidConsoleWriter() {
      private StringWriter myWriter = new StringWriter();

      @Override
      public String toString() {
        return myWriter.getBuffer().toString();
      }

      @Override
      public void clear() {
        myWriter.flush();
        myWriter.getBuffer().setLength(0);
      }

      @Override
      public void addMessage(@NotNull String text) {
        myWriter.append(text).append('\n');
      }
    };
    myReceiver = new AndroidLogcatReceiver(createMockDevice(), mySink);
  }

  public void testParser() {
    // the following two lines are a sample output from 'logcat -v long'
    myReceiver.processNewLine("[ 02-11 16:41:10.621 17945:17995 W/GAV2     ]");
    myReceiver.processNewLine("Thread[Service Reconnect,5,main]: Connection to service failed 1");

    assertEquals(
      insertTagSeparator("02-11 16:41:10.621  17945-17995/? W/GAV2", "Thread[Service Reconnect,5,main]: Connection to service failed 1\n"),
      mySink.toString());
  }

  public void testParseException() {
    String line1 = "FATAL EXCEPTION: main";
    String line2 = "java.lang.RuntimeException: Unable to <snip>: j.l.Exception";
    String line3 = "at android..performLaunchActivity(ActivityThread.java:2180)";

    myReceiver.processNewLine("[ 02-11 18:03:35.037 19796:19796 E/AndroidRuntime ]");
    myReceiver.processNewLine(line1);
    myReceiver.processNewLine(line2);
    myReceiver.processNewLine(line3);

    assertEquals("02-11 18:03:35.037  19796-19796/? E/AndroidRuntime: " +
                 line1 + "\n" +
                 AndroidLogcatReceiver.CONTINUATION_LINE_PREFIX + line2 + "\n" +
                 AndroidLogcatReceiver.STACK_TRACE_LINE_PREFIX + line3 + "\n",
                 mySink.toString());
  }

  public void testParser2() {
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

    for (int i = 0; i < messages.length; i++) {
      myReceiver.processNewLine(messages[i]);
    }

    assertEquals(
      insertTagSeparator("08-11 19:11:07.132      495-495/? D/dtag", "debug message\n") +
      insertTagSeparator("08-11 19:11:07.132      495-234/? E/etag", "error message\n") +
      insertTagSeparator("08-11 19:11:07.132      495-495/? I/itag", "info message\n") +
      insertTagSeparator("08-11 19:11:07.132      495-495/? V/vtag", "verbose message\n") +
      insertTagSeparator("08-11 19:11:07.132      495-495/? W/wtag", "warning message\n") +
      insertTagSeparator("08-11 19:11:07.132      495-495/? A/wtftag", "wtf message\n") +
      insertTagSeparator("08-11 21:15:35.7524      540-540/? D/debug tag", "debug message\n") +
      insertTagSeparator("08-11 21:15:35.7524      540-540/? I/tag:with:colons", "message:with:colons\n"),
                       mySink.toString());
  }

  private String insertTagSeparator(String header, String msg) {
    return String.format("%1$s: %2$s", header, msg);
  }
}

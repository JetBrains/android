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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class StackTraceExpanderTest extends TestCase {
  public void testExpander() {
    String input = "HighLevelException: MidLevelException: LowLevelException\n" +
                   "         at Junk.a(Junk.java:13)\n" +
                   "         at Junk.main(Junk.java:4)\n" +
                   " Caused by: MidLevelException: LowLevelException\n" +
                   "         at Junk.c(Junk.java:23)\n" +
                   "         at Junk.b(Junk.java:17)\n" +
                   "         at Junk.a(Junk.java:11)\n" +
                   "         ... 1 more\n" +
                   " Caused by: LowLevelException\n" +
                   "         at Junk.e(Junk.java:30)\n" +
                   "         at Junk.d(Junk.java:27)\n" +
                   "         at Junk.c(Junk.java:21)\n" +
                   "         ... 3 more\n";

    String expectedOutput = "HighLevelException: MidLevelException: LowLevelException\n" +
                            "         at Junk.a(Junk.java:13)\n" +
                            "         at Junk.main(Junk.java:4)\n" +
                            " Caused by: MidLevelException: LowLevelException\n" +
                            "         at Junk.c(Junk.java:23)\n" +
                            "         at Junk.b(Junk.java:17)\n" +
                            "         at Junk.a(Junk.java:11)\n" +
                            "           at Junk.main(Junk.java:4)\n" +
                            " Caused by: LowLevelException\n" +
                            "         at Junk.e(Junk.java:30)\n" +
                            "         at Junk.d(Junk.java:27)\n" +
                            "         at Junk.c(Junk.java:21)\n" +
                            "           at Junk.b(Junk.java:17)\n" +
                            "           at Junk.a(Junk.java:11)\n" +
                            "           at Junk.main(Junk.java:4)\n";

    test(input, expectedOutput);
  }

  public void testExpander2() {
    String input = "10-02 15:22:48.811  24985-24998/com.example.t1 E/e1﹕ error\n" +
                   "java.lang.SecurityException: Permission denied (missing INTERNET permission?)\n" +
                   "         at java.net.InetAddress.lookupHostByName(InetAddress.java:418)\n" +
                   "         at java.net.InetAddress.getAllByNameImpl(InetAddress.java:236)\n" +
                   "         at java.net.InetAddress.getAllByName(InetAddress.java:214)\n" +
                   "         at java.lang.Thread.run(Thread.java:841)\n" +
                   " Caused by: libcore.io.GaiException: getaddrinfo failed: EAI_NODATA (No address associated with hostname)\n" +
                   "         at java.net.InetAddress.lookupHostByName(InetAddress.java:405)\n" +
                   "         ... 2 more\n" +
                   " Caused by: libcore.io.ErrnoException: getaddrinfo failed: EACCES (Permission denied)\n" +
                   "         ... 3 more\n";
    String expected = "10-02 15:22:48.811  24985-24998/com.example.t1 E/e1﹕ error\n" +
                   "java.lang.SecurityException: Permission denied (missing INTERNET permission?)\n" +
                   "         at java.net.InetAddress.lookupHostByName(InetAddress.java:418)\n" +
                   "         at java.net.InetAddress.getAllByNameImpl(InetAddress.java:236)\n" +
                   "         at java.net.InetAddress.getAllByName(InetAddress.java:214)\n" +
                   "         at java.lang.Thread.run(Thread.java:841)\n" +
                   " Caused by: libcore.io.GaiException: getaddrinfo failed: EAI_NODATA (No address associated with hostname)\n" +
                   "         at java.net.InetAddress.lookupHostByName(InetAddress.java:405)\n" +
                   "           at java.net.InetAddress.getAllByName(InetAddress.java:214)\n" +
                   "           at java.lang.Thread.run(Thread.java:841)\n" +
                   " Caused by: libcore.io.ErrnoException: getaddrinfo failed: EACCES (Permission denied)\n" +
                   "           at java.net.InetAddress.lookupHostByName(InetAddress.java:405)\n" +
                   "           at java.net.InetAddress.getAllByName(InetAddress.java:214)\n" +
                   "           at java.lang.Thread.run(Thread.java:841)\n";
    test(input, expected);
  }

  private void test(String input, String expectedOutput) {
    StackTraceExpander expander = new StackTraceExpander(
      "", // don't add any extra prefixes
      StringUtil.repeatSymbol(' ', 9), // 9 spaces before at symbol in standard Java traces, used above
      StringUtil.repeatSymbol(' ', 11), // indent the expanded traces by 2 additional characters
      " "); // 1 space before "Caused by:" in the stack traces used above
    List<String> output = new ArrayList<String>();

    for (String line: Splitter.on('\n').split(input)) {
      line = line.trim();

      output.add(expander.expand(line));
    }

    assertEquals(expectedOutput, Joiner.on('\n').join(output));
  }

  public void testStackFrameMatcher() {
    String[] validFrames = new String[] {
      "at com.example.t1.MainActivity.one(MainActivity.java:31)",
      "at android.app.ActivityThread.access$700(ActivityThread.java:134)",
      "at java.lang.reflect.Method.invokeNative(Native Method)",
    };

    for (String frame: validFrames) {
      assertTrue(StackTraceExpander.isStackFrame(frame));
    }

    String[] invalidFrames = new String[] {
      "Caused by: java.lang.RuntimeException",
      "... 15 more"
    };

    for (String frame: invalidFrames) {
      assertFalse(StackTraceExpander.isStackFrame(frame));
    }
  }

  public void testElidedCount() {
    assertEquals(3, StackTraceExpander.getElidedFrameCount("... 3 more"));
    assertEquals(-1, StackTraceExpander.getElidedFrameCount("... more follow"));
  }
}

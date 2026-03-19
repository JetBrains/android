/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.google.common.base.Charsets;
import java.io.IOException;
import java.nio.ByteBuffer;
import junit.framework.TestCase;

public class AllocationsParserTest extends TestCase {

  public void testParsingOnNoAllocations() throws IOException {
    ByteBuffer data = putAllocationInfo(new String[0], new String[0], new String[0], new int[0][], new short[0][][]);
    assertEquals(0, AllocationsParser.parse(data).length);
  }

  public void testParsingOnOneAllocationWithoutStackTrace() throws IOException {
    ByteBuffer data =
            putAllocationInfo(new String[]{"path.Foo"}, new String[0], new String[0], new int[][]{{32, 4, 0, 0}}, new short[][][]{{}});
    AllocationInfo[] info = AllocationsParser.parse(data);
    assertEquals(1, info.length);

    AllocationInfo alloc = info[0];
    checkEntry(1, "path.Foo", 32, 4, alloc);
    checkFirstTrace(null, null, alloc);
    assertEquals(0, alloc.getStackTrace().length);
  }

  public void testParsingOnOneAllocationWithStackTrace() throws IOException {
    ByteBuffer data = putAllocationInfo(new String[]{"path.Foo", "path.Bar", "path.Baz"}, new String[]{"foo", "bar", "baz"},
            new String[]{"Foo.java", "Bar.java"}, new int[][]{{64, 0, 1, 3}},
            new short[][][]{{{1, 1, 1, -1}, {2, 0, 1, 2000}, {0, 2, 0, 10}}});
    AllocationInfo[] info = AllocationsParser.parse(data);
    assertEquals(1, info.length);

    AllocationInfo alloc = info[0];
    checkEntry(1, "path.Bar", 64, 0, alloc);
    checkFirstTrace("path.Bar", "bar", alloc);

    StackTraceElement[] elems = alloc.getStackTrace();
    assertEquals(3, elems.length);

    checkStackFrame("path.Bar", "bar", "Bar.java", -1, elems[0]);
    checkStackFrame("path.Baz", "foo", "Bar.java", 2000, elems[1]);
    checkStackFrame("path.Foo", "baz", "Foo.java", 10, elems[2]);
  }

  public void testParsing() throws IOException {
    ByteBuffer data = putAllocationInfo(new String[]{"path.Red", "path.Green", "path.Blue", "path.LightCanaryishGrey"},
            new String[]{"eatTiramisu", "failUnitTest", "watchCatVideos", "passGo", "collectFakeMoney", "findWaldo"},
            new String[]{"Red.java", "SomewhatBlue.java", "LightCanaryishGrey.java"},
            new int[][]{{128, 8, 0, 2}, {16, 8, 2, 1}, {42, 2, 1, 3}},
            new short[][][]{{{1, 0, 1, 100}, {2, 5, 1, -2}}, {{0, 1, 0, -1}}, {{3, 4, 2, 10001}, {0, 3, 0, 0}, {2, 2, 1, 16}}});
    AllocationInfo[] info = AllocationsParser.parse(data);
    assertEquals(3, info.length);

    AllocationInfo alloc1 = info[0];
    checkEntry(3, "path.Red", 128, 8, alloc1);
    checkFirstTrace("path.Green", "eatTiramisu", alloc1);

    StackTraceElement[] elems1 = alloc1.getStackTrace();
    assertEquals(2, elems1.length);

    checkStackFrame("path.Green", "eatTiramisu", "SomewhatBlue.java", 100, elems1[0]);
    checkStackFrame("path.Blue", "findWaldo", "SomewhatBlue.java", -2, elems1[1]);

    AllocationInfo alloc2 = info[1];
    checkEntry(2, "path.Blue", 16, 8, alloc2);
    checkFirstTrace("path.Red", "failUnitTest", alloc2);

    StackTraceElement[] elems2 = alloc2.getStackTrace();
    assertEquals(1, elems2.length);

    checkStackFrame("path.Red", "failUnitTest", "Red.java", -1, elems2[0]);

    AllocationInfo alloc3 = info[2];
    checkEntry(1, "path.Green", 42, 2, alloc3);
    checkFirstTrace("path.LightCanaryishGrey", "collectFakeMoney", alloc3);

    StackTraceElement[] elems3 = alloc3.getStackTrace();
    assertEquals(3, elems3.length);

    checkStackFrame("path.LightCanaryishGrey", "collectFakeMoney", "LightCanaryishGrey.java", 10001, elems3[0]);
    checkStackFrame("path.Red", "passGo", "Red.java", 0, elems3[1]);
    checkStackFrame("path.Blue", "watchCatVideos", "SomewhatBlue.java", 16, elems3[2]);
  }

  private static void checkEntry(int order, String className, int size, int thread, AllocationInfo alloc) {
    assertEquals(order, alloc.getAllocNumber());
    assertEquals(className, alloc.getAllocatedClass());
    assertEquals(size, alloc.getSize());
    assertEquals(thread, alloc.getThreadId());
  }

  private static void checkFirstTrace(String className, String methodName, AllocationInfo alloc) {
    assertEquals(className, alloc.getFirstTraceClassName());
    assertEquals(methodName, alloc.getFirstTraceMethodName());
  }

  private static void checkStackFrame(String className, String methodName, String fileName, int lineNumber, StackTraceElement elem) {
    assertEquals(className, elem.getClassName());
    assertEquals(methodName, elem.getMethodName());
    assertEquals(fileName, elem.getFileName());
    assertEquals(lineNumber, elem.getLineNumber());
  }

    public static ByteBuffer putAllocationInfo(
            String[] classNames,
            String[] methodNames,
            String[] fileNames,
            int[][] entries,
            short[][][] stackFrames) {
    byte msgHdrLen = 15, entryHdrLen = 9, stackFrameLen = 8;

    // Number of bytes from start of message to string tables
    int offset = msgHdrLen;
    for (int[] entry : entries) {
      offset += entryHdrLen + (stackFrameLen * entry[3]);
    }

    // Number of bytes in string tables
    int strNamesLen = 0;
    for (String name : classNames) { strNamesLen += 4 + (2 * name.length()); }
    for (String name : methodNames) { strNamesLen += 4 + (2 * name.length()); }
    for (String name : fileNames) { strNamesLen += 4 + (2 * name.length()); }

    ByteBuffer data = ByteBuffer.allocate(offset + strNamesLen);

    data.put(new byte[]{msgHdrLen, entryHdrLen, stackFrameLen});
    data.putShort((short) entries.length);
    data.putInt(offset);
    data.putShort((short) classNames.length);
    data.putShort((short) methodNames.length);
    data.putShort((short) fileNames.length);

    for (short i = 0; i < entries.length; ++i) {
      data.putInt(entries[i][0]); // total alloc size
      data.putShort((short) entries[i][1]); // thread id
      data.putShort((short) entries[i][2]); // allocated class index
      data.put((byte) entries[i][3]); // stack depth

      short[][] frames = stackFrames[i];
      for (short[] frame : frames) {
        data.putShort(frame[0]); // class name
        data.putShort(frame[1]); // method name
        data.putShort(frame[2]); // source file
        data.putShort(frame[3]); // line number
      }
    }

    for (String name : classNames) {
      data.putInt(name.length());
      data.put(strToBytes(name));
    }
    for (String name : methodNames) {
      data.putInt(name.length());
      data.put(strToBytes(name));
    }
    for (String name : fileNames) {
      data.putInt(name.length());
      data.put(strToBytes(name));
    }
    data.rewind();
    return data;
  }

  private static byte[] strToBytes(String str) {
    return str.getBytes(Charsets.UTF_16BE);
  }
}
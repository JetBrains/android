/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.tools.profiler.proto.MemoryProfiler;
import com.google.protobuf3jarjar.ByteString;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LegacyAllocationConverterTest {

  private static final String CLASS_NAME = LegacyAllocationConverterTest.class.getName();
  private static final String METHOD_NAME = "TestMethod";
  private static final String FILE_NAME = "LegacyAllocationConverterTest.java";
  private static final int LINE_NUMBER = 100;
  private static final int THREAD_ID = 10;
  private static final int SIZE = 101;
  private static final byte[] STACK_ID = ByteString.copyFrom("STACK_ID", Charset.defaultCharset()).toByteArray();

  @Test
  public void testCallStackFramesDuplicatedInsert() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    List<StackTraceElement> stackTraceElementList = new ArrayList<>();
    stackTraceElementList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
    LegacyAllocationConverter.CallStack callStack = converter.addCallStack(stackTraceElementList);
    assertEquals(callStack, converter.addCallStack(stackTraceElementList));
  }

  @Test
  public void testCallStackFrames() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    List<StackTraceElement> stackTraceElementList = new ArrayList<>();
    stackTraceElementList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
    stackTraceElementList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER + 1));
    stackTraceElementList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER + 2));
    LegacyAllocationConverter.CallStack callStack = converter.addCallStack(stackTraceElementList);
    List<MemoryProfiler.AllocationStack> allocated = converter.getAllocationStacks();
    assertEquals(allocated.size(), 1);
    for (MemoryProfiler.AllocationStack allocation : allocated) {
      assertEquals(callStack.getAllocationStack(), allocation);
      assertEquals(stackTraceElementList.size(), allocation.getStackFramesCount());
      for (int i = 0; i < allocation.getStackFramesCount(); i++) {
        MemoryProfiler.AllocationStack.StackFrame frame = allocation.getStackFrames(i);
        assertEquals(stackTraceElementList.get(i).getClassName(), frame.getClassName());
        assertEquals(stackTraceElementList.get(i).getMethodName(), frame.getMethodName());
        assertEquals(stackTraceElementList.get(i).getFileName(), frame.getFileName());
        assertEquals(stackTraceElementList.get(i).getLineNumber(), frame.getLineNumber());
      }
    }
  }

  @Test
  public void testCallstackHashcodeId() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    List<StackTraceElement> stackTraceElementList = new ArrayList<>();
    stackTraceElementList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
    LegacyAllocationConverter.CallStack callStack = converter.addCallStack(stackTraceElementList);
    assertEquals(ByteBuffer.wrap(callStack.getId()).getInt(), callStack.hashCode());
  }

  @Test
  public void testAddClassName() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    int id = converter.addClassName(CLASS_NAME);
    assertEquals(id, converter.addClassName(CLASS_NAME));

    List<MemoryProfiler.AllocatedClass> classes = converter.getClassNames();
    assertEquals(1, classes.size());
    assertEquals(CLASS_NAME, classes.get(0).getClassName());
    assertEquals(id, classes.get(0).getClassId());
  }

  @Test
  public void testAddAllocation() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    int id = converter.addClassName(CLASS_NAME);
    converter.addAllocation(new LegacyAllocationConverter.Allocation(id, SIZE, THREAD_ID, STACK_ID));
    List<MemoryProfiler.AllocationEvent> allocations = converter.getAllocationEvents(System.nanoTime(), System.nanoTime());
    assertEquals(1, allocations.size());
    assertEquals(id, allocations.get(0).getAllocatedClassId());
    assertEquals(SIZE, allocations.get(0).getSize());
    assertEquals(THREAD_ID, allocations.get(0).getThreadId());
    assertEquals(ByteString.copyFrom(STACK_ID), allocations.get(0).getAllocationStackId());
    converter.prepare();
    assertEquals(0, converter.getAllocationEvents(System.nanoTime(), System.nanoTime()).size());
  }
}

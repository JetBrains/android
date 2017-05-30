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
import org.junit.Test;

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
  private static final int STACK_ID = 1001;

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
  public void testCallstackHashcode() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    List<StackTraceElement> stackTraceElementList = new ArrayList<>();
    stackTraceElementList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
    LegacyAllocationConverter.CallStack callStack = converter.addCallStack(stackTraceElementList);
    assertEquals(stackTraceElementList.hashCode(), callStack.hashCode());
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
    List<MemoryProfiler.LegacyAllocationEvent> allocations = converter.getAllocationEvents(System.nanoTime(), System.nanoTime());
    assertEquals(1, allocations.size());
    assertEquals(id, allocations.get(0).getClassId());
    assertEquals(SIZE, allocations.get(0).getSize());
    assertEquals(THREAD_ID, allocations.get(0).getThreadId());
    assertEquals(STACK_ID, allocations.get(0).getStackId());
    converter.prepare();
    assertEquals(0, converter.getAllocationEvents(System.nanoTime(), System.nanoTime()).size());
  }
}

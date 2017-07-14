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

import static com.google.common.truth.Truth.assertThat;

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
    assertThat(converter.addCallStack(stackTraceElementList)).isEqualTo(callStack);
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
    assertThat(allocated.size()).isEqualTo(1);
    for (MemoryProfiler.AllocationStack allocation : allocated) {
      assertThat(allocation).isEqualTo(callStack.getAllocationStack());
      assertThat(allocation.getFrameCase()).isEqualTo(MemoryProfiler.AllocationStack.FrameCase.FULL_STACK);
      MemoryProfiler.AllocationStack.StackFrameWrapper fullStack = allocation.getFullStack();
      assertThat(fullStack.getFramesCount()).isEqualTo(stackTraceElementList.size());
      for (int i = 0; i < fullStack.getFramesCount(); i++) {
        MemoryProfiler.AllocationStack.StackFrame frame = fullStack.getFrames(i);
        assertThat(frame.getClassName()).isEqualTo(stackTraceElementList.get(i).getClassName());
        assertThat(frame.getMethodName()).isEqualTo(stackTraceElementList.get(i).getMethodName());
        assertThat(frame.getFileName()).isEqualTo(stackTraceElementList.get(i).getFileName());
        assertThat(frame.getLineNumber()).isEqualTo(stackTraceElementList.get(i).getLineNumber());
      }
    }
  }

  @Test
  public void testCallstackHashcode() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    List<StackTraceElement> stackTraceElementList = new ArrayList<>();
    stackTraceElementList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
    LegacyAllocationConverter.CallStack callStack = converter.addCallStack(stackTraceElementList);
    assertThat(callStack.hashCode()).isEqualTo(stackTraceElementList.hashCode());
  }

  @Test
  public void testAddClassName() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    int id = converter.addClassName(CLASS_NAME);
    assertThat(converter.addClassName(CLASS_NAME)).isEqualTo(id);

    List<MemoryProfiler.AllocatedClass> classes = converter.getClassNames();
    assertThat(classes.size()).isEqualTo(1);
    assertThat(classes.get(0).getClassName()).isEqualTo(CLASS_NAME);
    assertThat(classes.get(0).getClassId()).isEqualTo(id);
  }

  @Test
  public void testAddAllocation() {
    LegacyAllocationConverter converter = new LegacyAllocationConverter();
    int id = converter.addClassName(CLASS_NAME);
    converter.addAllocation(new LegacyAllocationConverter.Allocation(id, SIZE, THREAD_ID, STACK_ID));
    List<MemoryProfiler.LegacyAllocationEvent> allocations = converter.getAllocationEvents(System.nanoTime(), System.nanoTime());
    assertThat(allocations.size()).isEqualTo(1);
    assertThat(allocations.get(0).getClassId()).isEqualTo(id);
    assertThat(allocations.get(0).getSize()).isEqualTo(SIZE);
    assertThat(allocations.get(0).getThreadId()).isEqualTo(THREAD_ID);
    assertThat(allocations.get(0).getStackId()).isEqualTo(STACK_ID);
    converter.prepare();
    assertThat(converter.getAllocationEvents(System.nanoTime(), System.nanoTime()).size()).isEqualTo(0);
  }
}

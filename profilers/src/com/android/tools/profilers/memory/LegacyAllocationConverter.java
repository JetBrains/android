/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.AllocationInfo;
import com.android.ddmlib.AllocationsParser;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Memory.AllocatedClass;
import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * A class to convert JDWP-based legacy allocation into the gRPC-based profiler allocation tracking format.
 */
public class LegacyAllocationConverter {
  public static class CallStack {
    @NotNull
    private final List<StackTraceElement> myCallStackFrames;

    public CallStack(@NotNull List<StackTraceElement> frames) {
      myCallStackFrames = frames;
    }

    @Override
    public int hashCode() {
      return myCallStackFrames.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof CallStack)) {
        return false;
      }

      return myCallStackFrames.equals(((CallStack)obj).myCallStackFrames);
    }

    @NotNull
    public AllocationStack getAllocationStack() {
      AllocationStack.Builder builder = AllocationStack.newBuilder().setStackId(hashCode());
      AllocationStack.StackFrameWrapper.Builder frameBuilder = AllocationStack.StackFrameWrapper.newBuilder();
      for (StackTraceElement frame : myCallStackFrames) {
        frameBuilder.addFrames(
          AllocationStack.StackFrame.newBuilder().setClassName(frame.getClassName()).setMethodName(frame.getMethodName())
            .setFileName(frame.getFileName()).setLineNumber(frame.getLineNumber()).build());
      }
      builder.setFullStack(frameBuilder);
      return builder.build();
    }
  }

  public static class ClassName {
    private final String myClassName;

    private final int myClassId;

    public ClassName(@NotNull String className, int classId) {
      myClassName = className;
      myClassId = classId;
    }

    @NotNull
    public AllocatedClass getAllocatedClass() {
      return AllocatedClass.newBuilder().setClassName(myClassName).setClassId(myClassId).build();
    }
  }

  public static class Allocation {
    private final int myClassId;

    private final int mySize;

    private final int myThreadId;

    private final int myCallStackId;

    public Allocation(int classId, int size, int threadId, int callStackId) {
      myClassId = classId;
      mySize = size;
      myThreadId = threadId;
      myCallStackId = callStackId;
    }

    @NotNull
    public AllocationEvent.Allocation bindAllocationEventInfos() {
      return AllocationEvent.Allocation.newBuilder()
        .setClassTag(myClassId).setSize(mySize).setThreadId(myThreadId).setStackId(myCallStackId).build();
    }
  }

  @NotNull
  private List<Allocation> myAllocations = new ArrayList<>();

  @NotNull
  private Map<String, ClassName> myAllocatedClasses = new HashMap<>();

  @NotNull
  private Map<List<StackTraceElement>, CallStack> myAllocationStacks = new HashMap<>();

  public int addClassName(@NotNull String className) {
    int id;
    if (!myAllocatedClasses.containsKey(className)) {
      id = myAllocatedClasses.size();
      myAllocatedClasses.put(className, new ClassName(className, id));
    }
    else {
      id = myAllocatedClasses.get(className).myClassId;
    }
    return id;
  }

  @NotNull
  public CallStack addCallStack(@NotNull List<StackTraceElement> stackTraceElements) {
    CallStack result;
    if (!myAllocationStacks.containsKey(stackTraceElements)) {
      result = new CallStack(stackTraceElements);
      myAllocationStacks.put(stackTraceElements, result);
    }
    else {
      result = myAllocationStacks.get(stackTraceElements);
    }
    return result;
  }

  /**
   * Clears the converter of any existing allocation events. This allows the converter to parse a new .alloc file that point to the
   * same class and callstack caches.
   */
  public void resetAllocations() {
    myAllocations.clear();
  }

  public void addAllocation(@NotNull Allocation allocationInfo) {
    myAllocations.add(allocationInfo);
  }

  public List<AllocationEvent.Allocation> getAllocationEvents() {
    return ContainerUtil.map(myAllocations, allocation -> allocation.bindAllocationEventInfos());
  }

  /**
   * Note that this returns all stacks gathered from all seen allocation tracking sessions. We can refactor this to not share the data
   * across sessions, but it's probably not worth it given it is legacy.
   */
  public List<AllocationStack> getAllocationStacks() {
    return ContainerUtil.map(myAllocationStacks.values(), CallStack::getAllocationStack);
  }

  /**
   * Note that this returns all classes gathered from all seen allocation tracking sessions. We can refactor this to not share the data
   * across sessions, but it's probably not worth it given it is legacy.
   */
  public List<AllocatedClass> getClassNames() {
    return ContainerUtil.map(myAllocatedClasses.values(), ClassName::getAllocatedClass);
  }

  public void parseDump(@NotNull byte[] dumpData) {
    resetAllocations();
    AllocationInfo[] rawInfos = AllocationsParser.parse(ByteBuffer.wrap(dumpData));
    for (AllocationInfo info : rawInfos) {
      List<StackTraceElement> stackTraceElements = Arrays.asList(info.getStackTrace());
      LegacyAllocationConverter.CallStack callStack = addCallStack(stackTraceElements);
      int classId = addClassName(info.getAllocatedClass());
      addAllocation(new LegacyAllocationConverter.Allocation(classId, info.getSize(), info.getThreadId(), callStack.hashCode()));
    }
  }
}
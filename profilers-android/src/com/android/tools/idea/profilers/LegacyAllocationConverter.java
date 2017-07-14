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

import com.android.tools.profiler.proto.MemoryProfiler.AllocatedClass;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public Allocation(int classId, int size, int threadId, @NotNull int callStackId) {
      myClassId = classId;
      mySize = size;
      myThreadId = threadId;
      myCallStackId = callStackId;
    }

    @NotNull
    public LegacyAllocationEvent bindAllocationEventInfos(long startTime, long endTime) {
      return LegacyAllocationEvent.newBuilder().setClassId(myClassId).setSize(mySize).setThreadId(myThreadId)
        .setCaptureTime(startTime).setTimestamp(endTime).setStackId(myCallStackId).build();
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
   * Prepares the converter to convert a new .alloc file.
   */
  public void prepare() {
    myAllocations.clear();
  }

  public void addAllocation(@NotNull Allocation allocationInfo) {
    myAllocations.add(allocationInfo);
  }

  public List<LegacyAllocationEvent> getAllocationEvents(long startTime, long endTime) {
    return myAllocations.stream().map(allocation -> allocation.bindAllocationEventInfos(startTime, endTime)).collect(Collectors.toList());
  }

  /**
   * Note that this returns all stacks gathered from all seen allocation tracking sessions. We can refactor this to not share the data
   * across sessions, but it's probably not worth it given it is legacy.
   */
  public List<AllocationStack> getAllocationStacks() {
    return myAllocationStacks.values().stream().map(CallStack::getAllocationStack).collect(Collectors.toList());
  }

  /**
   * Note that this returns all classes gathered from all seen allocation tracking sessions. We can refactor this to not share the data
   * across sessions, but it's probably not worth it given it is legacy.
   */
  public List<AllocatedClass> getClassNames() {
    return myAllocatedClasses.values().stream().map(ClassName::getAllocatedClass).collect(Collectors.toList());
  }
}
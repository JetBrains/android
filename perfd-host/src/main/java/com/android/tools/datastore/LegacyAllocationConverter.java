/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.datastore;

import com.android.tools.profiler.proto.MemoryProfiler.AllocatedClass;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationEvent;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class to convert JDWP-based legacy allocation into the gRPC-based profiler allocation tracking format.
 */
public class LegacyAllocationConverter {
  public static class CallStack {
    @NotNull
    private final List<StackTraceElement> myCallStackFrames;

    @NotNull
    private final byte[] myId;

    private final int myHash;

    public CallStack(@NotNull List<StackTraceElement> frames) {
      myCallStackFrames = frames;
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        myId = digest.digest(toString().getBytes(StandardCharsets.UTF_8));
        assert myId.length >= 4;
        myHash = ByteBuffer.wrap(myId).getInt();
      }
      catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not defined on this system.");
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      for (StackTraceElement frame : myCallStackFrames) {
        builder.append(frame.toString());
        builder.append("\n");
      }
      return builder.toString();
    }

    @NotNull
    public byte[] getId() {
      return myId;
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof CallStack)) {
        return false;
      }
      return Arrays.equals(myId, ((CallStack)obj).myId);
    }

    @NotNull
    public AllocationStack getAllocationStack() {
      AllocationStack.Builder builder = AllocationStack.newBuilder().setStackId(ByteString.copyFrom(myId));
      for (StackTraceElement frame : myCallStackFrames) {
        builder.addStackFrames(
          AllocationStack.StackFrame.newBuilder().setClassName(frame.getClassName()).setLineNumber(frame.getLineNumber())
            .setMethodName(frame.getMethodName()).build());
      }
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

    @NotNull
    private final byte[] myCallStackId;

    public Allocation(int classId, int size, int threadId, @NotNull byte[] callStackId) {
      myClassId = classId;
      mySize = size;
      myThreadId = threadId;
      myCallStackId = callStackId;
    }

    @NotNull
    public AllocationEvent bindTimeToAllocationEvent(long time) {
      return AllocationEvent.newBuilder().setAllocatedClassId(myClassId).setSize(mySize).setThreadId(myThreadId).setTimestamp(time)
        .setAllocationStackId(ByteString.copyFrom(myCallStackId)).build();
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

  public List<AllocationEvent> getAllocationEvents(long time) {
    return myAllocations.stream().map(allocation -> allocation.bindTimeToAllocationEvent(time)).collect(Collectors.toList());
  }

  public List<AllocationStack> getAllocationStacks() {
    return myAllocationStacks.values().stream().map(CallStack::getAllocationStack).collect(Collectors.toList());
  }

  public List<AllocatedClass> getClassNames() {
    return myAllocatedClasses.values().stream().map(ClassName::getAllocatedClass).collect(Collectors.toList());
  }
}
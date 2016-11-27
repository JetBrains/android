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
    private final StackTraceElement[] myCallStackFrames;

    @NotNull
    private final byte[] mySha256;

    private final int myHash;

    public CallStack(@NotNull StackTraceElement[] frames) {
      myCallStackFrames = frames;
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        mySha256 = digest.digest(toString().getBytes(StandardCharsets.UTF_8));
        assert mySha256.length >= 4;
        myHash = ByteBuffer.wrap(mySha256).getInt();
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

    @Override
    public int hashCode() {
      return myHash;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof CallStack)) {
        return false;
      }
      return Arrays.equals(mySha256, ((CallStack)obj).mySha256);
    }

    @NotNull
    public AllocationStack getAllocationStack() {
      AllocationStack.Builder builder = AllocationStack.newBuilder().setStackId(ByteString.copyFrom(mySha256));
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

    @Override
    public int hashCode() {
      return myClassName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof ClassName) && myClassName.equals(((ClassName)obj).myClassName);
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
    private final byte[] myCallStack;

    public Allocation(int classId, int size, int threadId, @NotNull byte[] callStack) {
      myClassId = classId;
      mySize = size;
      myThreadId = threadId;
      myCallStack = callStack;
    }

    @NotNull
    public AllocationEvent getAllocationEvent(long time) {
      return AllocationEvent.newBuilder().setAllocatedClassId(myClassId).setSize(mySize).setThreadId(myThreadId).setTimestamp(time)
        .setAllocationStackId(ByteString.copyFrom(myCallStack)).build();
    }
  }

  @NotNull
  private List<Allocation> myAllocations = new ArrayList<>();

  @NotNull
  private Map<String, ClassName> myAllocatedClasses = new HashMap<>();

  @NotNull
  private Set<CallStack> myAllocationStacks = new HashSet<>();

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

  public byte[] addCallStack(@NotNull CallStack callStack) {
    if (!myAllocationStacks.contains(callStack)) {
      myAllocationStacks.add(callStack);
    }

    return callStack.mySha256;
  }

  public void addAllocation(@NotNull Allocation allocationInfo) {
    myAllocations.add(allocationInfo);
  }

  public List<AllocationEvent> getAllocationEvents(long time) {
    return myAllocations.stream().map(allocation -> allocation.getAllocationEvent(time)).collect(Collectors.toList());
  }

  public List<AllocationStack> getAllocationStacks() {
    return myAllocationStacks.stream().map(CallStack::getAllocationStack).collect(Collectors.toList());
  }

  public List<AllocatedClass> getClassNames() {
    return myAllocatedClasses.values().stream().map(ClassName::getAllocatedClass).collect(Collectors.toList());
  }
}
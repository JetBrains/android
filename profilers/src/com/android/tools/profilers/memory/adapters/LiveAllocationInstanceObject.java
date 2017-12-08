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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoRequest;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoResponse;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ThreadId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LiveAllocationInstanceObject implements InstanceObject {
  @NotNull private final LiveAllocationCaptureObject myCaptureObject;
  @NotNull private final ClassDb.ClassEntry myClassEntry;
  @Nullable private final LiveAllocationInstanceObject myClassObject;
  @NotNull private final ValueType myValueType;
  private long myAllocTime = Long.MIN_VALUE;
  private long myDeallocTime = Long.MAX_VALUE;
  private final long mySize;
  private final int myHeapId;
  @Nullable private AllocationStack myCallstack;
  @Nullable private final ThreadId myThreadId;

  public LiveAllocationInstanceObject(@NotNull LiveAllocationCaptureObject captureObject,
                                      @NotNull ClassDb.ClassEntry classEntry,
                                      @Nullable LiveAllocationInstanceObject classObject,
                                      @Nullable ThreadId threadId,
                                      @Nullable AllocationStack callstack,
                                      long size,
                                      int heapId) {
    myCaptureObject = captureObject;
    myClassEntry = classEntry;
    myClassObject = classObject;
    mySize = size;
    myHeapId = heapId;
    myThreadId = threadId == null ? ThreadId.INVALID_THREAD_ID : threadId;
    myCallstack = callstack;
    if ("java.lang.String".equals(classEntry.getClassName())) {
      myValueType = ValueType.STRING;
    }
    else if (classEntry.getClassName().endsWith("[]")) {
      myValueType = ValueType.ARRAY;
    }
    else {
      myValueType = ValueType.OBJECT;
    }
  }

  @Override
  public long getAllocTime() {
    return myAllocTime;
  }

  // Set deallocTime as Long.MAX_VALUE when no deallocation event can be found
  public void setDeallocTime(long deallocTime) {
    myDeallocTime = deallocTime;
  }

  // Set allocTime as Long.MIN_VALUE when no allocation event can be found
  public void setAllocationTime(long allocTime) {
    myAllocTime = allocTime;
  }

  @Override
  public long getDeallocTime() {
    return myDeallocTime;
  }

  @Override
  public boolean hasTimeData() {
    return hasAllocData() || hasDeallocData();
  }

  @Override
  public boolean hasAllocData() {
    return myAllocTime != Long.MIN_VALUE;
  }

  @Override
  public boolean hasDeallocData() {
    return myDeallocTime != Long.MAX_VALUE;
  }

  @NotNull
  @Override
  public String getName() {
    return "";
  }

  @Override
  public int getHeapId() {
    return myHeapId;
  }

  @Override
  public int getShallowSize() {
    // TODO upgrade to long
    return (int)mySize;
  }

  @Nullable
  @Override
  public AllocationStack getCallStack() {
    return myCallstack;
  }

  @NotNull
  @Override
  public List<CodeLocation> getCodeLocations() {
    List<CodeLocation> codeLocations = new ArrayList<>();
    if (myCallstack != null && myCallstack.getFrameCase() == AllocationStack.FrameCase.SMALL_STACK) {
      AllocationStack.SmallFrameWrapper smallFrames = myCallstack.getSmallStack();
      for (AllocationStack.SmallFrame frame : smallFrames.getFramesList()) {
        StackFrameInfoResponse frameInfo =
          myCaptureObject.getClient().getStackFrameInfo(StackFrameInfoRequest.newBuilder()
                                                          .setSession(myCaptureObject.getSession())
                                                          .setMethodId(frame.getMethodId()).build());
        CodeLocation.Builder builder = new CodeLocation.Builder(frameInfo.getClassName())
          .setMethodName(frameInfo.getMethodName())
          .setLineNumber(frame.getLineNumber() - 1);
        codeLocations.add(builder.build());
      }
    }

    return codeLocations;
  }

  @NotNull
  @Override
  public ThreadId getAllocationThreadId() {
    return myThreadId;
  }

  @NotNull
  @Override
  public ClassDb.ClassEntry getClassEntry() {
    return myClassEntry;
  }

  @Nullable
  @Override
  public InstanceObject getClassObject() {
    return myClassObject;
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return myValueType;
  }

  @NotNull
  @Override
  public String getValueText() {
    return myClassEntry.getSimpleClassName();
  }
}

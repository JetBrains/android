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

import com.android.tools.profiler.proto.MemoryProfiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LiveAllocationInstanceObject implements InstanceObject {
  @NotNull private final ClassDb.ClassEntry myClassEntry;
  @Nullable private final LiveAllocationInstanceObject myClassObject;
  @NotNull private final ValueType myValueType;
  private long myAllocTime = Long.MIN_VALUE;
  private long myDeallocTime = Long.MAX_VALUE;
  private final long mySize;
  @Nullable private MemoryProfiler.AllocationStack myCallstack;

  public LiveAllocationInstanceObject(@NotNull ClassDb.ClassEntry classEntry,
                                      @Nullable LiveAllocationInstanceObject classObject,
                                      long size) {
    myClassEntry = classEntry;
    myClassObject = classObject;
    mySize = size;
    myCallstack = null;
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

  @NotNull
  @Override
  public String getName() {
    return "";
  }

  @Override
  public int getHeapId() {
    return LiveAllocationCaptureObject.DEFAULT_HEAP_ID;
  }

  @Override
  public int getShallowSize() {
    // TODO upgrade to long
    return (int)mySize;
  }

  @Nullable
  @Override
  public MemoryProfiler.AllocationStack getCallStack() {
    return myCallstack;
  }

  public void setCallStack(MemoryProfiler.AllocationStack callstack) {
    myCallstack = callstack;
  }

  @Override
  public void removeCallstack() {
    myCallstack = null;
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

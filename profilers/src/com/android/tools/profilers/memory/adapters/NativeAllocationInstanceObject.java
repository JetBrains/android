
/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.profiler.proto.Memory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This wrapper class returns the minimal native allocation information needed for an {@link InstanceObject}.
 */
public class NativeAllocationInstanceObject implements InstanceObject {
  @NotNull private final Memory.AllocationEvent.Allocation myEvent;
  @NotNull private final ClassDb.ClassEntry myAllocationClassEntry;
  @NotNull private final Memory.AllocationStack myCallStack;
  @NotNull private final ValueObject.ValueType myValueType;
  private final boolean myIsAllocation;
  private final int myCount;

  /**
   * An object should be created for each unique allocation sample parsed from the perfetto capture.
   *
   * @param event                contains the total size to be represented by this {@link InstanceObject}
   * @param allocationClassEntry a class entry that represents the allocation function name (eg new).
   * @param callStack            is expected to be the full non-encoded callstack to the allocation location.
   * @param count                the number of allocations or (negative if deallocations) represented by this {@link InstanceObject}
   */
  public NativeAllocationInstanceObject(@NotNull Memory.AllocationEvent.Allocation event,
                                        @NotNull ClassDb.ClassEntry allocationClassEntry,
                                        @NotNull Memory.AllocationStack callStack,
                                        long count) {
    myEvent = event;
    myAllocationClassEntry = allocationClassEntry;
    myCallStack = callStack;
    myValueType = ValueType.BYTE;
    myIsAllocation = count > 0;
    myCount = (int)Math.abs(count);
  }

  @Override
  public int getInstanceCount() {
    return myCount;
  }

  @Override
  public int getHeapId() {
    return 0;
  }

  @NotNull
  @Override
  public ClassDb.ClassEntry getClassEntry() {
    return myAllocationClassEntry;
  }

  @Override
  public boolean hasTimeData() {
    return true;
  }

  @Override
  public boolean hasAllocTime() {
    return myIsAllocation;
  }

  @Override
  public boolean hasDeallocTime() {
    return !myIsAllocation;
  }

  /**
   * @return The callstack proto associated with the Instance's allocation event.
   */
  @Nullable
  @Override
  public Memory.AllocationStack getAllocationCallStack() {
    return myCallStack;
  }

  @Override
  public long getNativeSize() {
    return myEvent.getSize();
  }

  @Override
  public long getRetainedSize() {
    return myEvent.getSize();
  }

  @Override
  public int getShallowSize() {
    return (int)myEvent.getSize();
  }

  @Override
  public int getCallStackDepth() {
    Memory.AllocationStack callStack = getAllocationCallStack();
    if (callStack == null) {
      return 0;
    }
    return callStack.getFullStack().getFramesCount();
  }

  @Override
  public boolean getIsRoot() {
    return InstanceObject.super.getIsRoot();
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return myValueType;
  }

  @NotNull
  @Override
  public String getName() {
    return myAllocationClassEntry.getClassName();
  }
}
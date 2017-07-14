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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ThreadId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public interface InstanceObject extends ValueObject {
  int getHeapId();

  @NotNull
  default ThreadId getAllocationThreadId() {
    return ThreadId.INVALID_THREAD_ID;
  }

  @NotNull
  ClassDb.ClassEntry getClassEntry();

  @Nullable
  InstanceObject getClassObject();

  default int getFieldCount() {
    return 0;
  }

  @NotNull
  default List<FieldObject> getFields() {
    return Collections.emptyList();
  }

  // Specialized getter for array access (if and only if this instance represents an array).
  @Nullable
  default ArrayObject getArrayObject() {
    return null;
  }

  default long getAllocTime() {
    return Long.MIN_VALUE;
  }

  default long getDeallocTime() {
    return Long.MAX_VALUE;
  }

  /**
   * @return The callstack proto associated with the Instance's allocation event.
   */
  @Nullable
  default AllocationStack getCallStack() {
    return null;
  }

  default int getCallStackDepth() {
    AllocationStack callStack = getCallStack();
    if (callStack == null) {
      return 0;
    }

    switch (callStack.getFrameCase()) {
      case FULL_STACK:
        return callStack.getFullStack().getFramesCount();
      case SMALL_STACK:
        return callStack.getSmallStack().getFramesCount();
      default:
        return 0;
    }
  }

  /**
   * @return The IJ-friendly callstack which can be used to navigate to the user code using the StackTraceView.
   */
  @NotNull
  default List<CodeLocation> getCodeLocations() {
    AllocationStack callStack = getCallStack();
    if (callStack != null && callStack.getFrameCase() == AllocationStack.FrameCase.FULL_STACK) {
      AllocationStack.StackFrameWrapper fullStack = callStack.getFullStack();
      if (!fullStack.getFramesList().isEmpty()) {
        List<CodeLocation> stackFrames = fullStack.getFramesList().stream()
          .map(AllocationStackConverter::getCodeLocation)
          .collect(Collectors.toList());

        return stackFrames;
      }
    }

    return Collections.emptyList();
  }

  @NotNull
  default List<ReferenceObject> getReferences() {
    return Collections.emptyList();
  }

  default boolean getIsRoot() {
    return false;
  }

  default boolean hasTimeData() {
    return false;
  }

  default boolean hasAllocData() {
    return false;
  }

  default boolean hasDeallocData() {
    return false;
  }
}

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
import com.android.tools.profilers.stacktrace.ThreadId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

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

  @Nullable
  default AllocationStack getCallStack() {
    return null;
  }

  @NotNull
  default List<ReferenceObject> getReferences() {
    return Collections.emptyList();
  }

  default boolean getIsRoot() {
    return false;
  }

  default void removeCallstack() {};
}

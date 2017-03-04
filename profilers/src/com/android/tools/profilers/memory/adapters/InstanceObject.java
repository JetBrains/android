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

public interface InstanceObject extends MemoryObject {
  enum InstanceAttribute {
    LABEL(1),
    DEPTH(0),
    SHALLOW_SIZE(2),
    RETAINED_SIZE(3);

    private final int myWeight;

    InstanceAttribute(int weight) {
      myWeight = weight;
    }

    public int getWeight() {
      return myWeight;
    }
  }

  @Nullable
  default String getToStringText() {
    return null;
  }

  @Nullable
  ClassObject getClassObject();

  @Nullable
  String getClassName();

  default int getDepth() {
    return Integer.MAX_VALUE;
  }

  default int getShallowSize() {
    return INVALID_VALUE;
  }

  default long getRetainedSize() {
    return INVALID_VALUE;
  }

  default int getFieldCount() {
    return 0;
  }

  @NotNull
  default List<FieldObject> getFields() {
    return Collections.emptyList();
  }

  @NotNull
  default ThreadId getAllocationThreadId() {
    return ThreadId.INVALID_THREAD_ID;
  }

  @Nullable
  default AllocationStack getCallStack() {
    return null;
  }

  @NotNull
  default List<ReferenceObject> getReferences() {
    return Collections.emptyList();
  }

  @NotNull
  default ClassObject.ValueType getValueType() {
    return ClassObject.ValueType.NULL;
  }

  default boolean getIsArray() {
    return false;
  }

  default boolean getIsPrimitive() {
    return false;
  }

  default boolean getIsRoot() {
    return false;
  }

  default List<InstanceAttribute> getReferenceAttributes() {
    return Collections.emptyList();
  }
}

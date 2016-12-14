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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface InstanceObject extends MemoryObject {
  enum ValueType {
    NULL(false),
    BOOLEAN(true),
    BYTE(true),
    CHAR(true),
    SHORT(true),
    INT(true),
    LONG(true),
    FLOAT(true),
    DOUBLE(true),
    OBJECT(false),
    CLASS(false),
    STRING(false); // special case for strings

    private boolean myIsPrimitive;

    ValueType(boolean isPrimitive) {
      myIsPrimitive = isPrimitive;
    }

    public boolean getIsPrimitive() {
      return myIsPrimitive;
    }
  }

  @NotNull
  String getName();

  default int getDepth() {
    return 0;
  }

  default int getShallowSize() {
    return 0;
  }

  default long getRetainedSize() {
    return 0;
  }

  @NotNull
  default List<FieldObject> getFields() {
    return Collections.emptyList();
  }

  @Nullable
  default AllocationStack getCallStack() {
    return null;
  }

  @NotNull
  default String getValueLabel() {
    return "";
  }

  default ValueType getValueType() {
    return ValueType.NULL;
  }

  default boolean getIsArray() {
    return false;
  }

  default boolean getIsPrimitive() {
    return false;
  }
}

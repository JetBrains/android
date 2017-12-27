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

import org.jetbrains.annotations.NotNull;

/**
 * A class that represents a value in memory, such as a primitive, a reference, or even the value of {@code null}.
 */
public interface ValueObject extends MemoryObject {
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
    ARRAY(false),
    STRING(false); // special case for strings

    private boolean myIsPrimitive;

    ValueType(boolean isPrimitive) {
      myIsPrimitive = isPrimitive;
    }

    public boolean getIsPrimitive() {
      return myIsPrimitive;
    }
  }

  default int getDepth() {
    return Integer.MAX_VALUE;
  }

  default long getNativeSize() {
    return INVALID_VALUE;
  }

  default int getShallowSize() {
    return INVALID_VALUE;
  }

  default long getRetainedSize() {
    return INVALID_VALUE;
  }

  @NotNull
  ValueType getValueType();

  @NotNull
  default String getValueText() {
    return "";
  }

  @NotNull
  default String getToStringText() {
    return "";
  }
}

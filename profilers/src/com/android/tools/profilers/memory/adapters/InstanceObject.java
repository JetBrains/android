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

public abstract class InstanceObject implements MemoryObject {
  public enum ValueType {
    UNKNOWN,
    BOOLEAN,
    BYTE,
    CHAR,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    OBJECT,
    CLASS,
    STRING // special case for strings
  }

  @NotNull
  public abstract String getName();

  public int getDepth() {
    return 0;
  }

  public int getShallowSize() {
    return 0;
  }

  public long getRetainedSize() {
    return 0;
  }

  @Nullable
  public List<FieldObject> getFields() {
    return Collections.emptyList();
  }

  @Nullable
  public AllocationStack getCallStack() {
    return null;
  }

  @NotNull
  public String getValueLabel() {
    return "";
  }

  public ValueType getValueType() {
    return ValueType.UNKNOWN;
  }

  public boolean getIsArray() {
    return false;
  }
}

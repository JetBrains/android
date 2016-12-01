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

import com.android.tools.profiler.proto.MemoryProfiler;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class AllocationsInstanceObject implements InstanceObject {
  @NotNull static final Map<String, ValueType> ourTypeMap = ImmutableMap.<String, ValueType>builder()
    .put("boolean", ValueType.BOOLEAN)
    .put("byte", ValueType.BYTE)
    .put("char", ValueType.CHAR)
    .put("short", ValueType.SHORT)
    .put("int", ValueType.INT)
    .put("long", ValueType.LONG)
    .put("float", ValueType.FLOAT)
    .put("double", ValueType.DOUBLE)
    .build();

  @NotNull private final MemoryProfiler.MemoryData.AllocationEvent myEvent;
  @NotNull private final AllocationsClassObject myAllocationsClassObject;
  @NotNull private final MemoryProfiler.AllocationStack myCallStack;
  @NotNull private final ValueType myValueType;

  public AllocationsInstanceObject(@NotNull MemoryProfiler.MemoryData.AllocationEvent event,
                                   @NotNull AllocationsClassObject allocationsClassObject,
                                   @NotNull MemoryProfiler.AllocationStack callStack) {
    myEvent = event;
    myAllocationsClassObject = allocationsClassObject;
    myCallStack = callStack;

    String className = myAllocationsClassObject.getName();
    if (className.contains(".")) {
      if (className.equals("java.lang.String")) {
        myValueType = ValueType.STRING;
      }
      else {
        myValueType = ValueType.OBJECT;
      }
    }
    else {
      String trimmedClassName = className;
      if (getIsArray()) {
        trimmedClassName = className.substring(0, className.length() - "[]".length());
      }
      myValueType = ourTypeMap.getOrDefault(trimmedClassName, ValueType.OBJECT);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return myAllocationsClassObject.getName();
  }

  @Override
  public int getShallowSize() {
    return myEvent.getSize();
  }

  @NotNull
  @Override
  public MemoryProfiler.AllocationStack getCallStack() {
    return myCallStack;
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return myValueType;
  }

  @Override
  public boolean getIsArray() {
    return myAllocationsClassObject.getName().endsWith("[]");
  }

  @Override
  public boolean getIsPrimitive() {
    return myValueType.getIsPrimitive();
  }
}

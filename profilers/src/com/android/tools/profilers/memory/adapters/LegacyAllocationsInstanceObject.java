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

import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.memory.adapters.ClassDb.ClassEntry;
import com.android.tools.profilers.stacktrace.ThreadId;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class LegacyAllocationsInstanceObject implements InstanceObject {
  @NotNull static final Map<String, ValueObject.ValueType> ourTypeMap = ImmutableMap.<String, ValueObject.ValueType>builder()
    .put("boolean", ValueObject.ValueType.BOOLEAN)
    .put("byte", ValueObject.ValueType.BYTE)
    .put("char", ValueObject.ValueType.CHAR)
    .put("short", ValueObject.ValueType.SHORT)
    .put("int", ValueObject.ValueType.INT)
    .put("long", ValueObject.ValueType.LONG)
    .put("float", ValueObject.ValueType.FLOAT)
    .put("double", ValueObject.ValueType.DOUBLE)
    .build();

  @NotNull private final LegacyAllocationEvent myEvent;
  @NotNull private final ClassEntry myAllocationClassEntry;
  @NotNull private final AllocationStack myCallStack;
  @NotNull private final ValueObject.ValueType myValueType;
  @NotNull private final ThreadId myThreadId;

  public LegacyAllocationsInstanceObject(@NotNull LegacyAllocationEvent event,
                                         @NotNull ClassEntry allocationClassEntry,
                                         @NotNull AllocationStack callStack) {
    myEvent = event;
    myAllocationClassEntry = allocationClassEntry;
    myCallStack = callStack;
    myThreadId = new ThreadId(event.getThreadId());

    String className = myAllocationClassEntry.getClassName();
    if (className.contains(".")) {
      if (className.equals(ClassDb.JAVA_LANG_STRING)) {
        myValueType = ValueObject.ValueType.STRING;
      }
      else {
        myValueType = ValueObject.ValueType.OBJECT;
      }
    }
    else {
      if (myAllocationClassEntry.getClassName().endsWith("[]")) {
        myValueType = ValueObject.ValueType.ARRAY;
      }
      else {
        myValueType = ourTypeMap.getOrDefault(className, ValueObject.ValueType.OBJECT);
      }
    }
  }

  @NotNull
  @Override
  public String getName() {
    return "";
  }

  @Override
  public int getHeapId() {
    return LegacyAllocationCaptureObject.DEFAULT_HEAP_ID;
  }

  @NotNull
  @Override
  public ClassEntry getClassEntry() {
    return myAllocationClassEntry;
  }

  @Nullable
  @Override
  public InstanceObject getClassObject() {
    return null;
  }

  @Override
  public int getShallowSize() {
    return myEvent.getSize();
  }

  @Override
  @NotNull
  public ThreadId getAllocationThreadId() {
    return myThreadId;
  }

  @NotNull
  @Override
  public AllocationStack getCallStack() {
    return myCallStack;
  }

  @NotNull
  @Override
  public ValueObject.ValueType getValueType() {
    return myValueType;
  }

  @NotNull
  @Override
  public String getValueText() {
    return myAllocationClassEntry.getSimpleClassName();
  }
}

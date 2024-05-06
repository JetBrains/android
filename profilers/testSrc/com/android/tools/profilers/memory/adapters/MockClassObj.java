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

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockClassObj extends ClassObj {
  // keep insertion order with LinkedHashMap so we have deterministic positions when validating the fields
  @NotNull private final Map<Field, Object> myStaticFields = new LinkedHashMap<>();
  private final int myRootDistance;

  public MockClassObj(int id, @NotNull String className, int rootDistance) {
    super(id, null /* StackTrace - don't care */, className, 0 /* offset - don't care */);
    myRootDistance = rootDistance;
  }

  @Override
  public long getUniqueId() {
    return getId();
  }

  @Override
  public Heap getHeap() {
    Heap mockHeap = mock(Heap.class);
    when(mockHeap.getId()).thenReturn(-1);
    return mockHeap;
  }

  @Override
  public Map<Field, Object> getStaticFieldValues() {
    return myStaticFields;
  }

  public void addStaticField(@NotNull Type fieldType, @NotNull String fieldName, @Nullable Object value) {
    myStaticFields.put(new Field(fieldType, fieldName), value);
  }

  @Override
  public int getDistanceToGcRoot() {
    return myRootDistance;
  }
}

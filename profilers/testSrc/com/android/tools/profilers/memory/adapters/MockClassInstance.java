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

import com.android.tools.perflib.heap.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockClassInstance extends ClassInstance {
  @Nullable private final ArrayList<Instance> mySoftReferences;
  @NotNull private final ArrayList<Instance> myHardReferences;
  @NotNull private final List<FieldValue> myFieldValues = new ArrayList<>();
  private final int myRootDistance;
  @NotNull private final String myClassName;

  public MockClassInstance(int id, int rootDistance, @NotNull String className) {
    super(id, null /* StackTrace - don't care */, 0 /* valuesOffset - don't care */);
    myRootDistance = rootDistance;
    myClassName = className;
    mySoftReferences = new ArrayList<>();
    myHardReferences = new ArrayList<>();
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

  @NotNull
  @Override
  public List<FieldValue> getValues() {
    return myFieldValues;
  }

  public void addFieldValue(@NotNull Type fieldType, @NotNull String fieldName, @Nullable Object value) {
    Field field = new Field(fieldType, fieldName);
    myFieldValues.add(new FieldValue(field, value));
  }

  @Override
  public int getDistanceToGcRoot() {
    return myRootDistance;
  }

  @Override
  public ArrayList<Instance> getSoftReverseReferences() {
    return mySoftReferences;
  }

  @NotNull
  @Override
  public ArrayList<Instance> getHardReverseReferences() {
    return myHardReferences;
  }

  public void addSoftReferences(@NotNull Instance instance) {
    mySoftReferences.add(instance);
  }

  public void addHardReference(@NotNull Instance instance) {
    myHardReferences.add(instance);
  }

  @Override
  public ClassObj getClassObj() {
    return new MockClassObj(-1, myClassName, 0);
  }
}

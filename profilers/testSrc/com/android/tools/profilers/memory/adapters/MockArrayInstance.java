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

import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockArrayInstance extends ArrayInstance {
  @NotNull private final Object[] myArrayValues;
  private final int myRootDistance;

  public MockArrayInstance(int id, @NotNull Type arrayType, int length, int rootDistance) {
    super(id, null /* StackTrace - don't care */, arrayType, length, 0 /* valuesOffset - don't care */);
    myArrayValues = new Object[length];
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
  public Object[] getValues() {
    return myArrayValues;
  }

  public void setValue(int index, @Nullable Object object) {
    assert index >= 0 && index < myArrayValues.length;
    myArrayValues[index] = object;
  }

  @Override
  public int getDistanceToGcRoot() {
    return myRootDistance;
  }

  @Override
  public ClassObj getClassObj() {
    return new MockClassObj(-1, "MockClass", 0);
  }
}

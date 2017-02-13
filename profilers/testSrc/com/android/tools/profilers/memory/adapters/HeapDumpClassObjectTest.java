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

import com.android.tools.perflib.heap.Heap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

public class HeapDumpClassObjectTest {
  @Test
  public void testEqual() throws Exception {
    MockClassObj mockClass = new MockClassObj(-1, "MockClass1", 3);
    HeapDumpHeapObject mockHeap = new HeapDumpHeapObject(mock(Heap.class));
    HeapDumpClassObject classObject1 = new HeapDumpClassObject(mockHeap, mockClass);
    HeapDumpClassObject classObject2 = new HeapDumpClassObject(mockHeap, mockClass);
    assertEquals(classObject1, classObject2);
  }

  @Test
  public void testNotEqual() throws Exception {
    MockClassObj mockClass1 = new MockClassObj(-1, "MockClass1", 3);
    MockClassObj mockClass2 = new MockClassObj(-1, "MockClass1", 3);
    HeapDumpHeapObject mockHeap = new HeapDumpHeapObject(mock(Heap.class));
    HeapDumpClassObject classObject1 = new HeapDumpClassObject(mockHeap, mockClass1);
    HeapDumpClassObject classObject2 = new HeapDumpClassObject(mockHeap, mockClass2);
    assertNotEquals(classObject1, classObject2);
  }
}
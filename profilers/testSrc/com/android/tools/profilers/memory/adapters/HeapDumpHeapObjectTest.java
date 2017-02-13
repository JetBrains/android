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
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import gnu.trove.TObjectProcedure;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class HeapDumpHeapObjectTest {
  @Test
  public void testEqual() throws Exception {
    Heap mockHeap = mock(Heap.class);
    HeapDumpHeapObject heap1 = new HeapDumpHeapObject(mockHeap);
    HeapDumpHeapObject heap2 = new HeapDumpHeapObject(mockHeap);
    assertEquals(heap1.getHeap(), heap2.getHeap());
    assertEquals(heap1, heap2);
  }

  @Test
  public void testNotEqual() throws Exception {
    Heap mockHeap1 = mock(Heap.class);
    Heap mockHeap2 = mock(Heap.class);
    HeapDumpHeapObject heap1 = new HeapDumpHeapObject(mockHeap1);
    HeapDumpHeapObject heap2 = new HeapDumpHeapObject(mockHeap2);
    assertNotEquals(heap1.getHeap(), heap2.getHeap());
    assertNotEquals(heap1, heap2);
  }

  @Test
  public void testCorrectClassObjectsGenerated() throws Exception {
    // Adding fake class objects to the heap.
    ClassObj klass1 = new MockClassObj(1, "MockClass1", 1);
    ClassObj klass2 = new MockClassObj(2, "MockClass2", 2);
    ClassObj klass3 = new MockClassObj(3, "MockClass3", 3);
    Heap heap = mock(Heap.class);
    when(heap.getName()).thenReturn("FakeHeap");
    when(heap.getClasses()).thenReturn(Arrays.asList(klass1, klass2, klass3));

    // Adding fake class instances to the heap.
    LinkedList<Instance> instances = new LinkedList<>();
    instances.add(new MockClassInstance(4, 4, "MockClass4"));
    instances.add(new MockClassInstance(5, 5, "MockClass5"));
    instances.add(new MockClassInstance(6, 6, "MockClass6"));
    doAnswer(invocation -> {
      TObjectProcedure proc = (TObjectProcedure<Instance>)invocation.getArguments()[0];
      for (Instance instance : instances) {
        proc.execute(instance);
      }
      return null;
    }).when(heap).forEachInstance(anyObject());

    // Verify that the list of class objects contains classes referred to by both the ClassObjs and the Instances in the heap.
    HeapDumpHeapObject heapObject = new HeapDumpHeapObject(heap);
    List<ClassObject> classes = heapObject.getClasses();
    assertEquals(6, classes.size());
    assertEquals("MockClass1", classes.get(0).getClassName());
    assertEquals("MockClass2", classes.get(1).getClassName());
    assertEquals("MockClass3", classes.get(2).getClassName());
    assertEquals("MockClass4", classes.get(3).getClassName());
    assertEquals("MockClass5", classes.get(4).getClassName());
    assertEquals("MockClass6", classes.get(5).getClassName());
  }
}
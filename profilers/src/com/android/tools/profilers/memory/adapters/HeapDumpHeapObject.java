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

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.profilers.memory.adapters.ClassObject.ClassAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.android.tools.profilers.memory.adapters.ClassObject.ClassAttribute.*;

/**
 * A UI representation for a {@link Heap}.
 */
final class HeapDumpHeapObject implements HeapObject {
  @NotNull
  private final Heap myHeap;

  @Nullable
  private List<ClassObject> myClassObjects = null;

  public HeapDumpHeapObject(@NotNull Heap heap) {
    myHeap = heap;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HeapDumpHeapObject)) {
      return false;
    }

    HeapDumpHeapObject otherHeap = (HeapDumpHeapObject)obj;
    return myHeap == otherHeap.myHeap;
  }

  @Override
  public int hashCode() {
    return myHeap.hashCode();
  }

  @NotNull
  public Heap getHeap() {
    return myHeap;
  }

  @Override
  public String toString() {
    return getHeapName();
  }

  @NotNull
  @Override
  public String getHeapName() {
    return myHeap.getName();
  }

  @NotNull
  @Override
  public List<ClassObject> getClasses() {
    if (myClassObjects == null) {
      // Find the union of the classObjs this heap has instances of, plus the classObjs themselves that are allocated on this heap.
      Set<ClassObj> classes = new LinkedHashSet<>(myHeap.getClasses().size() + myHeap.getInstancesCount());
      classes.addAll(myHeap.getClasses());

      myHeap.forEachInstance(instance -> {
        classes.add(instance.getClassObj());
        return true;
      });
      myClassObjects = classes.stream().map(klass -> new HeapDumpClassObject(this, klass)).collect(Collectors.toList());
    }
    return myClassObjects;
  }

  @NotNull
  @Override
  public List<ClassAttribute> getClassAttributes() {
    return Arrays.asList(LABEL, TOTAL_COUNT, HEAP_COUNT, INSTANCE_SIZE, SHALLOW_SIZE, RETAINED_SIZE);
  }
}

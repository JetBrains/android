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

import com.android.tools.perflib.heap.Heap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.profilers.memory.adapters.HeapObject.ClassAttribute.*;

/**
 * A UI representation for a {@link Heap}.
 */
final class HeapDumpHeapObject implements HeapObject {
  @NotNull
  private final Heap myHeap;

  public HeapDumpHeapObject(@NotNull Heap heap) {
    myHeap = heap;
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
    return myHeap.getClasses().stream().map(HeapDumpClassObject::new).collect(Collectors.toList());
  }

  @NotNull
  @Override
  public List<ClassAttribute> getClassAttributes() {
    return Arrays.asList(LABEL, CHILDREN_COUNT, ELEMENT_SIZE, SHALLOW_SIZE, RETAINED_SIZE);
  }
}

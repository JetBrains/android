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

import com.android.tools.profiler.proto.MemoryProfiler.AllocatedClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.profilers.memory.adapters.InstanceObject.InstanceAttribute.LABEL;
import static com.android.tools.profilers.memory.adapters.InstanceObject.InstanceAttribute.SHALLOW_SIZE;

final class AllocationsClassObject extends ClassObject {
  @NotNull private final AllocatedClass myAllocatedClass;
  @NotNull private final List<InstanceObject> myInstanceNodes = new ArrayList<>();
  @NotNull private final AllocationsCaptureObject.AllocationsHeapObject myHeap;

  public AllocationsClassObject(@NotNull AllocationsCaptureObject.AllocationsHeapObject heap, @NotNull AllocatedClass allocatedClass) {
    super(allocatedClass.getClassName());
    myAllocatedClass = allocatedClass;
    myHeap = heap;
  }

  @NotNull
  @Override
  public HeapObject getHeapObject() {
    return myHeap;
  }

  public void addInstance(@NotNull AllocationsInstanceObject node) {
    myInstanceNodes.add(node);
  }

  @Override
  public int getHeapCount() {
    return myInstanceNodes.size();
  }

  @NotNull
  @Override
  public List<InstanceObject> getInstances() {
    return myInstanceNodes;
  }

  @NotNull
  @Override
  public List<InstanceObject.InstanceAttribute> getInstanceAttributes() {
    return Arrays.asList(LABEL, SHALLOW_SIZE);
  }
}

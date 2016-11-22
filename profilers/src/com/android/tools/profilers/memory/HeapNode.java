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
package com.android.tools.profilers.memory;

import com.android.tools.perflib.heap.Heap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: Add header comment
 */
class HeapNode implements MemoryNode {
  @NotNull
  private final Heap myHeap;

  public HeapNode(@NotNull Heap heap) {
    myHeap = heap;
  }

  @NotNull
  public Heap getHeap() {
    return myHeap;
  }

  @Override
  public String toString() {
    return getName();
  }

  @NotNull
  @Override
  public String getName() {
    return myHeap.getName();
  }

  @NotNull
  @Override
  public List<MemoryNode> getSubList(long startTime, long endTime) {
    return myHeap.getClasses().stream().map(ClassNode::new).collect(Collectors.toList());
  }

  @NotNull
  @Override
  public List<Capability> getCapabilities() {
    return Arrays
      .asList(Capability.LABEL, Capability.CHILDREN_COUNT, Capability.ELEMENT_SIZE, Capability.SHALLOW_SIZE, Capability.RETAINED_SIZE);
  }
}

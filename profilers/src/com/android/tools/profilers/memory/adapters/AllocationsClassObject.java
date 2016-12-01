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

import com.android.tools.profiler.proto.MemoryProfiler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.profilers.memory.adapters.ClassObject.InstanceAttribute.LABEL;
import static com.android.tools.profilers.memory.adapters.ClassObject.InstanceAttribute.SHALLOW_SIZE;

final class AllocationsClassObject implements ClassObject {
  @NotNull private final MemoryProfiler.AllocatedClass myAllocatedClass;
  @NotNull private final List<InstanceObject> myInstanceNodes = new ArrayList<>();

  public AllocationsClassObject(@NotNull MemoryProfiler.AllocatedClass allocatedClass) {
    myAllocatedClass = allocatedClass;
  }

  @NotNull
  @Override
  public String getName() {
    return myAllocatedClass.getClassName();
  }

  public void addInstance(@NotNull AllocationsInstanceObject node) {
    myInstanceNodes.add(node);
  }

  @Override
  public int getChildrenCount() {
    return myInstanceNodes.size();
  }

  @NotNull
  @Override
  public List<InstanceObject> getInstances() {
    return myInstanceNodes;
  }

  @NotNull
  @Override
  public List<InstanceAttribute> getInstanceAttributes() {
    return Arrays.asList(LABEL, SHALLOW_SIZE);
  }
}

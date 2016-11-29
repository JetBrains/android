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

import com.android.tools.perflib.heap.Instance;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class InstanceNode implements MemoryNode {
  @NotNull private final Instance myInstance;

  public InstanceNode(@NotNull Instance instance) {
    myInstance = instance;
  }

  @NotNull
  @Override
  public String getName() {
    // TODO show length of array instance
    return String.format("@%d (0x%x)", myInstance.getUniqueId(), myInstance.getUniqueId());
  }

  @Override
  public int getDepth() {
    return myInstance.getDistanceToGcRoot();
  }

  @Override
  public int getShallowSize() {
    return myInstance.getSize();
  }

  @Override
  public long getRetainedSize() {
    return myInstance.getTotalRetainedSize();
  }

  @NotNull
  @Override
  public List<MemoryNode> getSubList() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Capability> getCapabilities() {
    return Arrays.asList(Capability.LABEL, Capability.ELEMENT_SIZE);
  }
}

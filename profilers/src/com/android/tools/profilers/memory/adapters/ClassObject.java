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

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class ClassObject implements MemoryObject {
  public enum InstanceAttribute {
    LABEL,
    ELEMENT_SIZE,
    DEPTH,
    SHALLOW_SIZE,
    RETAINED_SIZE
  }

  @NotNull
  public String getName() {
    return "";
  }

  public int getChildrenCount() {
    return 0;
  }

  public int getElementSize() {
    return 0;
  }

  public int getDepth() {
    return 0;
  }

  public int getShallowSize() {
    return 0;
  }

  public long getRetainedSize() {
    return 0;
  }

  @NotNull
  public List<InstanceObject> getInstances() {
    return Collections.emptyList();
  }

  @NotNull
  public abstract List<InstanceAttribute> getInstanceAttributes();
}

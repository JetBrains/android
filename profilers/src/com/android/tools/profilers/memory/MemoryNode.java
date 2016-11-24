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

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public interface MemoryNode {
  enum Capability {
    LABEL,
    CHILDREN_COUNT,
    ELEMENT_SIZE,
    SHALLOW_SIZE,
    RETAINED_SIZE
  }

  @NotNull
  default String getName() {
    return "";
  }

  default int getChildrenCount() {
    return 0;
  }

  default int getElementSize() {
    return 0;
  }

  default int getShallowSize() {
    return 0;
  }

  default long getRetainedSize() {
    return 0;
  }

  @NotNull
  default List<MemoryNode> getSubList(long startTimeUs, long endTimeUs) {
    return Collections.emptyList();
  }

  @NotNull
  default List<Capability> getCapabilities() {
    return Collections.emptyList();
  }
}

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

public class NamespaceObject implements MemoryObject {
  protected final String myName;

  public NamespaceObject(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isInNamespace(@NotNull NamespaceObject target) {
    return false;
  }

  /**
   * @return number of instances across all heaps.
   */
  public int getTotalCount() {
    return INVALID_VALUE;
  }

  /**
   * @return number of instances on current heap.
   */
  public int getHeapCount() {
    return INVALID_VALUE;
  }

  /**
   * @return number of instances on current heap.
   */
  public long getRetainedSize() {
    return INVALID_VALUE;
  }

  /**
   * @return the (approximated?) size of each instance of the class.
   */
  public int getInstanceSize() {
    return INVALID_VALUE;
  }

  /**
   * @return size of instances on current heap.
   */
  public int getShallowSize() {
    return INVALID_VALUE;
  }

  /**
   * Accumulate the stats of the given {@link InstanceObject} into this {@link NamespaceObject}.
   */
  public void accumulateInstanceObject(@NotNull InstanceObject instanceObject) {}

  /**
   * Accumulate the stats of the given {@link NamespaceObject} into this {@link NamespaceObject}.
   */
  public void accumulateNamespaceObject(@NotNull NamespaceObject namespaceObject) {}

  /**
   * @return true if this {@link NamespaceObject} or any descendant nodes have stack information
   */
  public boolean hasStackInfo() {
    return false;
  }
}

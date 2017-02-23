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

import org.jetbrains.annotations.NotNull;

public abstract class ClassifierObject extends NamespaceObject {
  protected int myTotalCount = 0;
  protected int myHeapCount = 0;
  protected long myRetainedSize = 0L;

  public ClassifierObject(@NotNull String name) {
    super(name);
  }

  @Override
  public int getTotalCount() {
    return myTotalCount;
  }

  @Override
  public int getHeapCount() {
    return myHeapCount;
  }

  @Override
  public long getRetainedSize() {
    return myRetainedSize;
  }

  @Override
  public void accumulateInstanceObject(@NotNull InstanceObject instanceObject) {
    myHeapCount += 1;
    myTotalCount += 1;
    myRetainedSize += Math.max(instanceObject.getRetainedSize(), 0);
  }

  @Override
  public void accumulateNamespaceObject(@NotNull NamespaceObject namespaceObject) {
    myHeapCount += namespaceObject.getHeapCount();
    myTotalCount += namespaceObject.getTotalCount();
    myRetainedSize += Math.max(0, namespaceObject.getRetainedSize());
  }
}

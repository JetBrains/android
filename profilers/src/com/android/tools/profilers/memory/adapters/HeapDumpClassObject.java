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
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A UI representation of a {@link ClassObj}.
 */
final class HeapDumpClassObject implements ClassObject {
  private final ClassObj myClassObj;

  @NotNull
  private String myMemoizedName;

  public HeapDumpClassObject(@NotNull ClassObj classObj) {
    myClassObj = classObj;

    String className = myClassObj.getClassName();
    String packageName = null;
    int i = className.lastIndexOf(".");
    if (i != -1) {
      packageName = className.substring(0, i);
      className = className.substring(i + 1);
    }
    myMemoizedName = packageName == null ? className : String.format("%s (%s)", className, packageName);
  }

  @NotNull
  @Override
  public String getName() {
    return myMemoizedName;
  }

  @Override
  public int getChildrenCount() {
    return myClassObj.getInstanceCount();
  }

  @Override
  public int getElementSize() {
    return myClassObj.getSize();
  }

  @Override
  public int getShallowSize() {
    return myClassObj.getShallowSize();
  }

  @Override
  public long getRetainedSize() {
    return myClassObj.getTotalRetainedSize();
  }

  @NotNull
  @Override
  public List<InstanceObject> getInstances() {
    return myClassObj.getInstancesList().stream().map(HeapDumpInstanceObject::new).collect(Collectors.toList());
  }

  @NotNull
  @Override
  public List<InstanceAttribute> getInstanceAttributes() {
    return Arrays.asList(InstanceAttribute.LABEL, InstanceAttribute.DEPTH, InstanceAttribute.SHALLOW_SIZE, InstanceAttribute.RETAINED_SIZE);
  }
}

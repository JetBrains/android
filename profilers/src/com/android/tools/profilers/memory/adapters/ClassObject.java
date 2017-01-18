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

import com.android.tools.profilers.memory.adapters.InstanceObject.InstanceAttribute;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class ClassObject extends NamespaceObject {
  public enum ClassAttribute {
    LABEL,
    CHILDREN_COUNT,
    ELEMENT_SIZE,
    SHALLOW_SIZE,
    RETAINED_SIZE
  }

  @NotNull
  private final String myPackageName;

  @NotNull
  private final String myClassName;

  public ClassObject(@NotNull String fullyQualifiedClassName) {
    super(fullyQualifiedClassName);

    int lastIndexOfDot = fullyQualifiedClassName.lastIndexOf('.');
    myPackageName = lastIndexOfDot > 0 ? fullyQualifiedClassName.substring(0, lastIndexOfDot) : "";
    myClassName = fullyQualifiedClassName.substring(lastIndexOfDot + 1);
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }

  @NotNull
  public String[] getSplitPackageName() {
    //noinspection SSBasedInspection
    return myPackageName.isEmpty() ? new String[0] : myPackageName.split("\\.");
  }

  public int getChildrenCount() {
    return INVALID_VALUE;
  }

  public int getElementSize() {
    return INVALID_VALUE;
  }

  public int getShallowSize() {
    return INVALID_VALUE;
  }

  public long getRetainedSize() {
    return INVALID_VALUE;
  }

  @NotNull
  public List<InstanceObject> getInstances() {
    return Collections.emptyList();
  }

  @NotNull
  public abstract List<InstanceAttribute> getInstanceAttributes();
}

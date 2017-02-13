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
  public static final String JAVA_LANG_STRING = "java.lang.String";
  public static final String JAVA_LANG_CLASS = "java.lang.Class";

  public enum ClassAttribute {
    LABEL,
    TOTAL_COUNT,
    HEAP_COUNT,
    INSTANCE_SIZE,
    SHALLOW_SIZE,
    RETAINED_SIZE
  }

  public enum ValueType {
    NULL(false),
    BOOLEAN(true),
    BYTE(true),
    CHAR(true),
    SHORT(true),
    INT(true),
    LONG(true),
    FLOAT(true),
    DOUBLE(true),
    OBJECT(false),
    CLASS(false),
    STRING(false); // special case for strings

    private boolean myIsPrimitive;

    ValueType(boolean isPrimitive) {
      myIsPrimitive = isPrimitive;
    }

    public boolean getIsPrimitive() {
      return myIsPrimitive;
    }
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
  public abstract HeapObject getHeapObject();

  @NotNull
  public String getClassName() {
    return myClassName;
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  public String[] getSplitPackageName() {
    //noinspection SSBasedInspection
    return myPackageName.isEmpty() ? new String[0] : myPackageName.split("\\.");
  }

  /**
   * @return number of instances across all heaps.
   */
  @Override
  public int getTotalCount() {
    return INVALID_VALUE;
  }

  /**
   * @return number of instances on current heap.
   */
  @Override
  public int getHeapCount() {
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
   * @return number of instances on current heap.
   */
  @Override
  public long getRetainedSize() {
    return INVALID_VALUE;
  }

  /**
   * @return list of instances on current heap.
   */
  @NotNull
  public List<InstanceObject> getInstances() {
    return Collections.emptyList();
  }

  @NotNull
  public abstract List<InstanceAttribute> getInstanceAttributes();
}

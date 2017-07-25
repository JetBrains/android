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

import java.util.List;

/**
 * A {@link MemoryObject} that describes an object instance having a reference to another.
 */
public class ReferenceObject implements ValueObject {
  @NotNull private final List<String> myReferencingFieldNames;

  @NotNull private final InstanceObject myReferencingInstance;

  @NotNull private final String myName;

  public ReferenceObject(@NotNull List<String> referencingFieldNames, @NotNull InstanceObject referencingInstance) {
    myReferencingFieldNames = referencingFieldNames;
    myReferencingInstance = referencingInstance;

    StringBuilder builder = new StringBuilder();
    if (!myReferencingFieldNames.isEmpty()) {
      if (getValueType() == ValueObject.ValueType.ARRAY) {
        builder.append("Index ");
      }
      builder.append(String.join(", ", myReferencingFieldNames));
    }
    myName = builder.toString();
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  /**
   * @return the names of the fields of this object instance that holds a reference to the referree.
   * If this object is an array, then a list of indices pointing to the referree is returned.
   */
  @NotNull
  public List<String> getReferenceFieldNames() {
    return myReferencingFieldNames;
  }

  @NotNull
  public InstanceObject getReferenceInstance() {
    return myReferencingInstance;
  }

  @Override
  public int getDepth() {
    return myReferencingInstance.getDepth();
  }

  @Override
  public long getNativeSize() {
    return myReferencingInstance.getNativeSize();
  }

  @Override
  public int getShallowSize() {
    return myReferencingInstance.getShallowSize();
  }

  @Override
  public long getRetainedSize() {
    return myReferencingInstance.getRetainedSize();
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return myReferencingInstance.getValueType();
  }

  @NotNull
  @Override
  public String getValueText() {
    return myReferencingInstance.getValueText();
  }

  @NotNull
  @Override
  public String getToStringText() {
    return myReferencingInstance.getToStringText();
  }
}

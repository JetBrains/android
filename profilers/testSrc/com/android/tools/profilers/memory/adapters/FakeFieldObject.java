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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public class FakeFieldObject implements FieldObject {
  @NotNull private final String myFieldName;
  @NotNull private ValueType myValueType;
  @Nullable private Object myValue;

  public FakeFieldObject(@NotNull String fieldName, @NotNull ValueType valueType, @Nullable Object value) {
    myFieldName = fieldName;
    myValueType = valueType;
    myValue = value;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[]{myFieldName, myValueType, myValue});
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FakeFieldObject)) {
      return false;
    }

    FakeFieldObject other = (FakeFieldObject)obj;
    return myFieldName.equals(other.myFieldName) && Objects.equals(myValue, other.myValue) && myValueType == other.myValueType;
  }

  @NotNull
  @Override
  public String getName() {
    return myFieldName;
  }

  @NotNull
  @Override
  public String getFieldName() {
    return myFieldName;
  }

  @Nullable
  @Override
  public InstanceObject getAsInstance() {
    return myValueType == ValueType.NULL || myValueType.getIsPrimitive() ? null : (InstanceObject)myValue;
  }

  @Nullable
  @Override
  public Object getValue() {
    return myValue;
  }

  @Override
  public int getDepth() {
    InstanceObject instanceObject = getAsInstance();
    return instanceObject == null ? Integer.MAX_VALUE : instanceObject.getDepth();
  }

  @Override
  public int getShallowSize() {
    InstanceObject instanceObject = getAsInstance();
    return instanceObject == null ? INVALID_VALUE : instanceObject.getShallowSize();
  }

  @Override
  public long getRetainedSize() {
    InstanceObject instanceObject = getAsInstance();
    return instanceObject == null ? Integer.MAX_VALUE : instanceObject.getRetainedSize();
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return myValueType;
  }

  public void setFieldValue(@NotNull ValueType valueType, @Nullable Object value) {
    assert valueType == ValueType.NULL || value != null;
    myValueType = valueType;
    myValue = value;
  }

  @NotNull
  @Override
  public String getToStringText() {
    return myValueType == ValueType.NULL || myValue == null ?
           "{null}" :
           (myValueType.getIsPrimitive() ? myValue.toString() : ((InstanceObject)myValue).getName());
  }
}

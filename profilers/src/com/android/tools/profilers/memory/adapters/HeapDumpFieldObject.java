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

import com.android.tools.perflib.heap.*;
import com.android.tools.perflib.heap.ClassInstance.FieldValue;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A UI representation of a {@link Field}.
 */
final class HeapDumpFieldObject implements FieldObject {
  private static final Map<Type, ValueType> ourValueTypeMap = ImmutableMap.<Type, ValueType>builder()
    .put(Type.BOOLEAN, ValueType.BOOLEAN)
    .put(Type.BYTE, ValueType.BYTE)
    .put(Type.CHAR, ValueType.CHAR)
    .put(Type.SHORT, ValueType.SHORT)
    .put(Type.INT, ValueType.INT)
    .put(Type.LONG, ValueType.LONG)
    .put(Type.FLOAT, ValueType.FLOAT)
    .put(Type.DOUBLE, ValueType.DOUBLE)
    .build();

  @NotNull private final FieldValue myField;
  private final int myDepth;
  private final int myShallowSize;
  private final long myRetainedSize;
  private final ValueType myValueType;

  public HeapDumpFieldObject(@NotNull Instance parentInstance, @NotNull FieldValue field) {
    myField = field;
    Type type = myField.getField().getType();
    if (type == Type.OBJECT) {
      if (myField.getValue() == null) {
        myValueType = ValueType.NULL;
        myShallowSize = 0;
        myRetainedSize = 0;
        myDepth = -1;
      }
      else {
        Class valueClass = myField.getValue().getClass();
        Instance instance = (Instance)myField.getValue();
        if (ClassObj.class.isAssignableFrom(valueClass)) {
          myValueType = ValueType.CLASS;
        }
        else if (ClassInstance.class.isAssignableFrom(valueClass) &&
                 "java.lang.String".equals(((ClassInstance)myField.getValue()).getClassObj().getClassName())) {
          myValueType = ValueType.STRING;
        }
        else {
          myValueType = ValueType.OBJECT;
        }
        myShallowSize = instance.getSize();
        myRetainedSize = instance.getTotalRetainedSize();
        myDepth = instance.getDistanceToGcRoot();
      }
    }
    else {
      myValueType = ourValueTypeMap.getOrDefault(type, ValueType.NULL);
      myShallowSize = type.getSize();
      myRetainedSize = type.getSize();
      myDepth = parentInstance.getDistanceToGcRoot();
    }
  }

  @NotNull
  @Override
  public String getName() {
    if (myField.getValue() == null) {
      return myField.getField().getName() + "= {null}";
    }
    else {
      return myField.getField().getName() + "=" + myField.getValue().toString();
    }
  }

  @Override
  public int getShallowSize() {
    return myShallowSize;
  }

  @Override
  public long getRetainedSize() {
    return myRetainedSize;
  }

  @Override
  public int getDepth() {
    return myDepth;
  }

  @NotNull
  @Override
  public List<FieldObject> getFields() {
    Type type = myField.getField().getType();
    Object value = myField.getValue();
    // The field has children only if it is a non-primitive field.
    if (type == Type.OBJECT && value != null && value instanceof Instance) {
      return HeapDumpInstanceObject.extractFields((Instance)value);
    }

    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String getFieldName() {
    return myField.getField().getName();
  }

  @NotNull
  @Override
  public String getValueLabel() {
    return myField.getValue().toString();
  }

  @Override
  public ValueType getValueType() {
    return myValueType;
  }

  @Override
  public boolean getIsArray() {
    return myField.getValue() instanceof ArrayInstance;
  }

  @Override
  public boolean getIsPrimitive() {
    return myValueType.getIsPrimitive();
  }
}

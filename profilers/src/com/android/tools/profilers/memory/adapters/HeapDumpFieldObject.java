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

import java.util.Map;

/**
 * A UI representation of a {@link Field}.
 */
final class HeapDumpFieldObject extends HeapDumpInstanceObject implements FieldObject {
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

  public HeapDumpFieldObject(@NotNull Instance parentInstance, @NotNull FieldValue field, @Nullable Instance instance) {
    // TODO - is the ClassObj logic correct here? Should a ClassObj instance not have the "java.lang.Class" as its ClassObj?
    super(instance == null ? null
                           : new HeapDumpClassObject(new HeapDumpHeapObject(instance.getHeap()),
                                                     instance instanceof ClassObj ? (ClassObj)instance : instance.getClassObj()),
          instance);

    myField = field;
    Type type = myField.getField().getType();
    if (type == Type.OBJECT) {
      if (myField.getValue() == null) {
        myValueType = ValueType.NULL;
        myShallowSize = INVALID_VALUE;
        myRetainedSize = INVALID_VALUE;
        myDepth = INVALID_VALUE;
      }
      else {
        assert myField.getValue() == instance;
        if (instance instanceof ClassObj) {
          myValueType = ValueType.CLASS;
        }
        else if (instance instanceof ClassInstance && instance.getClassObj().getClassName().equals(STRING_NAMESPACE)) {
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
  public String getDisplayLabel() {
    return String.format(FIELD_DISPLAY_FORMAT, myField.getField().getName(), myField.getValue() == null ? "{null}" : myField.getValue());
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
  public String getFieldName() {
    return myField.getField().getName();
  }

  @Override
  public ValueType getValueType() {
    return myValueType;
  }

  @Override
  public boolean getIsPrimitive() {
    return myValueType.getIsPrimitive();
  }
}

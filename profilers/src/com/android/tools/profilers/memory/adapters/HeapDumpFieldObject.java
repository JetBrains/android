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
  private static final Map<Type, ClassObject.ValueType> ourPrimitiveValueTypeMap = ImmutableMap.<Type, ClassObject.ValueType>builder()
    .put(Type.BOOLEAN, ClassObject.ValueType.BOOLEAN)
    .put(Type.BYTE, ClassObject.ValueType.BYTE)
    .put(Type.CHAR, ClassObject.ValueType.CHAR)
    .put(Type.SHORT, ClassObject.ValueType.SHORT)
    .put(Type.INT, ClassObject.ValueType.INT)
    .put(Type.LONG, ClassObject.ValueType.LONG)
    .put(Type.FLOAT, ClassObject.ValueType.FLOAT)
    .put(Type.DOUBLE, ClassObject.ValueType.DOUBLE)
    .build();

  @NotNull private final FieldValue myField;
  private final int myDepth;
  private final int myShallowSize;
  private final long myRetainedSize;

  public HeapDumpFieldObject(@NotNull Instance parentInstance, @NotNull FieldValue field, @Nullable Instance instance) {
    // TODO - is the ClassObj logic correct here? Should a ClassObj instance not have the "java.lang.Class" as its ClassObj?
    super(instance == null ? null
                           : new HeapDumpClassObject(new HeapDumpHeapObject(instance.getHeap()),
                                                     instance instanceof ClassObj ? (ClassObj)instance : instance.getClassObj()),
          instance, null);

    myField = field;
    Type type = myField.getField().getType();
    if (type == Type.OBJECT) {
      if (instance == null || myField.getValue() == null) {
        myValueType = ClassObject.ValueType.NULL;
        myShallowSize = 0;
        myRetainedSize = 0;
        myDepth = Integer.MAX_VALUE;
      }
      else {
        assert myField.getValue() == instance;
        if (instance instanceof ClassObj) {
          myValueType = ClassObject.ValueType.CLASS;
        }
        else if (instance instanceof ClassInstance && instance.getClassObj().getClassName().equals(ClassObject.JAVA_LANG_STRING)) {
          myValueType = ClassObject.ValueType.STRING;
        }
        else {
          myValueType = ClassObject.ValueType.OBJECT;
        }

        myShallowSize = instance.getSize();
        myRetainedSize = instance.getTotalRetainedSize();
        myDepth = instance.getDistanceToGcRoot();
      }
    }
    else {
      myValueType = ourPrimitiveValueTypeMap.getOrDefault(type, ClassObject.ValueType.NULL);
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
  public boolean getIsPrimitive() {
    return myValueType.getIsPrimitive();
  }
}

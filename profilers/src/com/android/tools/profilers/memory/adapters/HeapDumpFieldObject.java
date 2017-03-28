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
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A UI representation of a {@link Field}.
 */
public class HeapDumpFieldObject extends FieldObject {
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

  @NotNull private final ClassInstance.FieldValue myField;
  private final int myShallowSize;
  private final long myRetainedSize;
  private final ValueType myValueType;

  public HeapDumpFieldObject(@NotNull ClassInstance.FieldValue field) {
    myField = field;
    Type type = myField.getField().getType();
    if (type == Type.OBJECT) {
      if (myField.getValue() == null) {
        myValueType = ValueType.UNKNOWN; // TODO fix this by using the parent instance's information
        myShallowSize = 0;
        myRetainedSize = 0;
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
      }
    }
    else {
      myValueType = ourValueTypeMap.getOrDefault(type, ValueType.UNKNOWN);
      myShallowSize = type.getSize();
      myRetainedSize = type.getSize();
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
    // TODO fill this in using parent instance's information
    return 0;
  }

  @Nullable
  @Override
  public List<FieldObject> getFields() {
    // The field only has children if it is a non-primitive field.
    if (myField.getField().getType() == Type.OBJECT && myField.getValue() != null) {
      Instance instance = (Instance)myField.getValue();
      assert instance != null;
      return (new HeapDumpInstanceObject(instance)).getFields();
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
    return ArrayInstance.class.isAssignableFrom(myField.getValue().getClass());
  }
}

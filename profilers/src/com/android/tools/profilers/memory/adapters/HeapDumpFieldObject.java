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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.*;

final class HeapDumpFieldObject implements FieldObject {
  private static final Map<Type, ValueType> ourPrimitiveValueTypeMap = ImmutableMap.<Type, ValueObject.ValueType>builder()
    .put(Type.BOOLEAN, BOOLEAN)
    .put(Type.BYTE, BYTE)
    .put(Type.CHAR, CHAR)
    .put(Type.SHORT, SHORT)
    .put(Type.INT, INT)
    .put(Type.LONG, LONG)
    .put(Type.FLOAT, FLOAT)
    .put(Type.DOUBLE, DOUBLE)
    .build();

  @NotNull private final FieldValue myField;
  @NotNull private final ValueObject.ValueType myValueType;
  @Nullable private final InstanceObject myInstanceObject;
  private final int myDepth;
  private final long myNativeSize;
  private final int myShallowSize;
  private final long myRetainedSize;

  private final int myHashCode;

  public HeapDumpFieldObject(@NotNull HeapDumpCaptureObject captureObject, @NotNull Instance parentInstance, @NotNull FieldValue field) {
    myField = field;
    Type type = myField.getField().getType();
    if (type == Type.OBJECT) {
      Instance instance = (Instance)myField.getValue();
      if (instance == null || myField.getValue() == null) {
        myValueType = NULL;
        myInstanceObject = null;
        myNativeSize = 0;
        myShallowSize = 0;
        myRetainedSize = 0;
        myDepth = Integer.MAX_VALUE;
      }
      else {
        myInstanceObject = captureObject.findInstanceObject(instance);
        if (instance instanceof ClassObj) {
          myValueType = CLASS;
        }
        else if (instance instanceof ArrayInstance) {
          myValueType = ARRAY;
        }
        else if (instance instanceof ClassInstance && instance.getClassObj().getClassName().equals(ClassDb.JAVA_LANG_STRING)) {
          myValueType = STRING;
        }
        else {
          myValueType = OBJECT;
        }

        myNativeSize = instance.getNativeSize();
        myShallowSize = instance.getSize();
        myRetainedSize = instance.getTotalRetainedSize();
        myDepth = instance.getDistanceToGcRoot();
      }
    }
    else {
      myValueType = ourPrimitiveValueTypeMap.getOrDefault(type, NULL);
      myInstanceObject = null;
      myNativeSize = 0;
      myShallowSize = type.getSize();
      myRetainedSize = type.getSize();
      myDepth = parentInstance.getDistanceToGcRoot();
    }

    myHashCode = Arrays.hashCode(new Object[]{myInstanceObject, getFieldName(), getValueType(), myField.getValue()});
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HeapDumpFieldObject)) {
      return false;
    }

    HeapDumpFieldObject other = (HeapDumpFieldObject)obj;
    return other.myInstanceObject == myInstanceObject &&
           getFieldName().equals(other.getFieldName()) &&
           getValueType() == other.getValueType() &&
           (getAsInstance() == other.getAsInstance() || Objects.equals(myField.getValue(), other.myField.getValue()));
  }

  @NotNull
  @Override
  public String getName() {
    return getFieldName();
  }

  @Override
  public long getNativeSize() {
    return myNativeSize;
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

  @Nullable
  @Override
  public InstanceObject getAsInstance() {
    return myInstanceObject;
  }

  @Nullable
  @Override
  public Object getValue() {
    return myInstanceObject != null ? myInstanceObject : myField.getValue();
  }

  @NotNull
  @Override
  public ValueObject.ValueType getValueType() {
    return myValueType;
  }

  @NotNull
  @Override
  public String getValueText() {
    if (getValueType().getIsPrimitive()) {
      return "";
    }
    else if (getValueType() == NULL || myField.getValue() == null || myInstanceObject == null) {
      return "null";
    }
    else {
      return String.format("{%s}", myInstanceObject.getClassEntry().getSimpleClassName());
    }
  }

  @NotNull
  @Override
  public String getToStringText() {
    if (getValueType() == NULL || myField.getValue() == null) {
      return "";
    }
    else if (getValueType().getIsPrimitive()) {
      return myField.getValue().toString();
    }
    else {
      return myInstanceObject == null ? "" : myInstanceObject.getToStringText();
    }
  }
}

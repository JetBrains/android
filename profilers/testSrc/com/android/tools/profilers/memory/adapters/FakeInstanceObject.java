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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.android.tools.profilers.stacktrace.ThreadId;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FakeInstanceObject implements InstanceObject {
  @NotNull private final String myName;
  @NotNull private final ClassDb.ClassEntry myClassEntry;
  @NotNull private final List<FieldObject> myFields;
  @NotNull private final List<ReferenceObject> myReferences = new ArrayList<>();
  @NotNull private final ThreadId myAllocationThreadId;
  @Nullable private final AllocationStack myAllocationStack;
  @NotNull private final ValueType myValueType;
  @Nullable private final ValueType myArrayElementType;
  @Nullable private final Object myArray;
  private final int myArrayLength;
  private final int myHeapId;
  private final int myDepth;
  private final long myNativeSize;
  private final int myShallowSize;
  private final long myRetainedSize;

  private FakeInstanceObject(@NotNull String name,
                             @NotNull ClassDb.ClassEntry classEntry,
                             @NotNull List<FakeFieldObject> fields,
                             @NotNull ThreadId allocationThreadId,
                             @Nullable AllocationStack allocationStack,
                             @NotNull ValueType valueType,
                             @Nullable ValueType arrayElementType,
                             @Nullable Object array,
                             int arrayLength,
                             int heapId, int depth, long nativeSize, int shallowSize, long retainedSize) {
    myName = name;
    myClassEntry = classEntry;
    myDepth = depth;
    myNativeSize = nativeSize;
    myShallowSize = shallowSize;
    myRetainedSize = retainedSize;
    myFields = new ArrayList<>(fields);
    myAllocationThreadId = allocationThreadId;
    myAllocationStack = allocationStack;
    myValueType = valueType;
    myArrayElementType = arrayElementType;
    myArray = array;
    myArrayLength = arrayLength;
    myHeapId = heapId;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public int getHeapId() {
    return myHeapId;
  }

  @Override
  @NotNull
  public ThreadId getAllocationThreadId() {
    return myAllocationThreadId;
  }

  @Nullable
  public AllocationStack getAllocationStack() {
    return myAllocationStack;
  }

  @Override
  @Nullable
  public AllocationStack getAllocationCallStack() {
    return myAllocationStack;
  }

  @NotNull
  @Override
  public ClassDb.ClassEntry getClassEntry() {
    return myClassEntry;
  }

  @Override
  public int getFieldCount() {
    return myFields.size();
  }

  @Override
  @NotNull
  public List<FieldObject> getFields() {
    return myFields;
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return myValueType;
  }

  @Nullable
  @Override
  public ArrayObject getArrayObject() {
    if (myArray == null || myArrayElementType == null) {
      return null;
    }

    assertTrue(getValueType() == ValueType.ARRAY);
    return new ArrayObject() {
      @NotNull
      @Override
      public ValueType getArrayElementType() {
        return myArrayElementType;
      }

      @Nullable
      @Override
      public byte[] getAsByteArray() {
        if (myArrayElementType == ValueType.BYTE) {
          assertTrue(myArray instanceof byte[]);
          return (byte[])myArray;
        }
        return null;
      }

      @Nullable
      @Override
      public char[] getAsCharArray() {
        if (myArrayElementType == ValueType.CHAR) {
          assertTrue(myArray instanceof char[]);
          return (char[])myArray;
        }
        return null;
      }

      @Nullable
      @Override
      public Object[] getAsArray() {
        if (myArrayElementType.getIsPrimitive()) {
          return null;
        }

        return (Object[])myArray;
      }

      @Override
      public int getArrayLength() {
        return myArrayLength;
      }
    };
  }

  @Override
  public int getDepth() {
    return myDepth;
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

  @NotNull
  @Override
  public List<ReferenceObject> getReferences() {
    return myReferences;
  }

  // This fake doesn't handle circular references or multiple fields referring to the same object.
  @NotNull
  public FakeInstanceObject setFieldValue(@NotNull String fieldName, @NotNull ValueType fieldType, @Nullable Object fieldValue) {
    FakeFieldObject fieldObject = (FakeFieldObject)myFields.stream().filter(field -> field.getName().equals(fieldName)).findFirst()
      .orElseThrow(() -> new RuntimeException("Nonexistent field name"));
    fieldObject.setFieldValue(fieldType, fieldValue);

    if (fieldType == ValueType.OBJECT || fieldType == ValueType.ARRAY || fieldType == ValueType.CLASS || fieldType == ValueType.STRING) {
      assertNotNull(fieldValue);
      ((FakeInstanceObject)fieldValue).addReference(new ReferenceObject(Collections.singletonList(fieldName), this));
    }
    return this;
  }

  private void addReference(@NotNull ReferenceObject reference) {
    assertFalse(myReferences.contains(reference));
    myReferences.add(reference);
  }

  public static class Builder {
    @NotNull private FakeCaptureObject myCaptureObject;
    @NotNull private String myName = "SAMPLE_INSTANCE";
    @NotNull private List<FakeFieldObject> myFields = new ArrayList<>();
    @NotNull private ThreadId myAllocationThreadId = ThreadId.INVALID_THREAD_ID;
    @Nullable private AllocationStack myAllocationStack = null;
    @NotNull private ValueType myValueType = ValueType.NULL;
    @NotNull private String myClassName;
    private long myClassId;
    private long mySuperClassId = ClassDb.INVALID_CLASS_ID;
    private int myHeapId = FakeCaptureObject.DEFAULT_HEAP_ID;

    private int myDepth = Integer.MAX_VALUE;
    private long myNativeSize = INVALID_VALUE;
    private int myShallowSize = INVALID_VALUE;
    private long myRetainedSize = INVALID_VALUE;
    private ValueType myArrayElementType;
    private Object myArray;
    private int myArrayLength;

    public Builder(@NotNull FakeCaptureObject captureObject, long classId, @NotNull String className) {
      myCaptureObject = captureObject;
      myClassId = classId;
      myClassName = className;
    }

    @NotNull
    public Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    public Builder setSuperClassId(long superClassId) {
      mySuperClassId = superClassId;
      return this;
    }

    @NotNull
    public Builder createFakeFields(int fieldCount) {
      assertEquals(0, myFields.size());
      for (int i = 0; i < fieldCount; i++) {
        myFields.add(new FakeFieldObject("mField" + i, ValueType.NULL, null));
      }
      return this;
    }

    @NotNull
    public Builder setFields(@NotNull List<String> fields) {
      myFields.clear();
      myFields.addAll(ContainerUtil.map(fields, name -> new FakeFieldObject(name, ValueType.NULL, null)));
      return this;
    }

    public Builder addField(@NotNull String fieldName, ValueType fieldType, Object value) {
      myFields.add(new FakeFieldObject(fieldName, fieldType, value));
      return this;
    }

    @NotNull
    public Builder setAllocationThreadId(@NotNull ThreadId allocationThreadId) {
      myAllocationThreadId = allocationThreadId;
      return this;
    }

    @NotNull
    public Builder setAllocationStack(@Nullable AllocationStack allocationStack) {
      myAllocationStack = allocationStack;
      return this;
    }

    @NotNull
    public Builder setValueType(@NotNull ValueType valueType) {
      myValueType = valueType;
      return this;
    }

    @NotNull
    public Builder setArray(@NotNull ValueType elementType, @NotNull Object array, int arrayLength) {
      assertTrue(array.getClass().isArray());
      myArrayElementType = elementType;
      myArray = array;
      myArrayLength = arrayLength;
      return this;
    }

    @NotNull
    public Builder setHeapId(int heapId) {
      myHeapId = heapId;
      return this;
    }

    @NotNull
    public Builder setDepth(int depth) {
      myDepth = depth;
      return this;
    }

    @NotNull
    public Builder setNativeSize(long nativeSize) {
      myNativeSize = nativeSize;
      return this;
    }

    @NotNull
    public Builder setShallowSize(int shallowSize) {
      myShallowSize = shallowSize;
      return this;
    }

    @NotNull
    public Builder setRetainedSize(long retainedSize) {
      myRetainedSize = retainedSize;
      return this;
    }

    @NotNull
    public FakeInstanceObject build() {
      return new FakeInstanceObject(myName, myCaptureObject.registerClass(myClassId, mySuperClassId, myClassName), myFields,
                                    myAllocationThreadId, myAllocationStack, myValueType, myArrayElementType, myArray, myArrayLength,
                                    myHeapId, myDepth, myNativeSize, myShallowSize, myRetainedSize);
    }
  }
}

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
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A UI representation of a {@link ClassInstance}.
 */
class HeapDumpInstanceObject implements InstanceObject {
  public static final String NAME_FORMATTER = "@%d (0x%x)";
  private static final Comparator<Instance> DEPTH_COMPARATOR = Comparator.comparingInt(Instance::getDistanceToGcRoot);

  @Nullable private final ClassObject myClassObject;
  @Nullable private final Instance myInstance;
  @NotNull private final String myMemoizedLabel;

  @NotNull
  static List<FieldObject> extractFields(@NotNull Instance parentInstance) {
    List<FieldObject> fields = new ArrayList<>();
    if (parentInstance instanceof ClassInstance) {
      ClassInstance classInstance = (ClassInstance)parentInstance;
      for (FieldValue field : classInstance.getValues()) {
        Instance instance = field.getValue() instanceof Instance ? (Instance)field.getValue() : null;
        fields.add(new HeapDumpFieldObject(parentInstance, field, instance));
      }
    }
    else if (parentInstance instanceof ArrayInstance) {
      ArrayInstance arrayInstance = (ArrayInstance)parentInstance;
      Type arrayType = arrayInstance.getArrayType();
      int arrayIndex = 0;
      for (Object value : arrayInstance.getValues()) {
        FieldValue field = new FieldValue(new Field(arrayType, Integer.toString(arrayIndex)), value);
        Instance instance = field.getValue() instanceof Instance ? (Instance)field.getValue() : null;
        fields.add(new HeapDumpFieldObject(parentInstance, field, instance));
        arrayIndex++;
      }
    }
    else if (parentInstance instanceof ClassObj) {
      ClassObj classObj = (ClassObj)parentInstance;
      for (Map.Entry<Field, Object> entry : classObj.getStaticFieldValues().entrySet()) {
        FieldValue field = new FieldValue(entry.getKey(), entry.getValue());
        Instance instance = entry.getValue() instanceof Instance ? (Instance)field.getValue() : null;
        fields.add(new HeapDumpFieldObject(parentInstance, field, instance));
      }
    }

    return fields;
  }

  static List<ReferenceObject> extractReferences(@NotNull Instance instance) {
    // Sort hard referrers to appear first.
    List<Instance> sortedReferences = new ArrayList<>(instance.getHardReverseReferences());
    sortedReferences.sort(DEPTH_COMPARATOR);

    // Sort soft referrers to appear second.
    if (instance.getSoftReverseReferences() != null) {
      List<Instance> sortedSoftReferences = new ArrayList<>(instance.getSoftReverseReferences());
      sortedSoftReferences.sort(DEPTH_COMPARATOR);
      sortedReferences.addAll(sortedSoftReferences);
    }

    List<ReferenceObject> referrers = new ArrayList<>(sortedReferences.size());

    // Determine the type of references
    for (Instance reference : sortedReferences) {
      List<String> referencingFieldNames = new ArrayList<>(3);
      if (reference instanceof ClassInstance) {
        ClassInstance classInstance = (ClassInstance)reference;
        for (ClassInstance.FieldValue entry : classInstance.getValues()) {
          // This instance is referenced by a field of the referrer class
          if (entry.getField().getType() == Type.OBJECT && entry.getValue() == instance) {
            referencingFieldNames.add(entry.getField().getName());
          }
        }
      }
      else if (reference instanceof ArrayInstance) {
        ArrayInstance arrayInstance = (ArrayInstance)reference;
        assert arrayInstance.getArrayType() == Type.OBJECT;
        Object[] values = arrayInstance.getValues();
        for (int i = 0; i < values.length; ++i) {
          // This instance is referenced by an array
          if (values[i] == instance) {
            referencingFieldNames.add(String.valueOf(i));
          }
        }
      }
      else if (reference instanceof ClassObj) {
        ClassObj classObj = (ClassObj)reference;
        Map<Field, Object> staticValues = classObj.getStaticFieldValues();
        for (Map.Entry<Field, Object> entry : staticValues.entrySet()) {
          // This instance is referenced by a static field of a Class object.
          if (entry.getKey().getType() == Type.OBJECT && entry.getValue() == instance) {
            referencingFieldNames.add(entry.getKey().getName());
          }
        }
      }

      referrers.add(new HeapDumpReferenceObject(
        // TODO - is the ClassObj logic correct here? Should a ClassObj instance not have the "java.lang.Class" as its ClassObj?
        new HeapDumpClassObject(reference instanceof ClassObj ? (ClassObj)reference : reference.getClassObj()), reference,
        referencingFieldNames));
    }

    return referrers;
  }

  public HeapDumpInstanceObject(@Nullable ClassObject classObject, @Nullable Instance instance) {
    myClassObject = classObject;
    myInstance = instance;
    myMemoizedLabel = myInstance == null ? "{null}" : String.format(NAME_FORMATTER, myInstance.getUniqueId(), myInstance.getUniqueId());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HeapDumpInstanceObject)) {
      return false;
    }

    HeapDumpInstanceObject otherInstance = (HeapDumpInstanceObject)obj;
    return myInstance == otherInstance.myInstance;
  }

  @Override
  public int hashCode() {
    return myInstance == null ? super.hashCode() : myInstance.hashCode();
  }

  @NotNull
  @Override
  public String getDisplayLabel() {
    // TODO show length of array instance
    return myMemoizedLabel;
  }

  @Nullable
  @Override
  public ClassObject getClassObject() {
    return myClassObject;
  }

  @Nullable
  @Override
  public String getClassName() {
    return myClassObject == null ? null : myClassObject.getName();
  }

  @Override
  public int getDepth() {
    return myInstance == null ? MemoryObject.INVALID_VALUE : myInstance.getDistanceToGcRoot();
  }

  @Override
  public int getShallowSize() {
    return myInstance == null ? MemoryObject.INVALID_VALUE : myInstance.getSize();
  }

  @Override
  public long getRetainedSize() {
    return myInstance == null ? MemoryObject.INVALID_VALUE : myInstance.getTotalRetainedSize();
  }

  @NotNull
  @Override
  public List<FieldObject> getFields() {
    return myInstance == null ? Collections.emptyList() : extractFields(myInstance);
  }

  @Nullable
  @Override
  public AllocationStack getCallStack() {
    if (myInstance == null) {
      return null;
    }

    AllocationStack.Builder builder = AllocationStack.newBuilder();
    for (StackFrame stackFrame : myInstance.getStack().getFrames()) {
      String fileName = stackFrame.getFilename();
      String guessedClassName = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - ".java".length()) : fileName;
      builder.addStackFrames(
        AllocationStack.StackFrame.newBuilder().setClassName(guessedClassName).setMethodName(stackFrame.getMethodName())
          .setLineNumber(stackFrame.getLineNumber()).setFileName(fileName).build());
    }
    return builder.build();
  }

  @Override
  public boolean getIsArray() {
    return myInstance instanceof ArrayInstance;
  }

  @Override
  public boolean getIsRoot() {
    return myInstance instanceof RootObj;
  }

  @NotNull
  @Override
  public List<ReferenceObject> getReferences() {
    return getIsRoot() ? Collections.EMPTY_LIST : (myInstance == null ? Collections.emptyList() : extractReferences(myInstance));
  }

  @NotNull
  @Override
  public List<InstanceAttribute> getReferenceAttributes() {
    return Arrays
      .asList(InstanceObject.InstanceAttribute.LABEL, InstanceObject.InstanceAttribute.DEPTH, InstanceObject.InstanceAttribute.SHALLOW_SIZE,
              InstanceObject.InstanceAttribute.RETAINED_SIZE);
  }
}

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
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A UI representation of a {@link ClassInstance}.
 */
public class HeapDumpInstanceObject extends InstanceObject {
  @NotNull private final Instance myInstance;

  public HeapDumpInstanceObject(@NotNull Instance instance) {
    myInstance = instance;
  }

  @NotNull
  @Override
  public String getName() {
    // TODO show length of array instance
    return String.format("@%d (0x%x)", myInstance.getUniqueId(), myInstance.getUniqueId());
  }

  @Override
  public int getDepth() {
    return myInstance.getDistanceToGcRoot();
  }

  @Override
  public int getShallowSize() {
    return myInstance.getSize();
  }

  @Override
  public long getRetainedSize() {
    return myInstance.getTotalRetainedSize();
  }

  @Nullable
  @Override
  public List<FieldObject> getFields() {
    List<FieldObject> sublist = new ArrayList<>();
    if (myInstance instanceof ClassInstance) {
      ClassInstance classInstance = (ClassInstance)myInstance;
      for (ClassInstance.FieldValue field : classInstance.getValues()) {
        sublist.add(new HeapDumpFieldObject(field));
      }
    }
    else if (myInstance instanceof ArrayInstance) {
      ArrayInstance arrayInstance = (ArrayInstance)myInstance;
      Type arrayType = arrayInstance.getArrayType();
      int arrayIndex = 0;
      for (Object value : arrayInstance.getValues()) {
        sublist.add(new HeapDumpFieldObject(new ClassInstance.FieldValue(new Field(arrayType, Integer.toString(arrayIndex)), value)));
        arrayIndex++;
      }
    }

    return sublist;
  }

  @Nullable
  @Override
  public AllocationStack getCallStack() {
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
}

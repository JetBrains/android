/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.descriptors;

import com.android.tools.idea.editors.hprof.jdi.ArrayReferenceImpl;
import com.android.tools.idea.editors.hprof.jdi.ObjectReferenceImpl;
import com.android.tools.idea.editors.hprof.jdi.StringReferenceImpl;
import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Instance;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class InstanceFieldDescriptorImpl extends HprofFieldDescriptorImpl {
  @NotNull private ObjectReferenceImpl myObjectReference;

  public InstanceFieldDescriptorImpl(@NotNull Project project, @NotNull Field field, @Nullable Instance instance, int memoryOrdering) {
    super(project, field, instance, memoryOrdering);
    myObjectReference = initObjectReference();
  }

  @Nullable
  public Instance getInstance() {
    return (Instance)myValueData;
  }

  @Override
  public boolean isString() {
    return myValueData != null &&
           ((Instance)myValueData).getClassObj() != null &&
           "java.lang.String".equals(((Instance)myValueData).getClassObj().getClassName());
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isExpandable() {
    return !isNull();
  }

  @Override
  public Value getValue() {
    return calcValue(null);
  }

  @Override
  @NotNull
  public String getIdLabel() {
    if (myValueData == null) {
      return "";
    }

    return ValueDescriptorImpl.getIdLabel(myObjectReference);
  }

  @NotNull
  @Override
  public String getValueText() {
    if (myValueData == null) {
      return "null";
    }
    else if (!isString()) {
      return "";
    }
    else {
      ArrayInstance charBufferArray = null;
      assert (myValueData instanceof ClassInstance);
      ClassInstance classInstance = (ClassInstance)myValueData;
      for (Map.Entry<Field, Object> entry : classInstance.getValues().entrySet()) {
        if ("value".equals(entry.getKey().getName())) {
          charBufferArray = (ArrayInstance)entry.getValue();
        }
      }
      assert (charBufferArray != null);
      StringBuilder builder = new StringBuilder(charBufferArray.getValues().length + 3);
      builder.append(" \"");
      for (Object o : charBufferArray.getValues()) {
        assert (o instanceof Character);
        builder.append(o);
      }
      builder.append("\"");
      return builder.toString();
    }
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) {
    return myObjectReference;
  }

  private ObjectReferenceImpl initObjectReference() {
    if (isString()) {
      return new StringReferenceImpl(myField, (Instance)myValueData);
    }
    else if (isArray()) {
      //noinspection ConstantConditions
      return new ArrayReferenceImpl(myField, (ArrayInstance)myValueData);
    }
    else {
      // TODO: Implement Class objects.
      return new ObjectReferenceImpl(myField, (Instance)myValueData);
    }
  }
}

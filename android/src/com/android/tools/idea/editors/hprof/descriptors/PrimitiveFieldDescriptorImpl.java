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

import com.android.tools.idea.editors.hprof.jdi.*;
import com.android.tools.perflib.heap.Field;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public class PrimitiveFieldDescriptorImpl extends HprofFieldDescriptorImpl {
  @NotNull private PrimitiveValueImpl myPrimitiveValue;

  public PrimitiveFieldDescriptorImpl(@NotNull Project project, @NotNull Field field, @NotNull final Object value, int memoryOrdering) {
    super(project, field, value, memoryOrdering);
    myPrimitiveValue = (PrimitiveValueImpl)calcValue(null);
  }

  @Override
  public boolean isString() {
    return false;
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public boolean isExpandable() {
    return false;
  }

  @Override
  public Value getValue() {
    return myPrimitiveValue;
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) {
    assert (myValueData != null);
    switch (myField.getType()) {
      case BOOLEAN:
        return new BooleanValueImpl(myField, myValueData);
      case BYTE:
        return new ByteValueImpl(myField, myValueData);
      case CHAR:
        return new CharValueImpl(myField, myValueData);
      case SHORT:
        return new ShortValueImpl(myField, myValueData);
      case INT:
        return new IntegerValueImpl(myField, myValueData);
      case LONG:
        return new LongValueImpl(myField, myValueData);
      case FLOAT:
        return new FloatValueImpl(myField, myValueData);
      case DOUBLE:
        return new DoubleValueImpl(myField, myValueData);
      default:
        throw new RuntimeException("Invalid type passed ot PrimitiveDescriptorImpl");
    }
  }
}

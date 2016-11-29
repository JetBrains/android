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
package com.android.tools.profilers.memory;

import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Type;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class FieldNode implements MemoryNode {
  @NotNull private final ClassInstance.FieldValue myField;

  public FieldNode(@NotNull ClassInstance.FieldValue field) {
    myField = field;
  }

  @NotNull
  public ClassInstance.FieldValue getFieldValue() {
    return myField;
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

  @NotNull
  @Override
  public List<MemoryNode> getSubList() {
    // The field only has children if it is a non-primitive field.
    if (myField.getField().getType() == Type.OBJECT && myField.getValue() != null) {
      Instance instance = (Instance)myField.getValue();
      assert instance != null;
      return (new InstanceNode(instance)).getSubList();
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Capability> getCapabilities() {
    return Collections.emptyList();
  }
}

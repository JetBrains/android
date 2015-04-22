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
package com.android.tools.idea.editors.hprof.tables;

import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

public class HprofTreeNode extends DefaultMutableTreeNode implements HprofInstanceNode {
  private static class Data {
    @NotNull private Field myField;
    @Nullable private Object myValue;

    private Data(@NotNull Field field, @Nullable Object value) {
      myField = field;
      myValue = value;
    }
  }

  public HprofTreeNode(@NotNull Object value, @NotNull Field field) {
    super(new Data(field, value));
  }

  @Override
  public boolean isPrimitive() {
    return getField().getType() != Type.OBJECT;
  }

  @Nullable
  @Override
  public Instance getInstance() {
    return getData().myValue instanceof Instance ? (Instance)getData().myValue : null;
  }

  @NotNull
  @Override
  public Object getValue() {
    return getData().myValue;
  }

  @NotNull
  @Override
  public Field getField() {
    return getData().myField;
  }

  @NotNull
  private Data getData() {
    return (Data)getUserObject();
  }
}

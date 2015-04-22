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
package com.android.tools.idea.editors.hprof.tables.instancestable;


import com.android.tools.idea.editors.hprof.tables.HprofInstanceNode;
import com.android.tools.perflib.heap.*;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class InstanceDetailTreeNode extends TreeBuilderNode implements HprofInstanceNode {
  protected static class Data {
    @NotNull private InstanceDetailModel myModel;
    @NotNull private Field myField;
    @NotNull Object myValue;

    protected Data(@NotNull InstanceDetailModel model, @NotNull Field field, @NotNull Object value) {
      myModel = model;
      myField = field;
      myValue = value;
    }
  }

  public InstanceDetailTreeNode(@NotNull InstanceDetailModel model, @NotNull Field field, @NotNull Object value) {
    super(new Data(model, field, value));
  }

  @NotNull
  @Override
  public Field getField() {
    return getData().myField;
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

  @Override
  public boolean isPrimitive() {
    return getField().getType() != Type.OBJECT;
  }

  @Nullable
  public Long getRetainedSize(int heapIndex) {
    Instance instance = getInstance();
    return instance != null ? instance.getRetainedSize(heapIndex) : null;
  }

  @Override
  protected TreeBuilder getTreeBuilder() {
    return getData().myModel.getTreeBuilder();
  }

  protected boolean isExpandable() {
    Instance instance = getInstance();
    if (instance == null) {
      return false;
    }

    if (instance instanceof ClassInstance) {
      return instance.getClassObj().getAllFieldsCount() > 0;
    }
    else if (instance instanceof ClassObj) {
      return true;
    }
    else {
      return instance.getSize() > 0;
    }
  }

  @NotNull
  private Data getData() {
    return (Data)getUserObject();
  }

  protected void buildChildren(@NotNull InstanceDetailModel model) {
    ClassInstance instance = (ClassInstance)getInstance();
    assert (instance != null && instance.getValues().size() > 0);
    int childrenCount = instance.getValues().size();
    InstanceDetailTreeNode[] children = new InstanceDetailTreeNode[childrenCount];
    int[] childIndices = new int[childrenCount];

    if (childrenCount > 0) {
      int currentChildIndex = 0;
      for (Map.Entry<Field, Object> entry : instance.getValues().entrySet()) {
        children[currentChildIndex] = new InstanceDetailTreeNode(model, entry.getKey(), entry.getValue() == null ? "Invalid value" : entry.getValue());
        childIndices[currentChildIndex] = currentChildIndex;
        add(children[currentChildIndex]);
        ++currentChildIndex;
      }
    }

    model.getTreeBuilder().nodeStructureChanged(this);
    model.fireTreeNodesInserted(this, model.getPathToRoot(this), childIndices, children);
  }
}

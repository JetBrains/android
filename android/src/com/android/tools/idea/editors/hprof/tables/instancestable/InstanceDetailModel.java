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

import com.android.tools.idea.editors.hprof.tables.HprofColumnInfo;
import com.android.tools.idea.editors.hprof.tables.HprofTreeTableModel;
import com.android.tools.perflib.heap.*;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class InstanceDetailModel extends HprofTreeTableModel {
  @NotNull private InstanceDetailTreeBuilder myTreeBuilder;
  @NotNull private Heap myHeap;

  protected final HprofColumnInfo<InstanceDetailTreeNode, String> myInstanceData =
    new HprofColumnInfo<InstanceDetailTreeNode, String>("Instance Data", String.class, SwingConstants.LEFT, 200, true) {
      @Override
      @Nullable
      public String valueOf(@NotNull InstanceDetailTreeNode node) {
        return node.getField().getName();
      }

      @Override
      @NotNull
      public Class<?> getColumnClass() {
        return String.class;
      }
    };

  protected final HprofColumnInfo<InstanceDetailTreeNode, Long> myRetainedSizeInfo =
    new HprofColumnInfo<InstanceDetailTreeNode, Long>("Retained Size", Long.class, SwingConstants.RIGHT, 100, false) {
      @Nullable
      @Override
      public Long valueOf(@NotNull InstanceDetailTreeNode node) {
        return node.getRetainedSize(mySnapshot.getHeapIndex(myHeap));
      }

      @Override
      @NotNull
      public Class<?> getColumnClass() {
        return Long.class;
      }
    };

  public InstanceDetailModel(@NotNull Snapshot snapshot, @NotNull Heap heap, @NotNull Instance root, boolean allColumnsEnabled) {
    super(snapshot, null, null);
    myHeap = heap;
    setRoot(new InstanceDetailTreeNode(this, new Field(Type.OBJECT, "HiddenRootNode"), root));
    setColumns(createColumnInfo());
    if (allColumnsEnabled) {
      enableAllColumns();
    }
    myTreeBuilder = new InstanceDetailTreeBuilder(this);
  }

  @Override
  protected HprofColumnInfo getColumn(int column) {
    return (HprofColumnInfo)getColumns()[column];
  }

  @NotNull
  protected ColumnInfo[] createColumnInfo() {
    return new ColumnInfo[]{myInstanceData, HprofColumnInfo.getInstanceSizeInfo(), myRetainedSizeInfo};
  }

  @NotNull
  protected InstanceDetailTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  /**
   * This method builds the child nodes under the given parent and attaches the child nodes to the given parent node.
   */
  protected void buildNode(@NotNull InstanceDetailTreeNode parent) {
    parent.buildChildren(this);
  }

  @Override
  protected void fireTreeNodesInserted(Object source, Object[] path, int[] childIndices, Object[] children) {
    super.fireTreeNodesInserted(source, path, childIndices, children);
  }

  /*
   * This inner class serves as the tree builder adapter for the DebugTreeRenderer infrastructure.
   */
  protected static class InstanceDetailTreeBuilder extends TreeBuilder {
    protected InstanceDetailTreeBuilder(@NotNull Object userObject) {
      super(userObject);
    }

    @Override
    public void buildChildren(@NotNull TreeBuilderNode node) {
      ((InstanceDetailModel)getUserObject()).buildNode((InstanceDetailTreeNode)node);
    }

    @Override
    public boolean isExpandable(@NotNull TreeBuilderNode node) {
      return ((InstanceDetailTreeNode)node).isExpandable();
    }
  }
}

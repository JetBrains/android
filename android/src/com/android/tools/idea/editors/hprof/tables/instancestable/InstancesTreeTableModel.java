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
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class InstancesTreeTableModel extends HprofTreeTableModel {
  @NotNull private Heap myHeap;
  protected final HprofColumnInfo<DefaultMutableTreeNode, Long> myRetainedSizeInfo =
    new HprofColumnInfo<DefaultMutableTreeNode, Long>("Retained Size", Long.class, SwingConstants.RIGHT, 100, false) {
      @Nullable
      @Override
      public Long valueOf(DefaultMutableTreeNode node) {
        return HprofColumnInfo.getUserInstance(node).getRetainedSize(mySnapshot.getHeapIndex(myHeap));
      }

      @Override
      @NotNull
      public Class<?> getColumnClass() {
        return Long.class;
      }
    };
  @NotNull private Set<TreeModelListener> myTreeModelListeners;

  public InstancesTreeTableModel(@NotNull Snapshot snapshot,
                                 @NotNull Heap heap,
                                 @NotNull Collection<Instance> entries,
                                 boolean allColumnsEnabled) {
    super(snapshot, new DefaultMutableTreeNode(), null);
    setColumns(createColumnInfo());
    setRoot(new DefaultMutableTreeNode());
    myTreeModelListeners = new HashSet<TreeModelListener>();
    myHeap = heap;

    if (allColumnsEnabled) {
      enableAllColumns();
    }

    for (Instance instance : entries) {
      if (instance.getHeap() == myHeap) {
        getMutableRoot().add(new DefaultMutableTreeNode(instance));
      }
    }
  }

  @Override
  public void setTree(@Nullable JTree tree) {
    super.setTree(tree);
    if (tree != null) {
      tree.setRootVisible(false);
    }
  }

  @Override
  protected HprofColumnInfo getColumn(int column) {
    return (HprofColumnInfo)getColumns()[column];
  }

  @NotNull
  protected ColumnInfo[] createColumnInfo() {
    return new ColumnInfo[]{HprofColumnInfo.INSTANCE_ID_INFO, HprofColumnInfo.INSTANCE_SIZE_INFO, HprofColumnInfo.INSTANCE_DOMINATOR_INFO,
      myRetainedSizeInfo,};
  }

  public DefaultMutableTreeNode getMutableRoot() {
    return (DefaultMutableTreeNode)getRoot();
  }

  @Override
  public Object getChild(@NotNull Object node, int childIndex) {
    if (node == getMutableRoot()) {
      return getMutableRoot().getChildAt(childIndex);
    }
    else {
      return new DefaultMutableTreeNode(HprofColumnInfo.getUserInstance(node).getReferences().get(childIndex));
    }
  }

  @Override
  public int getChildCount(@NotNull Object node) {
    if (node == getMutableRoot()) {
      return getMutableRoot().getChildCount();
    }
    else {
      return HprofColumnInfo.getUserInstance(node).getReferences().size();
    }
  }

  @Override
  public boolean isLeaf(@NotNull Object node) {
    return getChildCount(node) == 0;
  }

  @Override
  public void valueForPathChanged(@NotNull TreePath treePath, @NotNull Object child) {

  }

  @Override
  public int getIndexOfChild(@NotNull Object parent, @NotNull Object child) {
    if (parent == getMutableRoot()) {
      return getMutableRoot().getIndex((DefaultMutableTreeNode)child);
    }
    else {
      return HprofColumnInfo.getUserInstance(parent).getReferences().indexOf(child);
    }
  }

  @Override
  public void addTreeModelListener(@NotNull TreeModelListener treeModelListener) {
    myTreeModelListeners.add(treeModelListener);
  }

  @Override
  public void removeTreeModelListener(@NotNull TreeModelListener treeModelListener) {
    myTreeModelListeners.remove(treeModelListener);
  }
}

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
import com.android.tools.idea.editors.hprof.tables.HprofTreeNode;
import com.android.tools.idea.editors.hprof.tables.HprofTreeTableModel;
import com.android.tools.perflib.heap.*;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class InstancesTreeTableModel extends HprofTreeTableModel {
  @NotNull private Heap myHeap;
  protected final HprofColumnInfo<HprofTreeNode, Long> myRetainedSizeInfo =
    new HprofColumnInfo<HprofTreeNode, Long>("Retained Size", Long.class, SwingConstants.RIGHT, 100, false) {
      @Nullable
      @Override
      public Long valueOf(@NotNull HprofTreeNode node) {
        Instance instance = node.getInstance();
        return instance == null ? null : instance.getRetainedSize(mySnapshot.getHeapIndex(myHeap));
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
    super(snapshot, null, null);
    setColumns(createColumnInfo());
    setRoot(new HprofTreeNode(new Integer(0), new Field(Type.OBJECT, "HiddenRootNode")));
    myTreeModelListeners = new HashSet<TreeModelListener>();
    myHeap = heap;

    if (allColumnsEnabled) {
      enableAllColumns();
    }

    for (Instance instance : entries) {
      if (instance.getHeap() == myHeap) {
        getMutableRoot().add(new HprofTreeNode(instance, new Field(Type.OBJECT, instance.getClassObj().getClassName())));
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
    return new ColumnInfo[]{HprofColumnInfo.getInstanceIdInfo(), HprofColumnInfo.getInstanceSizeInfo(),
      HprofColumnInfo.getInstanceDominatorInfo(), myRetainedSizeInfo};
  }

  public HprofTreeNode getMutableRoot() {
    return (HprofTreeNode)getRoot();
  }

  @Override
  public Object getChild(@NotNull Object node, int childIndex) {
    if (node == getMutableRoot()) {
      return getMutableRoot().getChildAt(childIndex);
    }
    else {
      HprofTreeNode hprofTreeNode = (HprofTreeNode)node;
      if (hprofTreeNode.isPrimitive()) {
        return null;
      }
      else {
        Instance instance = hprofTreeNode.getInstance();
        assert (instance != null);
        Instance parentInstance = instance.getReferences().get(childIndex);
        return new HprofTreeNode(parentInstance, new Field(Type.OBJECT, parentInstance.getClassObj().getClassName()));
      }
    }
  }

  @Override
  public int getChildCount(@NotNull Object node) {
    if (node == getMutableRoot()) {
      return getMutableRoot().getChildCount();
    }
    else {
      HprofTreeNode hprofTreeNode = (HprofTreeNode)node;
      if (hprofTreeNode.isPrimitive()) {
        return 0;
      }
      else {
        Instance instance = hprofTreeNode.getInstance();
        return instance == null ? 0 : instance.getReferences().size();
      }
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
  public void addTreeModelListener(@NotNull TreeModelListener treeModelListener) {
    myTreeModelListeners.add(treeModelListener);
  }

  @Override
  public void removeTreeModelListener(@NotNull TreeModelListener treeModelListener) {
    myTreeModelListeners.remove(treeModelListener);
  }
}

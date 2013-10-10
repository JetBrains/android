/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.vmtrace.treemodel;

import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import org.jdesktop.swingx.treetable.AbstractTreeTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

/**
 * A {@link TreeTableModel} for viewing method statistics from a VM Trace.
 * The root and nodes of the tree model are instances of {@link StatsNode}, so most methods here
 * simply delegate to the corresponding method in the {@link StatsNode}.
 */
public class VmStatsTreeTableModel extends AbstractTreeTableModel implements TreeTableModel {
  private VmTraceData myVmTraceData;
  private StatsNode myRootNode;
  private ThreadInfo myThread;
  private ClockType myClockType = ClockType.GLOBAL;

  private StatsTableColumn mySortByColumn = StatsTableColumn.EXCLUSIVE_TIME;
  private boolean mySortAscending = true;

  public VmStatsTreeTableModel() {
    myRootNode = new NullStatsNode();
  }

  public void setTraceData(@NotNull VmTraceData traceData, @NotNull ThreadInfo thread) {
    myVmTraceData = traceData;
    setThread(thread);
  }

  public void setClockType(ClockType type) {
    myClockType = type;
    fireTreeStructureChanged();
  }

  public void setThread(@NotNull ThreadInfo thread) {
    myThread = thread;
    if (myVmTraceData != null) {
      myRootNode = new StatsByThreadNode(myVmTraceData, thread);
    } else {
      myRootNode = new NullStatsNode();
    }
    fireTreeStructureChanged();
  }

  private void fireTreeStructureChanged() {
    TreeModelEvent e = new TreeModelEvent(this, new Object[] { myRootNode});
    for (TreeModelListener listener: getTreeModelListeners()) {
      listener.treeStructureChanged(e);
    }
  }

  @Override
  public int getColumnCount() {
    return StatsTableColumn.values().length;
  }

  @Override
  public String getColumnName(int column) {
    return getStatsTableColumn(column).toString();
  }

  private StatsTableColumn getStatsTableColumn(int index) {
    return StatsTableColumn.fromColumnIndex(index);
  }

  @Override
  public Class getColumnClass(int column) {
    return column == 0 ? TreeTableModel.class : String.class;
  }

  @Nullable
  @Override
  public Object getValueAt(Object node, int column) {
    if (!(node instanceof StatsNode)) {
      return "???";
    }

    return ((StatsNode)node).getValueAt(column, myThread, myVmTraceData, myClockType);
  }

  @Override
  public boolean isCellEditable(Object node, int column) {
    return false;
  }

  @Override
  public void setTree(JTree tree) {
  }

  @Override
  public Object getRoot() {
    return myRootNode;
  }

  @Override
  public Object getChild(Object parent, int index) {
    return ((StatsNode)parent).getChild(index);
  }

  @Override
  public int getChildCount(Object parent) {
    return ((StatsNode)parent).getChildCount();
  }

  @Override
  public boolean isLeaf(Object node) {
    return ((StatsNode)node).isLeaf();
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    return 0;
  }

  public void sortByColumn(StatsTableColumn column) {
    if (column != mySortByColumn) {
      mySortByColumn = column;
      mySortAscending = true;
    } else {
      mySortAscending = !mySortAscending;
    }
    myRootNode.setSortColumn(mySortByColumn, mySortAscending);
    fireTreeStructureChanged();
  }
}

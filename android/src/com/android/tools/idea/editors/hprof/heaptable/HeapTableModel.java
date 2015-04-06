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
package com.android.tools.idea.editors.hprof.heaptable;

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;

public class HeapTableModel extends AbstractTableModel {
  @NotNull private Heap myHeap;
  @NotNull private HeapTableColumn[] myColumns;
  @NotNull private ArrayList<ClassObj> myEntries;
  @NotNull private int[] myUiColumnToEnabledMap;
  private int myUiColumnMapSize;

  public HeapTableModel(@NotNull HeapTableColumn[] columns, @NotNull Heap heap) {
    myHeap = heap;
    myColumns = columns;
    myEntries = new ArrayList<ClassObj>(heap.getClasses());
    myUiColumnToEnabledMap = new int[myColumns.length];
    refreshUiColumnToEnabledMap();
  }

  @NotNull
  public ClassObj getEntry(int row) {
    return myEntries.get(row);
  }

  @Override
  public String getColumnName(int column) {
    return remapUiColumnIndexToColumn(column).getColumnName();
  }

  @Override
  public int getColumnCount() {
    return myUiColumnMapSize;
  }

  @Override
  public int getRowCount() {
    return myEntries.size();
  }

  @Override
  public Object getValueAt(int row, int column) {
    return remapUiColumnIndexToColumn(column).getValue(this, row);
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return remapUiColumnIndexToColumn(column).getColumnClass();
  }

  public int getColumnWidth(int column) {
    return remapUiColumnIndexToColumn(column).getColumnWidth();
  }

  public int getColumnHeaderJustification(int column) {
    return remapUiColumnIndexToColumn(column).getHeaderJustification();
  }

  @NotNull
  public String getHeapName() {
    return myHeap.getName();
  }

  @NotNull
  protected static HeapTableColumn[] createDefaultHeapTableColumns() {
    return new HeapTableColumn[]{new HeapTableColumn<ClassObj>("Class Name", ClassObj.class, SwingConstants.LEFT, 800, true) {
      @Override
      @NotNull
      public ClassObj getValue(@NotNull HeapTableModel model, int row) {
        return model.getEntry(row);
      }
    }, new HeapTableColumn<Integer>("Count", Integer.class, SwingConstants.RIGHT, 100, true) {
      @Override
      @NotNull
      public Integer getValue(@NotNull HeapTableModel model, int row) {
        return model.getEntry(row).getInstances().size();
      }
    }, new HeapTableColumn<Integer>("Sizeof", Integer.class, SwingConstants.RIGHT, 80, true) {
      @Override
      @NotNull
      public Integer getValue(@NotNull HeapTableModel model, int row) {
        return model.getEntry(row).getInstanceSize();
      }
    }, new HeapTableColumn<Long>("Retained Size", Long.class, SwingConstants.RIGHT, 120, false) {
      @Override
      @NotNull
      public Long getValue(@NotNull HeapTableModel model, int row) {
        long totalSize = 0;
        for (Instance i : model.getEntry(row).getInstances()) {
          totalSize += i.getTotalRetainedSize();
        }
        return totalSize;
      }
    }};
  }

  private void refreshUiColumnToEnabledMap() {
    if (myUiColumnToEnabledMap.length != myColumns.length) {
      myUiColumnToEnabledMap = new int[myColumns.length];
    }

    myUiColumnMapSize = 0;
    for (int i = 0; i < myColumns.length; ++i) {
      if (myColumns[i].getEnabled()) {
        myUiColumnToEnabledMap[myUiColumnMapSize] = i;
        ++myUiColumnMapSize;
      }
    }
  }

  @NotNull
  private HeapTableColumn remapUiColumnIndexToColumn(int uiIndex) {
    return myColumns[myUiColumnToEnabledMap[uiIndex]];
  }
}

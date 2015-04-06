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

import com.android.tools.idea.editors.hprof.tables.HprofTableModel;
import com.android.tools.idea.editors.hprof.tables.TableColumn;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InstancesTableModel extends HprofTableModel {
  @NotNull private Snapshot mySnapshot;
  @NotNull private List<Instance> myEntries;
  @Nullable private Heap myHeap;
  @NotNull private List<TableColumn<InstancesTableModel, ?>> myColumns;

  public InstancesTableModel(@NotNull Snapshot snapshot) {
    mySnapshot = snapshot;
    myColumns = createInstancesTableColumns();
    myEntries = new ArrayList<Instance>();
  }

  public void setInstances(@Nullable Heap heap, @Nullable Collection<Instance> entries) {
    myHeap = heap;
    myEntries.clear();
    if (entries != null) {
      for (Instance instance : entries) {
        if (instance.getHeap() == myHeap) {
          myEntries.add(instance);
        }
      }
    }
    fireTableDataChanged();
  }

  @NotNull
  public Instance getEntry(int row) {
    return myEntries.get(row);
  }

  @Override
  public void enableAllColumns() {
    for (TableColumn column : myColumns) {
      column.setEnabled(true);
    }
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    return myEntries.size();
  }

  @Override
  public int getColumnCount() {
    return myColumns.size();
  }

  @Override
  protected boolean isColumnEnabled(int unmappedIndex) {
    return myColumns.get(unmappedIndex).getEnabled();
  }

  @Override
  protected TableColumn getColumn(int uiIndex) {
    return myColumns.get(uiIndex);
  }

  @NotNull
  protected List<TableColumn<InstancesTableModel, ?>> createInstancesTableColumns() {
    List<TableColumn<InstancesTableModel, ?>> columns = new ArrayList<TableColumn<InstancesTableModel, ?>>();
    columns.add(new TableColumn<InstancesTableModel, String>("Instance ID", String.class, SwingConstants.LEFT, 200, true) {
      @Override
      @NotNull
      public String getValue(@NotNull InstancesTableModel model, int row) {
        return String.format("<0x%x16>", model.getEntry(row).getId());
      }
    });
    columns.add(new TableColumn<InstancesTableModel, Integer>("Sizeof", Integer.class, SwingConstants.RIGHT, 80, true) {
      @Override
      @NotNull
      public Integer getValue(@NotNull InstancesTableModel model, int row) {
        return model.getEntry(row).getCompositeSize();
      }
    });
    columns.add(new TableColumn<InstancesTableModel, ClassObj>("Dominating Class", ClassObj.class, SwingConstants.RIGHT, 400, false) {
      @Override
      @Nullable
      public ClassObj getValue(@NotNull InstancesTableModel model, int row) {
        Instance dominator = model.getEntry(row).getImmediateDominator();
        return dominator == null ? null : dominator.getClassObj();
      }
    });
    columns.add(new TableColumn<InstancesTableModel, Long>("Retained Size", Long.class, SwingConstants.RIGHT, 80, false) {
      @Override
      @NotNull
      public Long getValue(@NotNull InstancesTableModel model, int row) {
        assert (myHeap != null);
        return model.getEntry(row).getRetainedSize(mySnapshot.getHeapIndex(myHeap));
      }
    });

    return columns;
  }
}

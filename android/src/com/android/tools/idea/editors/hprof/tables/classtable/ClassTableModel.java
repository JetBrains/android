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
package com.android.tools.idea.editors.hprof.tables.classtable;

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
import java.util.List;

public class ClassTableModel extends HprofTableModel {
  @NotNull private List<TableColumn<ClassTableModel, ?>> myColumns;
  @NotNull private ArrayList<ClassObj> myEntries;

  public ClassTableModel(@NotNull Snapshot snapshot) {
    super();
    myColumns = createHeapTableColumns();
    myEntries = new ArrayList<ClassObj>();
    for (Heap heap : snapshot.getHeaps()) {
      myEntries.addAll(heap.getClasses());
    }
  }

  @NotNull
  protected static List<TableColumn<ClassTableModel, ?>> createHeapTableColumns() {
    List<TableColumn<ClassTableModel, ?>> columns = new ArrayList<TableColumn<ClassTableModel, ?>>();
    columns.add(new TableColumn<ClassTableModel, ClassObj>("Class Name", ClassObj.class, SwingConstants.LEFT, 800, true) {
      @Override
      @NotNull
      public ClassObj getValue(@NotNull ClassTableModel model, int row) {
        return model.getEntry(row);
      }
    });
    columns.add(new TableColumn<ClassTableModel, Integer>("Count", Integer.class, SwingConstants.RIGHT, 100, true) {
      @Override
      @NotNull
      public Integer getValue(@NotNull ClassTableModel model, int row) {
        return model.getEntry(row).getInstances().size();
      }
    });
    columns.add(new TableColumn<ClassTableModel, Integer>("Sizeof", Integer.class, SwingConstants.RIGHT, 80, true) {
      @Override
      @NotNull
      public Integer getValue(@NotNull ClassTableModel model, int row) {
        return model.getEntry(row).getInstanceSize();
      }
    });
    columns.add(new TableColumn<ClassTableModel, Integer>("Shallow Size", Integer.class, SwingConstants.RIGHT, 100, true) {
      @Nullable
      @Override
      public Integer getValue(@NotNull ClassTableModel model, int row) {
        return model.getEntry(row).getShalowSize();
      }
    });
    columns.add(new TableColumn<ClassTableModel, Long>("Retained Size", Long.class, SwingConstants.RIGHT, 120, false) {
      @Override
      @NotNull
      public Long getValue(@NotNull ClassTableModel model, int row) {
        long totalSize = 0;
        for (Instance i : model.getEntry(row).getInstances()) {
          totalSize += i.getTotalRetainedSize();
        }
        return totalSize;
      }
    });

    return columns;
  }

  @NotNull
  public ClassObj getEntry(int row) {
    return myEntries.get(row);
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
  @NotNull
  protected TableColumn getColumn(int index) {
    return myColumns.get(index);
  }
}

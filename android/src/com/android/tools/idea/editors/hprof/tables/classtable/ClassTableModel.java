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
import com.android.tools.idea.editors.hprof.tables.SelectionModel;
import com.android.tools.idea.editors.hprof.tables.TableColumn;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ClassTableModel extends HprofTableModel {
  @NotNull private List<TableColumn<ClassTableModel, ?>> myColumns;
  @NotNull private ArrayList<ClassObj> myEntries;
  @NotNull private SelectionModel mySelectionModel;
  private int myCurrentHeapId;

  public ClassTableModel(@NotNull SelectionModel selectionModel) {
    super();
    mySelectionModel = selectionModel;
    myColumns = createHeapTableColumns();
    myEntries = new ArrayList<ClassObj>();
    mySelectionModel.addListener(new SelectionModel.SelectionListener() {
      @Override
      public void onHeapChanged(@NotNull Heap heap) {
        final Heap selectedHeap = mySelectionModel.getHeap();
        myCurrentHeapId = selectedHeap.getId();
        myEntries.clear();
        // Find the union of the classObjs this heap has instances of, plus the classObjs themselves that are allocated on this heap.
        HashSet<ClassObj> entriesSet = new HashSet<ClassObj>(selectedHeap.getClasses());
        for (Instance instance : selectedHeap.getInstances()) {
          entriesSet.add(instance.getClassObj());
        }
        myEntries.addAll(entriesSet);
        Collections.sort(myEntries, new Comparator<ClassObj>() {
          @Override
          public int compare(ClassObj o1, ClassObj o2) {
            return o1.getHeapInstancesCount(selectedHeap.getId()) - o2.getHeapInstancesCount(selectedHeap.getId());
          }
        });
        fireTableDataChanged();
      }

      @Override
      public void onClassObjChanged(@Nullable ClassObj classObj) {

      }

      @Override
      public void onInstanceChanged(@Nullable Instance instance) {

      }
    });
  }

  @NotNull
  protected List<TableColumn<ClassTableModel, ?>> createHeapTableColumns() {
    List<TableColumn<ClassTableModel, ?>> columns = new ArrayList<TableColumn<ClassTableModel, ?>>();
    columns.add(new TableColumn<ClassTableModel, ClassObj>("Class Name", ClassObj.class, SwingConstants.LEFT, 800, true) {
      @Override
      @NotNull
      public ClassObj getValue(@NotNull ClassTableModel model, int row) {
        return model.getEntry(row);
      }
    });
    columns.add(new TableColumn<ClassTableModel, Integer>("Total Count", Integer.class, SwingConstants.RIGHT, 100, true) {
      @Override
      @NotNull
      public Integer getValue(@NotNull ClassTableModel model, int row) {
        return model.getEntry(row).getInstanceCount();
      }
    });
    columns.add(new TableColumn<ClassTableModel, Integer>("Heap Count", Integer.class, SwingConstants.RIGHT, 100, true) {
      @Override
      @NotNull
      public Integer getValue(@NotNull ClassTableModel model, int row) {
        return model.getEntry(row).getHeapInstances(myCurrentHeapId).size();
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
        return model.getEntry(row).getShallowSize(myCurrentHeapId);
      }
    });
    columns.add(new TableColumn<ClassTableModel, Long>("Retained Size", Long.class, SwingConstants.RIGHT, 120, false) {
      @Override
      @NotNull
      public Long getValue(@NotNull ClassTableModel model, int row) {
        long totalSize = 0;
        for (Instance i : model.getEntry(row).getHeapInstances(myCurrentHeapId)) {
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

  public int findEntryRow(@NotNull ClassObj classObj) {
    for (int i = 0; i < myEntries.size(); ++i) {
      if (myEntries.get(i) == classObj) {
        return i;
      }
    }
    return -1;
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

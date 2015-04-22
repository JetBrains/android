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
package com.android.tools.idea.editors.hprof.tables.gcroottable;

import com.android.tools.idea.editors.hprof.tables.HprofTableModel;
import com.android.tools.idea.editors.hprof.tables.TableColumn;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.RootType;
import com.android.tools.perflib.heap.Snapshot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class GcRootTableModel extends HprofTableModel {
  private static final boolean HIDE_UNDEFINED_NAMES = false;
  @NotNull private Snapshot mySnapshot;
  private List<RootObj> myRoots;
  @NotNull private List<TableColumn<GcRootTableModel, ?>> myColumns;

  public GcRootTableModel(@NotNull Snapshot snapshot) {
    super();
    mySnapshot = snapshot;
    myColumns = createGcRootTableColumns();
    myRoots = new ArrayList<RootObj>();
    if (HIDE_UNDEFINED_NAMES) {
      for (RootObj root : mySnapshot.getGCRoots()) {
        if (root.getClassName(mySnapshot) != RootObj.UNDEFINED_CLASS_NAME) {
          myRoots.add(root);
        }
      }
    }
    else {
      myRoots.addAll(mySnapshot.getGCRoots());
    }
  }

  @Override
  public int getRowCount() {
    return myRoots.size();
  }

  @Override
  public int getColumnCount() {
    return myColumns.size();
  }

  @NotNull
  @Override
  protected TableColumn getColumn(int index) {
    return myColumns.get(index);
  }

  @NotNull
  private List<TableColumn<GcRootTableModel, ?>> createGcRootTableColumns() {
    List<TableColumn<GcRootTableModel, ?>> columns = new ArrayList<TableColumn<GcRootTableModel, ?>>();
    columns.add(new TableColumn<GcRootTableModel, String>("Root Instance", String.class, SwingConstants.LEFT, 600, true) {
      @Override
      public String getValue(@NotNull GcRootTableModel modelType, int row) {
        return modelType.myRoots.get(row).getClassName(mySnapshot);
      }
    });
    columns.add(new TableColumn<GcRootTableModel, RootType>("Root Type", RootType.class, SwingConstants.LEFT, 150, true) {
      @Override
      public RootType getValue(@NotNull GcRootTableModel modelType, int row) {
        return modelType.myRoots.get(row).getRootType();
      }
    });
    columns.add(new TableColumn<GcRootTableModel, Long>("Total Retention", Long.class, SwingConstants.RIGHT, 100, false) {
      @Override
      public Long getValue(@NotNull GcRootTableModel modelType, int row) {
        return modelType.myRoots.get(row).getTotalRetainedSize();
      }
    });

    return columns;
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * The delegate fires events using delegate indices. SubTableModel listeners expect model indices. This listener converts delegate indices
 * to model ones.
 */
final class SubTableModelListener implements TableModelListener {
  private final SubTableModel myModel;
  private final TableModelListener myDelegate;

  SubTableModelListener(@NotNull SubTableModel model, @NotNull TableModelListener delegate) {
    myModel = model;
    myDelegate = delegate;
  }

  @NotNull
  TableModelListener delegate() {
    return myDelegate;
  }

  @Override
  public void tableChanged(@NotNull TableModelEvent event) {
    int delegateColumnIndex = event.getColumn();

    if (delegateColumnIndex == TableModelEvent.ALL_COLUMNS) {
      myDelegate.tableChanged(new TableModelEvent(
        myModel,
        event.getFirstRow(),
        event.getLastRow(),
        TableModelEvent.ALL_COLUMNS,
        event.getType()));

      return;
    }

    if (!(myModel.getStartColumnSupplier().getAsInt() <= delegateColumnIndex &&
          delegateColumnIndex < myModel.getEndColumnSupplier().getAsInt())) {
      return;
    }

    myDelegate.tableChanged(new TableModelEvent(
      myModel,
      event.getFirstRow(),
      event.getLastRow(),
      myModel.convertColumnIndexToModel(delegateColumnIndex),
      event.getType()));
  }
}

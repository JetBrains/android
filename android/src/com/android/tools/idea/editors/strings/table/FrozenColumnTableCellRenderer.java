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

import com.intellij.ui.ColoredTableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class FrozenColumnTableCellRenderer extends ColoredTableCellRenderer {
  void customizeCellRenderer(@NotNull FrozenColumnTable table, @Nullable Object value, int viewRowIndex, int viewColumnIndex) {
  }

  @Override
  protected final void customizeCellRenderer(@NotNull JTable subTable,
                                             @Nullable Object value,
                                             boolean selected,
                                             boolean focusOwner,
                                             int viewRowIndex,
                                             int viewColumnIndex) {
    FrozenColumnTable frozenColumnTable = ((SubTable)subTable).getFrozenColumnTable();
    JTable frozenTable = frozenColumnTable.getFrozenTable();

    if (subTable == frozenTable) {
      customizeCellRenderer(frozenColumnTable, value, viewRowIndex, viewColumnIndex);
      return;
    }

    customizeCellRenderer(frozenColumnTable, value, viewRowIndex, viewColumnIndex + frozenTable.getColumnCount());
  }
}

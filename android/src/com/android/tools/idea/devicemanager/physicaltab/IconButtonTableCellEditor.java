/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.IconButton;
import java.awt.Component;
import javax.swing.AbstractCellEditor;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class IconButtonTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  final @NotNull IconButton myButton;
  private final @NotNull Object myValue;

  IconButtonTableCellEditor(@NotNull Icon icon, @NotNull Object value) {
    this(icon, value, null);
  }

  IconButtonTableCellEditor(@NotNull Icon icon, @NotNull Object value, @Nullable String tooltipText) {
    myButton = new IconButton(icon);

    myButton.setOpaque(true);
    myButton.setToolTipText(tooltipText);

    myValue = value;
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    // I'd pass selected instead of hard coding true but the selection is changed after the cell is edited. selected is false when I expect
    // it to be true.
    return myButton.getTableCellComponent(table, true, true);
  }

  @Override
  public final @NotNull Object getCellEditorValue() {
    return myValue;
  }
}

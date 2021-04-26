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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import java.awt.Component;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import javax.swing.table.TableColumn;
import org.jetbrains.annotations.NotNull;

/**
 * TableView that adjusts column widths automatically to not cut off table cell content
 */
final class VirtualTableView extends TableView<AvdInfo> {
  VirtualTableView(@NotNull ListTableModel<@NotNull AvdInfo> model) {
    super(model);
  }

  void setWidths() {
    IntStream.range(1, columnModel.getColumnCount())
      .forEach(viewColumnIndex -> {
        int preferredWidth = getPreferredColumnWidth(viewColumnIndex);
        TableColumn column = columnModel.getColumn(viewColumnIndex);
        column.setPreferredWidth(preferredWidth);
        column.setMinWidth(preferredWidth);
        column.setMaxWidth(preferredWidth);
      });
  }

  private int getPreferredColumnWidth(int viewColumnIndex) {
    OptionalInt width = IntStream.range(-1, getRowCount())
      .map(rowIndex -> getPreferredCellWidth(rowIndex, viewColumnIndex))
      .max();
    return width.orElse(0);
  }

  private int getPreferredCellWidth(int viewRowIndex, int viewColumnIndex) {
    Component component;
    if (viewRowIndex == -1) {
      component = getTableHeader().getDefaultRenderer().getTableCellRendererComponent(
        this, getColumnName(viewColumnIndex), false, false, -1, viewColumnIndex);
    }
    else {
      component = prepareRenderer(getCellRenderer(viewRowIndex, viewColumnIndex), viewRowIndex, viewColumnIndex);
    }
    return component.getPreferredSize().width + JBUI.scale(8);
  }
}

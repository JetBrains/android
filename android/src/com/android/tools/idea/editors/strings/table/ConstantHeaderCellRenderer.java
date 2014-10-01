/*
 * Copyright (C) 2014 The Android Open Source Project
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

import javax.swing.*;
import java.awt.*;

/**
 * Renderer for the header of a column that is always present in the table (e.g., Key).
 */
public class ConstantHeaderCellRenderer implements HeaderCellRenderer {
  private final int myCollapsedWidth;
  private final int myExpandedWidth;

  public ConstantHeaderCellRenderer(int index, @NotNull FontMetrics metrics) {
    int descriptionWidth = PADDING + metrics.stringWidth(ConstantColumn.values()[index].name);
    int dataWidth = PADDING + metrics.stringWidth(String.valueOf(ConstantColumn.values()[index].sampleData));
    myCollapsedWidth = Math.min(descriptionWidth, dataWidth);
    myExpandedWidth = Math.max(descriptionWidth, dataWidth);
  }

  @Override
  public int getCollapsedWidth() {
    return myCollapsedWidth;
  }

  @Override
  public int getFullExpandedWidth() {
    return myExpandedWidth;
  }

  @Override
  public int getMinimumExpandedWidth() {
    return (myCollapsedWidth + myExpandedWidth) / 2;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
    return table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table, value, selected, focused, row, column);
  }
}

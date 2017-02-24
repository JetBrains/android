/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.idea.uibuilder.property.NlResourceHeader;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.awt.*;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_BOLD;
import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;

public class NlSliceValueRenderer extends ColoredTableCellRenderer {
  public static final JBColor VALUE_COLOR = new JBColor(new Color(0, 128, 80), new Color(98, 150, 85));

  @Override
  protected void customizeCellRenderer(JTable table, @Nullable Object tableValue, boolean selected, boolean hasFocus, int row, int column) {
    if (tableValue instanceof NlResourceHeader) {
      setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    }
    else if (hasFocus) {
      setBorder(UIUtil.getTableFocusCellHighlightBorder());
    }
    PTableItem item = (PTableItem)tableValue;
    if (item == null) {
      return;
    }
    String value = item.getValue();
    if (value == null) {
      return;
    }
    SimpleTextAttributes attr = REGULAR_ATTRIBUTES.derive(STYLE_SMALLER | STYLE_BOLD, VALUE_COLOR, null, null);
    append(value, attr, true);
  }

  @Override
  public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
    // Do not change background color if a cell has focus
    super.acquireState(table, isSelected, false, row, column);
  }
}

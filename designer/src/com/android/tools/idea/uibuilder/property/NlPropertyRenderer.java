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
package com.android.tools.idea.uibuilder.property;

import com.android.annotations.VisibleForTesting;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NlPropertyRenderer extends ColoredTableCellRenderer {
  @Override
  protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
    if (!(value instanceof NlProperty)) {
      return;
    }

    NlProperty property = (NlProperty)value;

    customize(property, column);
  }

  @VisibleForTesting
  void customize(NlProperty property, int column) {
    if (column == 0) {
      appendName(property);
    } else {
      appendValue(property);
    }
  }

  private void appendValue(@NotNull NlProperty property) {
    String value = property.getValue();
    if (value == null) {
      value = "";
    }
    append(value, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  private void appendName(@NotNull NlProperty property) {
    append(property.getName());
    setToolTipText(property.getTooltipText());
  }
}

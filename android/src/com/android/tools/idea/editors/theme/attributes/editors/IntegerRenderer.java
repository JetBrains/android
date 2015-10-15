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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class IntegerRenderer extends DefaultTableCellRenderer {
  private static final Logger LOG = Logger.getInstance(IntegerRenderer.class);

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      LOG.error(String.format("Passed %1$s instead of EditedStyleItem", value.getClass().getName()));
      return null;
    }

    EditedStyleItem item = (EditedStyleItem) value;
    final Component component;

    if (column == 0) {
      component = table.getDefaultRenderer(String.class).getTableCellRendererComponent(table, ThemeEditorUtils.getDisplayHtml(item), isSelected, hasFocus, row, column);
    } else {
      component = super.getTableCellRendererComponent(table, item.getValue(), isSelected, hasFocus, row, column);
    }

    return component;
  }

}

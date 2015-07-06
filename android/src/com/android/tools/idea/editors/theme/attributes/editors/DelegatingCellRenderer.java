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

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.ui.JBColor;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * Cell renderer used to add tooltips to table cells containing {@link ItemResourceValue}
 *
 * Uses delegate to draw "simple" extracted value (which is usually a String or a Boolean)
 */
public class DelegatingCellRenderer implements TableCellRenderer {
  private final TableCellRenderer myDelegate;

  public DelegatingCellRenderer(final TableCellRenderer delegate) {
    myDelegate = delegate;
  }


  @Override
  public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {

    EditedStyleItem item = (value instanceof  EditedStyleItem) ? (EditedStyleItem) value : null;

    final Component returnedComponent =
      myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    ThemeEditorStyle selectedStyle = ((AttributesTableModel) table.getModel()).getSelectedStyle();
    // Displays in bold attributes that are overriding their inherited value
    returnedComponent.setFont(selectedStyle.hasItem(item) ? table.getFont().deriveFont(Font.BOLD) : table.getFont());

    returnedComponent.setForeground((item != null && !item.isPublicAttribute()) ? JBColor.LIGHT_GRAY : table.getForeground());

    if (!(returnedComponent instanceof JComponent)) {
      // Does not support tooltips
      return returnedComponent;
    }

    // Getting the tooltip information is an moderately expensive operation so we try to avoid doing it unless
    // it's necessary. We first check if the mouse is in the current cell being rendered and only then
    // we get the tooltip.
    final JComponent jComponent = (JComponent)returnedComponent;
    Point mousePos = table.getMousePosition();
    if (mousePos != null && item != null) {
      if (table.getCellRect(row, column, true).contains(mousePos)) {
        final ItemResourceValue resValue = ((EditedStyleItem)value).getSelectedValue();
        Configuration configuration = item.getSourceStyle().getConfiguration();
        String toolTipText = ThemeEditorUtils.generateToolTipText(resValue, configuration.getModule(), configuration);
        jComponent.setToolTipText(toolTipText);
      }
    } else {
      jComponent.setToolTipText(null);
    }

    return returnedComponent;
  }
}

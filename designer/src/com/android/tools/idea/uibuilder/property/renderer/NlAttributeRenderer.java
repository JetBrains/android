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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.BrowsePanel;
import com.android.tools.idea.uibuilder.property.editors.NlTableCellEditor;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Set;

public abstract class NlAttributeRenderer implements TableCellRenderer, BrowsePanel.Context {
  private final JPanel myPanel;
  private final JPanel myBrowsePanel;
  private JTable myTable;
  private int myRow;

  public NlAttributeRenderer() {
    myBrowsePanel = new BrowsePanel(this);
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));
    myPanel.add(myBrowsePanel, BorderLayout.LINE_END);
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
    assert value instanceof NlProperty;
    assert table instanceof PTable;
    myTable = table;
    myRow = row;

    Color fg, bg;
    if (isSelected) {
      fg = UIUtil.getTableSelectionForeground();
      bg = UIUtil.getTableSelectionBackground();
    }
    else {
      fg = UIUtil.getTableForeground();
      bg = UIUtil.getTableBackground();
    }

    myPanel.setForeground(fg);
    myPanel.setBackground(bg);

    for (int i = 0; i < myPanel.getComponentCount(); i++) {
      Component comp = myPanel.getComponent(i);
      comp.setForeground(fg);
      comp.setBackground(bg);
    }

    boolean hover = ((PTable)table).isHover(row, col);
    myBrowsePanel.setVisible(hover);

    customizeRenderContent(table, (NlProperty)value, isSelected, hasFocus, row, col);

    return myPanel;
  }

  public abstract void customizeRenderContent(@NotNull JTable table,
                                              @NotNull NlProperty p,
                                              boolean selected,
                                              boolean hasFocus,
                                              int row,
                                              int col);

  public abstract boolean canRender(@NotNull NlProperty p, @NotNull Set<AttributeFormat> formats);

  @Nullable
  @Override
  public NlProperty getProperty() {
    return NlTableCellEditor.getPropertyAt(myTable, myRow);
  }

  @Nullable
  @Override
  public NlProperty getDesignProperty() {
    return NlTableCellEditor.getPropertyAt(myTable, myRow + 1);
  }
}

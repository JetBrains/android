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
package com.android.tools.idea.editors.layoutInspector.ptable;

import com.android.tools.adtui.ptable.PNameRenderer;
import com.android.tools.adtui.ptable.PTableCellRendererProvider;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.editors.layoutInspector.ui.PropertiesTablePanel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class LITableRendererProvider implements PTableCellRendererProvider {
  private static LITableRendererProvider ourInstance = new LITableRendererProvider();

  private final LITableNameRenderer myNameRenderer;
  private final LIItemCellRenderer myItemCellRenderer;

  public static LITableRendererProvider getInstance() {
    if (ourInstance == null) {
      ourInstance = new LITableRendererProvider();
    }
    return ourInstance;
  }

  private LITableRendererProvider() {
    myNameRenderer = new LITableNameRenderer();
    myItemCellRenderer = new LIItemCellRenderer();
  }

  @NotNull
  @Override
  public PNameRenderer getNameCellRenderer(@NotNull PTableItem item) {
    return myNameRenderer;
  }

  @NotNull
  @Override
  public TableCellRenderer getValueCellRenderer(@NotNull PTableItem item) {
    return item.hasChildren() ? createGroupTableCellRenderer() : myItemCellRenderer;
  }

  private static ColoredTableCellRenderer createGroupTableCellRenderer() {
    return new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        setBackground(selected ? UIUtil.getTableSelectionBackground() : PropertiesTablePanel.GROUP_BACKGROUND_COLOR);
      }
    };
  }

  private class LIItemCellRenderer implements TableCellRenderer {
    private final JPanel myPanel;
    private final SimpleColoredComponent myLabel;

    public LIItemCellRenderer() {
      myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));
      myLabel = new SimpleColoredComponent();
      myPanel.add(myLabel, BorderLayout.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
      Color fg, bg;
      if (isSelected) {
        fg = UIUtil.getTableSelectionForeground();
        bg = UIUtil.getTableSelectionBackground();
      }
      else {
        fg = UIUtil.getTableForeground();
        bg = PropertiesTablePanel.ITEM__BACKGROUND_COLOR;
      }

      myPanel.setForeground(fg);
      myPanel.setBackground(bg);

      for (int i = 0; i < myPanel.getComponentCount(); i++) {
        Component comp = myPanel.getComponent(i);
        comp.setForeground(fg);
        comp.setBackground(bg);
      }

      customizeRenderContent(table, (LITableItem)value, isSelected, hasFocus, row, col);

      return myPanel;
    }

    public void customizeRenderContent(@NotNull JTable table, @NotNull LITableItem item, boolean selected, boolean hasFocus, int row, int col) {
      myLabel.clear();
      appendValue(item);
    }

    private void appendValue(LITableItem item) {
      String value = item.getValue();
      String text = StringUtil.notNullize(value);
      if (!item.isDefaultValue(value)) {
        myLabel.setForeground(JBColor.BLUE);
      }

      myLabel.append(text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      myLabel.setToolTipText(text);
    }
  }
}

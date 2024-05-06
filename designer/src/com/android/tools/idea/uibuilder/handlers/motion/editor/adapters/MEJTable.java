/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * Abstraction of a JTable/JBTable
 */
public class MEJTable extends JBTable {
  public MEJTable(DefaultTableModel model) {
    super(model);
    setDefaultRenderer(Icon.class, new DefaultIconRenderer());
  }

  @Override
  public Color getSelectionForeground() {
    return UIUtil.getTableSelectionForeground(isFocusOwner());
  }

  @Override
  public Color getSelectionBackground() {
    return UIUtil.getTableSelectionBackground(isFocusOwner());
  }

  private static class DefaultIconRenderer implements TableCellRenderer {
    private final JPanel myPanel;
    private final JBLabel myLabel;

    private DefaultIconRenderer() {
      myPanel = new AdtSecondaryPanel();
      myLabel = new JBLabel();
      myPanel.add(myLabel);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Icon icon = (Icon)value;
      boolean showFocus = table.isFocusOwner() && isSelected;
      if (showFocus && icon != null && !ExperimentalUI.isNewUI()) {
        myLabel.setIcon(ColoredIconGenerator.generateWhiteIcon(icon));
      }
      else {
        myLabel.setIcon(icon);
      }
      myPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return myPanel;
    }
  }
}

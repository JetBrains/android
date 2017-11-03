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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.adtui.ptable.PNameRenderer;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableCellRenderer;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.editors.layoutInspector.ui.PropertiesTablePanel;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LITableNameRenderer implements PNameRenderer {
  private final JPanel myPanel;
  private final PTableCellRenderer myRenderer;

  public LITableNameRenderer() {
    myPanel = new JPanel(new BorderLayout());
    myRenderer = new Renderer();
    myPanel.add(myRenderer, BorderLayout.CENTER);
  }

  @Override
  public Component getTableCellRendererComponent(@NotNull JTable table, @NotNull Object value, boolean isSelected, boolean cellHasFocus,
                                                 int row, int column) {
    myRenderer.clear();
    PTableItem item = (PTableItem)value;

    myRenderer.getTableCellRendererComponent(table, value, isSelected, cellHasFocus, row, column);
    myRenderer.setBackground(
      isSelected
      ? UIUtil.getTableSelectionBackground()
      : item.hasChildren() ? PropertiesTablePanel.GROUP_BACKGROUND_COLOR : PropertiesTablePanel.ITEM__BACKGROUND_COLOR);
    myPanel.setBackground(isSelected
                          ? UIUtil.getTableSelectionBackground()
                          : item.hasChildren() ? PropertiesTablePanel.GROUP_BACKGROUND_COLOR : PropertiesTablePanel.ITEM__BACKGROUND_COLOR);

    String label = item.getParent() != null ? item.getParent().getChildLabel(item) : item.getName();
    myRenderer.append(label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myRenderer.setToolTipText(item.getTooltipText());
    myRenderer.setBorder(new EmptyBorder(1, 1, 1, 1));
    return myPanel;
  }

  private static class Renderer extends PTableCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull PTable table,
                                         @NotNull PTableItem value,
                                         boolean selected,
                                         boolean hasFocus,
                                         int row,
                                         int column) {
      setIcon(value, selected, hasFocus);
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
    }

    private void setIcon(@NotNull PTableItem item, boolean selected, boolean hasFocus) {
      Icon groupIcon = UIUtil.getTreeNodeIcon(item.isExpanded(), selected, hasFocus);

      Icon icon;
      int indent;
      int textGap;
      if (item.hasChildren()) {
        icon = groupIcon;
        indent = getBeforeIconSpacing(getDepth(item), groupIcon.getIconWidth());
        textGap = getAfterIconSpacing(groupIcon.getIconWidth());
      }
      else {
        icon = null;
        indent = 2;
        textGap = 0;
      }

      setIcon(icon);
      setIconTextGap(textGap);
      setIpad(JBUI.insetsLeft(indent));
    }
  }

  @Override
  public boolean hitTestTreeNodeIcon(@NotNull PTableItem item, @SwingCoordinate int x, @SwingCoordinate int y) {
    Icon icon = UIUtil.getTreeNodeIcon(item.isExpanded(), true, true);
    int beforeIcon = getBeforeIconSpacing(getDepth(item), icon.getIconWidth());
    return x >= beforeIcon && x <= beforeIcon + icon.getIconWidth();
  }

  private static int getBeforeIconSpacing(int depth, int iconWidth) {
    int nodeIndent = UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent();
    int leftIconOffset = Math.max(0, UIUtil.getTreeLeftChildIndent() - (iconWidth / 2));
    return nodeIndent * depth + leftIconOffset;
  }

  private static int getAfterIconSpacing(int iconWidth) {
    int nodeIndent = UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent();
    int leftIconOffset = Math.max(0, UIUtil.getTreeLeftChildIndent() - (iconWidth / 2));
    return Math.max(0, nodeIndent - leftIconOffset - iconWidth);
  }

  private static int getDepth(@NotNull PTableItem item) {
    int result = 0;
    while (item.getParent() != null) {
      result++;
      item = item.getParent();
      assert item != null;
    }
    return result;
  }
}

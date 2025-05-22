/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.stdui.StandardColors;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExpandedItemRendererComponentWrapper;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;

/**
 * A JTable which can highlight the hovered row.
 */
public final class HoverRowTable extends JBTable {
  private int myHoveredRow = -1;
  private final Color myHoverColor;

  public HoverRowTable(@NotNull TableModel model) {
    super(model);
    myHoverColor = StandardColors.HOVER_COLOR;
    MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        hoveredRowChanged(rowAtPoint(e.getPoint()));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        hoveredRowChanged(-1);
      }

    };
    addMouseMotionListener(mouseAdapter);
    addMouseListener(mouseAdapter);
    getEmptyText().clear();
    setIntercellSpacing(new Dimension());
  }

  private void hoveredRowChanged(int row) {
    if (row == myHoveredRow) {
      return;
    }
    myHoveredRow = row;
    repaint();
  }

  @NotNull
  @Override
  public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
    Component comp = super.prepareRenderer(renderer, row, column);
    Component toChangeComp = comp;

    if (comp instanceof ExpandedItemRendererComponentWrapper) {
      // To be able to show extended value of a cell via popup, when the value is stripped,
      // JBTable wraps the cell component into ExpandedItemRendererComponentWrapper.
      // So, we need to change background and foreground colors of the cell component rather than the wrapper.
      toChangeComp = ((ExpandedItemRendererComponentWrapper)comp).getComponent(0);
    }

    if (getRowSelectionAllowed() && isRowSelected(row)) {
      toChangeComp.setForeground(getSelectionForeground());
      toChangeComp.setBackground(getSelectionBackground());
    }
    else if (row == myHoveredRow) {
      toChangeComp.setBackground(myHoverColor);
      toChangeComp.setForeground(getForeground());
    }
    else {
      toChangeComp.setBackground(getBackground());
      toChangeComp.setForeground(getForeground());
    }

    return comp;
  }

  @Override
  public void paint(@NotNull Graphics g) {
    if (g instanceof Graphics2D) {
      // Manually set the KEY_TEXT_LCD_CONTRAST here otherwise JTable/JList use inconsistent values by default compared to the rest
      // of the IntelliJ UI.
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, UIUtil.getLcdContrastValue());
    }
    super.paint(g);
    // When the table is empty and the empty table message is visible, the vertical lines should not be drawn that overlaps with empty text.
    if (isEmpty() && !StringUtil.isEmpty(getEmptyText().getText())) {
      return;
    }
    // Draw column line down to bottom of table, matches the look and feel of BasicTableUI#paintGrid which is private and cannot override.
    // To avoid non-transparent grid lines on top of selection color, draw them below the last row.
    TableColumnModel columnModel = getColumnModel();
    List<Integer> columnX = new ArrayList<>();
    for (int index = 0, x = 0; index < columnModel.getColumnCount() - 1; index++) {
      int column = getComponentOrientation().isLeftToRight() ? index : columnModel.getColumnCount() - 1 - index;
      x += columnModel.getColumn(column).getWidth();
      columnX.add(x - 1);
    }
    g.setColor(getGridColor());
    final int lastRowBottom = getRowCount() * getRowHeight();
    columnX.forEach((Integer x) -> g.drawLine(x, lastRowBottom, x, getHeight()));
    // Use a blending color of selection color and grid color to replace transparent grid lines look.
    if (getSelectedRow() != -1 && getCellSelectionEnabled()) {
      g.setColor(AdtUiUtils.overlayColor(getSelectionBackground().getRGB(), getGridColor().getRGB(), 0.25f));
      Rectangle selectedRowRect = getCellRect(getSelectedRow(), 0, true);
      columnX.forEach((Integer x) -> g.drawLine(x, selectedRowRect.y, x, selectedRowRect.y + selectedRowRect.height - 1));
    }
  }
}

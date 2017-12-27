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
package com.android.tools.profilers.network;

import com.intellij.ui.ExpandedItemRendererComponentWrapper;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A JTable which can highlight the hovered row.
 */
final class HoverRowTable extends JBTable {
  private int myHoveredRow = -1;
  private final Color myHoverColor;

  HoverRowTable(@NotNull TableModel model, @NotNull Color hoverColor) {
    super(model);
    myHoverColor = hoverColor;
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
    // Draw column line down to bottom of table, matches the look and feel of BasicTableUI#paintGrid which is private and cannot override.
    g.setColor(getGridColor());
    TableColumnModel columnModel = getColumnModel();
    int x = 0;
    if (getComponentOrientation().isLeftToRight()) {
      for (int column = 0; column < columnModel.getColumnCount() - 1; column++) {
        x += columnModel.getColumn(column).getWidth();
        g.drawLine(x - 1, 0, x - 1, getHeight());
      }
    } else {
      for (int column = columnModel.getColumnCount() - 1; column > 0; column--) {
        x += columnModel.getColumn(column).getWidth();
        g.drawLine(x - 1, 0, x - 1, getHeight());
      }
    }
  }
}

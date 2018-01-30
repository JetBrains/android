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
package com.android.tools.adtui.stdui;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.*;

/**
 * An Adaptation of {@link #BasicTabbedPaneUI} with the following changes:
 * 1) Supports hover states when mouse over unselected tabs
 * 2) Simpler content panel border rendering - only the border along the tab placement is drawn.
 */
class CommonTabbedPaneUI extends BasicTabbedPaneUI {

  private final MouseListener mouseHoverListener;
  private final MouseMotionListener mouseMotionListener;
  private int myHoveredTabIndex = -1;

  public CommonTabbedPaneUI() {
    mouseHoverListener = new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        setHoveredTabIndex(-1);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        setTabIndexForMouseEvent(e);
      }
    };

    mouseMotionListener = new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        setTabIndexForMouseEvent(e);
      }
    };
  }

  @Override
  protected void installListeners() {
    super.installListeners();
    tabPane.addMouseListener(mouseHoverListener);
    tabPane.addMouseMotionListener(mouseMotionListener);
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    tabPane.removeMouseListener(mouseHoverListener);
    tabPane.removeMouseMotionListener(mouseMotionListener);
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    tabInsets = JBUI.insets(9, 12, 8, 12);
    tabAreaInsets = JBUI.insets(-1, 0);
    contentBorderInsets = JBUI.insets(0, 0, 0, 0);
  }

  private void setHoveredTabIndex(int index) {
    int previousIndex = myHoveredTabIndex;
    myHoveredTabIndex = index;

    // Un-hover the previously hovered tab
    if (previousIndex >= 0) {
      Rectangle tabBound = getTabBounds(tabPane, previousIndex);
      tabPane.repaint(tabBound);
    }

    // Update the currently hovered tab.
    if (myHoveredTabIndex >= 0) {
      Rectangle tabBound = getTabBounds(tabPane, myHoveredTabIndex);
      tabPane.repaint(tabBound);
    }
  }

  private void setTabIndexForMouseEvent(@NotNull MouseEvent e) {
    int tabIndex = tabForCoordinate(tabPane, e.getX(), e.getY());
    setHoveredTabIndex(tabIndex);
  }

  private Rectangle adjustTabRect(int tabPlacement, Rectangle rect) {
    Rectangle adj = new Rectangle(rect);
    switch (tabPlacement) {
      case SwingConstants.BOTTOM:
        adj.height -= 1;
        adj.y += 1;
        break;
      case SwingConstants.LEFT:
        adj.width -= 1;
        break;
      case SwingConstants.RIGHT:
        adj.width -= 1;
        adj.x += 1;
        break;
      case SwingConstants.TOP:
      default:
        adj.height -= 1;
        break;
    }
    return adj;
  }

  @Override
  protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
    super.paintTabArea(g, tabPlacement, selectedIndex);
    int width = tabPane.getWidth();
    int height = tabPane.getHeight();
    Insets insets = tabPane.getInsets();
    int x = insets.left;
    int y = insets.top;
    int w = width - insets.right - insets.left;
    int h = height - insets.top - insets.bottom;
    int tw = calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
    int th = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);

    g.setColor(StandardColors.TAB_BORDER_COLOR);
    switch (tabPlacement) {
      case LEFT:
        g.drawLine(x + tw - 1, y, x + tw - 1, y + h);
        break;
      case RIGHT:
        g.drawLine(x + w - tw, y, x + w - tw, y + w);
        break;
      case BOTTOM:
        g.drawLine(x, y + h - th, x + w, y + h - th);
        break;
      case TOP:
      default:
        g.drawLine(x, y + th - 1, x + w, y + th - 1);
    }
  }

  @Override
  protected void paintTab(Graphics g, int tabPlacement, Rectangle[] rects,
                          int tabIndex, Rectangle iconRect, Rectangle textRect) {
    if (g instanceof Graphics2D) {
      Graphics2D g2d = (Graphics2D)g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR);
    }

    Rectangle tabRect = adjustTabRect(tabPlacement, rects[tabIndex]);
    String title = tabPane.getTitleAt(tabIndex);
    Icon icon = getIconForTab(tabIndex);
    int selectedIndex = tabPane.getSelectedIndex();
    boolean isSelected = selectedIndex == tabIndex;
    Font font = tabPane.getFont();
    FontMetrics metrics = SwingUtilities2.getFontMetrics(tabPane, g, font);

    if (tabIndex == myHoveredTabIndex) {
      g.setColor(StandardColors.TAB_HOVER_COLOR);
      g.fillRect(tabRect.x, tabRect.y, tabRect.width, tabRect.height);
    }

    if (tabPane.getTabComponentAt(tabIndex) == null) {
      textRect.x = textRect.y = iconRect.x = iconRect.y = 0;
      SwingUtilities.layoutCompoundLabel(tabPane,
                                         metrics, title, icon,
                                         SwingConstants.CENTER,
                                         SwingConstants.CENTER,
                                         SwingConstants.CENTER,
                                         SwingConstants.TRAILING,
                                         tabRect,
                                         iconRect,
                                         textRect,
                                         textIconGap);

      g.setColor(isSelected ? StandardColors.TAB_SELECTED_COLOR : tabPane.getForeground());
      g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
      paintIcon(g, tabPlacement, tabIndex, icon, iconRect, isSelected);
    }
    paintTabBorder(g, tabPlacement, tabIndex, tabRect.x, tabRect.y,
                   tabRect.width, tabRect.height, isSelected);
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement,
                                int tabIndex,
                                int x, int y, int w, int h,
                                boolean isSelected) {
    if (!isSelected) {
      return;
    }
    g.setColor(StandardColors.TAB_SELECTED_COLOR);
    switch (tabPlacement) {
      case LEFT:
        g.fillRect(x + w - 2 - 1, y, 2, h);
        break;
      case RIGHT:
        g.fillRect(x + 1, y, 2, h);
        break;
      case BOTTOM:
        g.fillRect(x, y + 1, w, 2);
        break;
      case TOP:
      default:
        g.fillRect(x, y + h - 1 - 2, w, 2);
    }
  }

  @Override
  protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
    // No border
  }
}

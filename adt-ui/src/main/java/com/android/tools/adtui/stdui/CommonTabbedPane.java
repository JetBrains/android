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

import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * A simple tabbed pane that mimics the laf of IntelliJ's flat toolbar tabs. Supports hover states over tab.
 * TODO: render vertical texts for left/right tab placement.
 */
public class CommonTabbedPane extends JTabbedPane {

  @NotNull private final CommonTabbedPaneUI myUi;

  enum ActionDirection {
    LEFT,
    RIGHT
  }

  final class NavigateAction extends AbstractAction {
    private final ActionDirection myDirection;

    NavigateAction(ActionDirection direction) {
      myDirection = direction;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int tabCount = getTabCount();
      if (myDirection == ActionDirection.RIGHT) {
        setSelectedIndex((getSelectedIndex() + 1) % tabCount);
      }
      else {
        setSelectedIndex((getSelectedIndex() - 1 + tabCount) % tabCount);
      }
    }
  }

  public CommonTabbedPane() {
    myUi = new CommonTabbedPaneUI();
    setUI(myUi);
    setFont(AdtUiUtils.DEFAULT_FONT);
    getActionMap().put("navigatePrevious", new NavigateAction(ActionDirection.LEFT));
    getActionMap().put("navigateNext", new NavigateAction(ActionDirection.RIGHT));
    getActionMap().put("navigateLeft", new NavigateAction(ActionDirection.LEFT));
    getActionMap().put("navigateRight", new NavigateAction(ActionDirection.RIGHT));
    // Sets up mouse listeners to support hover state rendering.
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        myUi.setHoveredTabIndex(-1);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        setTabIndexForMouseEvent(e);
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        setTabIndexForMouseEvent(e);
      }
    });
  }

  @Override
  public void updateUI() {
    // Always set the UI back the our FlatTabbedPaneUI instance as the hover state functionality depends on it.
    setUI(myUi);
  }

  @NotNull
  public Insets getTabAreaInsets() {
    return myUi.myTabAreaInsets;
  }

  public void setTabAreaInsets(@NotNull Insets insets) {
    myUi.setTabAreaInsets(insets);
  }

  @NotNull
  public Insets getTabInsets() {
    return myUi.myTabInsets;
  }

  public void setTabInsets(@NotNull Insets insets) {
    myUi.setTabInsets(insets);
  }

  @NotNull
  public Insets getContentBorderInsets() {
    return myUi.myContentBorderInsets;
  }

  public void setContentBorderInsets(@NotNull Insets insets) {
    myUi.setContentBorderInsets(insets);
  }

  private void setTabIndexForMouseEvent(@NotNull MouseEvent e) {
    int tabIndex = myUi.tabForCoordinate(this, e.getX(), e.getY());
    myUi.setHoveredTabIndex(tabIndex);
  }

  /**
   * An Adaptation of {@link #BasicTabbedPaneUI} with the following changes:
   * 1) Supports hover states when mouse over unselected tabs
   * 2) Simpler content panel border rendering - only the border along the tab placement is drawn.
   */
  private static class CommonTabbedPaneUI extends BasicTabbedPaneUI {

    private static final Color DEFAULT_ACTIVE_COLOR = new JBColor(new Color(255, 255, 255), new Color(81, 86, 88));
    private static final Color DEFAULT_INACTIVE_COLOR = new JBColor(new Color(212, 212, 212), new Color(60, 62, 63));
    private static final Color DEFAULT_HOVER_COLOR = new JBColor(new Color(230, 230, 230), new Color(70, 74, 75));
    private static final Color DEFAULT_BORDER_COLOR = new JBColor(Gray._201, Gray._40);
    private static final Insets DEFAULT_TAB_INSETS = new Insets(8, 12, 7, 12);
    // By default, make the tab area flushed against the top and bottom without borders (hence the -1s).
    private static final Insets DEFAULT_TAB_AREA_INSETS = new Insets(-1, 0, -1, 0);
    private static final Insets DEFAULT_CONTENT_BORDER_INSETS = new Insets(4, 0, 0, 0);

    private int myHoveredTabIndex = -1;
    @NotNull private final Color myActiveColor;
    @NotNull private final Color myInactiveColor;
    @NotNull private final Color myHoverColor;
    @NotNull private final Color myBorderColor;
    @NotNull private Insets myTabInsets;
    @NotNull private Insets myTabAreaInsets;
    @NotNull private Insets myContentBorderInsets;

    public CommonTabbedPaneUI() {
      myActiveColor = DEFAULT_ACTIVE_COLOR;
      myInactiveColor = DEFAULT_INACTIVE_COLOR;
      myHoverColor = DEFAULT_HOVER_COLOR;
      myBorderColor = DEFAULT_BORDER_COLOR;
      myTabInsets = DEFAULT_TAB_INSETS;
      myTabAreaInsets = DEFAULT_TAB_AREA_INSETS;
      myContentBorderInsets = DEFAULT_CONTENT_BORDER_INSETS;
    }

    @Override
    public void installUI(JComponent c) {
      super.installUI(c);
      // super.installUI would reset to the defaults so we have to set them to our custom values after.
      overwriteBaseSettings();
    }

    private void setTabAreaInsets(@NotNull Insets insets) {
      myTabAreaInsets = insets;
      overwriteBaseSettingsAndRevalidate();
    }

    private void setTabInsets(@NotNull Insets insets) {
      tabInsets = myTabInsets = insets;
      overwriteBaseSettingsAndRevalidate();
    }

    private void setContentBorderInsets(@NotNull Insets insets) {
      contentBorderInsets = myContentBorderInsets = insets;
      overwriteBaseSettingsAndRevalidate();
    }

    /**
     * Update the base class properties with our custom values, as the former values are what actually used for rendering.
     */
    private void overwriteBaseSettings() {
      tabInsets = myTabInsets;
      tabAreaInsets = myTabAreaInsets;
      contentBorderInsets = myContentBorderInsets;
    }

    private void overwriteBaseSettingsAndRevalidate() {
      overwriteBaseSettings();
      tabPane.revalidate();
      tabPane.repaint();
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

    @Override
    public void paint(Graphics g, JComponent c) {
      if (!tabPane.isValid()) {
        tabPane.validate();
      }

      int selectedIndex = tabPane.getSelectedIndex();
      int tabPlacement = tabPane.getTabPlacement();

      // The superclass's paint method does not always draw the content border after drawing the tab area.
      // Here we force the order so we always get the content border overlaying the tabs
      paintTabArea(g, tabPlacement, selectedIndex);
      paintContentBorder(g, tabPlacement, selectedIndex);
    }

    @Override
    protected void paintTab(Graphics g, int tabPlacement, Rectangle[] rects,
                            int tabIndex, Rectangle iconRect, Rectangle textRect) {
      if (g instanceof Graphics2D) {
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR);
      }

      Rectangle tabRect = rects[tabIndex];
      String title = tabPane.getTitleAt(tabIndex);
      Icon icon = getIconForTab(tabIndex);
      int selectedIndex = tabPane.getSelectedIndex();
      boolean isSelected = selectedIndex == tabIndex;
      Font font = tabPane.getFont();
      FontMetrics metrics = SwingUtilities2.getFontMetrics(tabPane, g, font);

      if (isSelected) {
        g.setColor(myActiveColor);
      }
      else if (tabIndex == myHoveredTabIndex) {
        g.setColor(myHoverColor);
      }
      else {
        g.setColor(myInactiveColor);
      }
      g.fillRect(tabRect.x, tabRect.y, tabRect.width, tabRect.height);
      paintTabBorder(g, tabPlacement, tabIndex, tabRect.x, tabRect.y, tabRect.width, tabRect.height, isSelected);

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
        paintText(g, tabPlacement, font, metrics, tabIndex, title, textRect, isSelected);
        paintIcon(g, tabPlacement, tabIndex, icon, iconRect, isSelected);
      }
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement,
                                  int tabIndex,
                                  int x, int y, int w, int h,
                                  boolean isSelected) {
      int startX = x;
      int endX = x + w;
      int startY = y;
      int endY = y + h;
      g.setColor(myBorderColor);
      switch (tabPlacement) {
        case LEFT:
          if (startY > 0) {
            // Special-case: do not draw border if tab is flushed against the edge
            g.drawLine(startX, startY, endX, startY); // top
          }
          g.drawLine(startX, startY, startX, endY); // left
          g.drawLine(startX, endY, endX, endY); // bottom
          break;
        case RIGHT:
          if (startY > 0) {
            // Special-case: do not draw border if tab is flushed against the edge
            g.drawLine(startX, startY, endX, startY); // top
          }
          g.drawLine(endX, startY, endX, endY); // right
          g.drawLine(startX, endY, endX, endY); // bottom
          break;
        case BOTTOM:
          if (startX > 0) {
            g.drawLine(startX, startY, startX, endY); // left
          }
          g.drawLine(startX + w, startY, endX, endY); // right
          g.drawLine(startX, endY, endX, endY); // bottom
          break;
        case TOP:
        default:
          if (startX > 0) {
            g.drawLine(startX, startY, startX, endY); // left
          }
          g.drawLine(startX, startY, endX, startY); // top
          g.drawLine(endX, startY, endX, endY); // right
      }
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
      int width = tabPane.getWidth();
      int height = tabPane.getHeight();
      Insets insets = tabPane.getInsets();
      int x = insets.left;
      int y = insets.top;
      int w = width - insets.right - insets.left;
      int h = height - insets.top - insets.bottom;

      switch (tabPlacement) {
        case LEFT:
          x += calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth) + 1;
          w -= (x - insets.left);
          break;
        case RIGHT:
          w -= calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth) + 1;
          break;
        case BOTTOM:
          h -= calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight) + 1;
          break;
        case TOP:
        default:
          y += calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight) + 1;
          h -= (y - insets.top);
      }

      if (tabPane.getTabCount() > 0 && tabPane.isOpaque()) {
        g.setColor(myActiveColor);
        g.fillRect(x, y, w, h);
      }

      g.setColor(myBorderColor);
      paintContentBorderTopEdge(g, tabPlacement, selectedIndex, x, y, w, h);
      paintContentBorderLeftEdge(g, tabPlacement, selectedIndex, x, y, w, h);
      paintContentBorderBottomEdge(g, tabPlacement, selectedIndex, x, y, w, h);
      paintContentBorderRightEdge(g, tabPlacement, selectedIndex, x, y, w, h);
    }

    @Override
    protected void paintContentBorderTopEdge(Graphics g, int tabPlacement,
                                             int selectedIndex,
                                             int x, int y, int w, int h) {
      if (tabPlacement != TOP) {
        return;
      }
      Rectangle selRect = selectedIndex < 0 ? null : getTabBounds(selectedIndex, calcRect);

      // Draw unbroken line if selected tab is not in run adjacent to content, OR selected tab is not visible (SCROLL_TAB_LAYOUT)
      if (selectedIndex < 0 || (selRect.y + selRect.height + 1 < y) || (selRect.x < x || selRect.x > x + w)) {
        g.drawLine(x, y, x + w, y);
      }
      else {
        // Break line to show visual connection to selected tab
        if (selRect.x - x > 0) {
          g.drawLine(x, y, selRect.x, y);
        }
        if (selRect.x + selRect.width < x + w) {
          g.drawLine(selRect.x + selRect.width, y, x + w, y);
        }
      }
    }

    @Override
    protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement,
                                              int selectedIndex,
                                              int x, int y, int w, int h) {
      if (tabPlacement != LEFT) {
        return;
      }
      Rectangle selRect = selectedIndex < 0 ? null : getTabBounds(selectedIndex, calcRect);

      // Draw unbroken line if selected tab is not in run adjacent to content, OR selected tab is not visible (SCROLL_TAB_LAYOUT)
      if (selectedIndex < 0 || (selRect.x + selRect.width + 1 < x) || (selRect.y < y || selRect.y > y + h)) {
        g.drawLine(x, y, x, y + h);
      }
      else {
        // Break line to show visual connection to selected tab
        if (selRect.y - y > 0) {
          g.drawLine(x, y, x, selRect.y);
        }
        if (selRect.y + selRect.height < y + h) {
          g.drawLine(x, selRect.y + selRect.height, x, y + h);
        }
      }
    }

    @Override
    protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement,
                                                int selectedIndex,
                                                int x, int y, int w, int h) {
      if (tabPlacement != BOTTOM) {
        return;
      }
      Rectangle selRect = selectedIndex < 0 ? null : getTabBounds(selectedIndex, calcRect);

      // Draw unbroken line if selected tab is not in run adjacent to content, OR selected tab is not visible (SCROLL_TAB_LAYOUT)
      if (selectedIndex < 0 || (selRect.y - 1 > h) || (selRect.x < x || selRect.x > x + w)) {
        g.drawLine(x, y + h, x + w, y + h);
      }
      else {
        // Break line to show visual connection to selected tab
        if (selRect.x - x > 0) {
          g.drawLine(x, y + h, selRect.x, y + h);
        }
        if (selRect.x + selRect.width < x + w) {
          g.drawLine(selRect.x + selRect.width, y + h, x + w, y + h);
        }
      }
    }

    @Override
    protected void paintContentBorderRightEdge(Graphics g, int tabPlacement,
                                               int selectedIndex,
                                               int x, int y, int w, int h) {
      if (tabPlacement != RIGHT) {
        return;
      }
      Rectangle selRect = selectedIndex < 0 ? null : getTabBounds(selectedIndex, calcRect);

      // Draw unbroken line if selected tab is not in run adjacent to content, OR selected tab is not visible (SCROLL_TAB_LAYOUT)
      if (selectedIndex < 0 || (selRect.x - 1 > w) || (selRect.y < y || selRect.y > y + h)) {
        g.drawLine(x + w, y, x + w, y + h);
      }
      else {
        // Break line to show visual connection to selected tab
        if (selRect.y - y > 0) {
          g.drawLine(x + w, y, x + w, selRect.y);
        }
        if (selRect.y + selRect.height < y + h) {
          g.drawLine(x + w, selRect.y + selRect.height, x + w, y + h);
        }
      }
    }
  }
}

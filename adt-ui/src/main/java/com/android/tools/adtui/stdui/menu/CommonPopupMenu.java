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
package com.android.tools.adtui.stdui.menu;

import com.android.tools.adtui.RangeScrollBarUI;
import com.intellij.ui.components.JBScrollBar;
import java.awt.Adjustable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Toolkit;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import org.jetbrains.annotations.NotNull;

/**
 * A popup menu that supports scrolling if the number of menu items exceeds what the screen can render.
 * <p>
 * We also need this class because {@link JMenu} does not expose its internal popup menu used for showing sub-menus, and thus there is no
 * way for us to control its style. The current solution essentially duplicates the {@link JMenu} implementation into {@link CommonMenu},
 * changing the internal logic to instantiate this {@link CommonPopupMenu} instead, where we can control the style. A better solution might
 * be to go through the laf system instead, which would eliminate the need to duplicate/subclass the entire menu chain.
 * <p>
 * Note that at the moment, the scrollbar component is always inserted and kept at index 0 of the children component list. We should look
 * for ways to hide it from the public APIs.
 *
 * @deprecated Prefer the use of {@link com.android.tools.adtui.actions.DropDownAction} instead.
 */
@Deprecated
public class CommonPopupMenu extends JPopupMenu {

  // Scrollbar used for when the menus are longer than what the screen can render.
  @NotNull private final JScrollBar myScrollBar;
  @NotNull private final MouseWheelListener myListener;

  public CommonPopupMenu() {
    setLayout(new ScrollablePopupLayout());

    myScrollBar = new JBScrollBar(Adjustable.VERTICAL);
    myScrollBar.setUI(new RangeScrollBarUI());
    myScrollBar.addAdjustmentListener(new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        doLayout();
        repaint();
      }
    });

    myListener = new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        int scrollAmount = e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ?
                           e.getUnitsToScroll() * myScrollBar.getUnitIncrement() :
                           (e.getWheelRotation() >= 0 ? 1 : -1) * myScrollBar.getBlockIncrement();
        myScrollBar.setValue(myScrollBar.getValue() + scrollAmount);
        // consume the event to prevent the popup from handling it, which has the adverse effect of closing the popup.
        e.consume();
      }
    };
    addMouseWheelListener(myListener);
    add(myScrollBar);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setUI(new CommonPopupMenuUI());
  }

  @Override
  public void addSeparator() {
    add(new CommonSeparator());
  }

  @Override
  public void removeAll() {
    // Do not remove Scrollbar since that is hidden for the caller.
    for (int i = getComponentCount() - 1; i >= 1; i--) {
      remove(i);
    }
  }

  @Override
  public void show(Component invoker, int x, int y) {
    int screenHeight = getScreenHeight(invoker);
    myScrollBar.setVisible(getPreferredPopupHeight() > screenHeight);

    if (myScrollBar.isVisible()) {
      // If scrollbar is visible. then the popup takes the screen's full height.
      // Here we initialize the parameters for the scrollbar and adjust the width of the popup to account for the scrollbar's thumb.
      Insets insets = getInsets();
      int height = screenHeight;
      int itemHeight = 0;
      int width = 0;
      int totalHeight = 0;
      for (Component child : getComponents()) {
        if (child == myScrollBar)  {
          // Skip scrollbar
          continue;
        }
        Dimension preferred = child.getPreferredSize();
        width = Math.max(width, preferred.width);
        totalHeight += preferred.height;
        itemHeight = Math.max(itemHeight, preferred.height);
      }
      width += insets.left + insets.right + myScrollBar.getPreferredSize().width;
      totalHeight += insets.top + insets.bottom;
      myScrollBar.setValues(0, height, 0, totalHeight);
      myScrollBar.setUnitIncrement(itemHeight);
      myScrollBar.setBlockIncrement(height);

      setPopupSize(width, height);
    }
    super.show(invoker, x, y);
  }

  /**
   * @return Calculate the height the popup should take if all the menus were to be rendered.
   */
  private int getPreferredPopupHeight() {
    Insets insets = getInsets();
    int totalHeight = insets.top + insets.bottom;
    for (Component child : getComponents()) {
      if (child == myScrollBar)  {
        // Skip scrollbar
        continue;
      }
      totalHeight += child.getPreferredSize().height;
    }

    return totalHeight;
  }

  private static int getScreenHeight(@NotNull Component target) {
    int screenHeight = Integer.MAX_VALUE;
    // Retrieve maximum screen height. Note that this can be null before the target container gets painted.
    GraphicsConfiguration config = target.getGraphicsConfiguration();
    if (config != null) {
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
      screenHeight = config.getBounds().height - (insets.top + insets.bottom);
    }

    return screenHeight;
  }

  public static class CommonSeparator extends JSeparator {
    @Override
    public void updateUI() {
      super.updateUI();
      setUI(new CommonSeparatorUI());
    }
  }

  /**
   * Custom layout that stacks the menus vertically. If the scrollbar should be present, it is place to the right of all the menu.
   */
  private class ScrollablePopupLayout implements LayoutManager2 {
    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Insets insets = parent.getInsets();
      int screenHeight = getScreenHeight(parent);
      int height = insets.top + insets.bottom;
      int width = 0;
      for (Component child : parent.getComponents()) {
        if (child == myScrollBar) {
          continue;
        }
        Dimension size = child.getPreferredSize();
        height += size.height;
        width = Math.max(width, size.width);
      }

      width += insets.left + insets.right;
      if (myScrollBar.isVisible()) {
        width += myScrollBar.getPreferredSize().width;
      }
      return new Dimension(width, Math.min(height, screenHeight));
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Insets insets = parent.getInsets();
      int screenHeight = getScreenHeight(parent);
      int height = insets.top + insets.bottom;
      int width = 0;
      for (Component child : parent.getComponents()) {
        if (child == myScrollBar) {
          continue;
        }
        Dimension size = child.getMinimumSize();
        height += size.height;
        width = Math.max(width, size.width);
      }

      width += insets.left + insets.right;
      if (myScrollBar.isVisible()) {
        width += myScrollBar.getMinimumSize().width;
      }
      return new Dimension(width, Math.min(height, screenHeight));
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
      return preferredLayoutSize(target);
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = parent.getInsets();
      int startHeight = myScrollBar.isVisible() ? -myScrollBar.getValue() : insets.top;
      int screenHeight = getScreenHeight(parent);

      int maxWidth = 0;
      // All components share the same width, so iterate through them to find the max width first.
      for (Component comp : parent.getComponents()) {
        if (comp == myScrollBar) {
          continue;
        }
        maxWidth = Math.max(maxWidth, comp.getPreferredSize().width);
      }

      for (Component child : parent.getComponents()) {
        Dimension size = child.getPreferredSize();
        if (child == myScrollBar) {
          if (child.isVisible()) {
            child.setBounds(insets.left + maxWidth, insets.top, size.width, screenHeight - insets.top - insets.bottom);
          }
          continue;
        }
        child.setBounds(insets.left, startHeight, maxWidth, size.height);
        startHeight += size.height;
      }
    }

    @Override
    public void invalidateLayout(Container target) {
      // No-op
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
      // No-op
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
      // No-op
    }

    @Override
    public void removeLayoutComponent(Component comp) {
      // No-op
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
      return 0;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
      return 0;
    }
  }
}

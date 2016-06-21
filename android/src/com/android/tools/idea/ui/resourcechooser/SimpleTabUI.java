/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser;

import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.View;
import java.awt.*;

public class SimpleTabUI extends BasicTabbedPaneUI {

  @Override
  protected void installDefaults() {
    super.installDefaults();
    tabInsets = JBUI.insets(8);
    selectedTabPadInsets = JBUI.emptyInsets();
    contentBorderInsets = JBUI.emptyInsets();
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    // dont want tab border
  }

  @Override
  protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    // dont want a background
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

    int thickness = JBUI.scale(1);
    g.setColor(OnePixelDivider.BACKGROUND);

    // use fillRect instead of drawLine with thickness as drawLine has bugs on OS X retina
    switch (tabPlacement) {
      case LEFT:
        x += calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
        g.fillRect(x - thickness, y, thickness, h);
        break;
      case RIGHT:
        w -= calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
        g.fillRect(x + w, y, thickness, h);
        break;
      case BOTTOM:
        h -= calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        g.fillRect(x, y + h, w, thickness);
        break;
      case TOP:
      default:
        y += calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        g.fillRect(x, y - thickness, w, thickness);
    }
  }

  @Override
  protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
    return super.getTabLabelShiftX(tabPlacement, tabIndex, false);
  }

  @Override
  protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
    return super.getTabLabelShiftY(tabPlacement, tabIndex, false);
  }

  @Override
  protected void layoutLabel(int tabPlacement,
                             FontMetrics metrics, int tabIndex,
                             String title, Icon icon,
                             Rectangle tabRect, Rectangle iconRect,
                             Rectangle textRect, boolean isSelected) {
    textRect.x = textRect.y = iconRect.x = iconRect.y = 0;

    View v = getTextViewForTab(tabIndex);
    if (v != null) {
      tabPane.putClientProperty("html", v);
    }

    // CHANGE FROM DEFAULT: take tab insets into account
    Insets insets = getTabInsets(tabPlacement, tabIndex);
    tabRect = new Rectangle(tabRect);
    tabRect.x += insets.left;
    tabRect.y += insets.top;
    tabRect.width = tabRect.width - insets.left - insets.right;
    tabRect.height = tabRect.height - insets.top - insets.bottom;

    SwingUtilities.layoutCompoundLabel(tabPane,
                                       metrics, title, icon,
                                       SwingConstants.CENTER,
                                       SwingConstants.LEADING, // CHANGE FROM DEFAULT
                                       SwingConstants.CENTER,
                                       SwingConstants.TRAILING,
                                       tabRect,
                                       iconRect,
                                       textRect,
                                       textIconGap);

    tabPane.putClientProperty("html", null);

    int xNudge = getTabLabelShiftX(tabPlacement, tabIndex, isSelected);
    int yNudge = getTabLabelShiftY(tabPlacement, tabIndex, isSelected);
    iconRect.x += xNudge;
    iconRect.y += yNudge;
    textRect.x += xNudge;
    textRect.y += yNudge;
  }

  // Appears to be based on http://stackoverflow.com/questions/7903657/basictabbedpaneui-paint-html-text

  @Override
  protected void paintText(Graphics g, int tabPlacement,
                           Font font, FontMetrics metrics, int tabIndex,
                           String title, Rectangle textRect,
                           boolean isSelected) {

    g.setFont(font);

    View v = getTextViewForTab(tabIndex);
    if (v != null) {
      // html
      v.paint(g, textRect);
    }
    else {
      // plain text
      int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);

      if (tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
        Color fg = tabPane.getForegroundAt(tabIndex);
        if (isSelected && (fg instanceof UIResource)) {
          Color selectedFG = JBColor.BLUE; // CHANGE FROM DEFAULT
          if (selectedFG != null) {
            fg = selectedFG;
          }
        }
        g.setColor(fg);
        SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                  title, mnemIndex,
                                                  textRect.x, textRect.y + metrics.getAscent());
      }
      else { // tab disabled
        g.setColor(tabPane.getBackgroundAt(tabIndex).brighter());
        SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                  title, mnemIndex,
                                                  textRect.x, textRect.y + metrics.getAscent());
        g.setColor(tabPane.getBackgroundAt(tabIndex).darker());
        SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                  title, mnemIndex,
                                                  textRect.x - 1, textRect.y + metrics.getAscent() - 1);
      }
    }
  }
}

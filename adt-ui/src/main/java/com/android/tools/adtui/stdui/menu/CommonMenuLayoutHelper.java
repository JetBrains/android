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

import sun.swing.MenuItemLayoutHelper;

import javax.swing.*;
import java.awt.*;

/**
 * Allows the caller to explicitly specify the leading gap and after checked icon gap, and have the layout logic respect those parameters
 * (The base class implementation resorts to using the text-icon gap instead if the leading/checked icon gaps are zeros, which introduces
 * extra paddings).
 */
public class CommonMenuLayoutHelper extends MenuItemLayoutHelper {

  private int myLeadingGap;
  private int myCheckedIconGap;

  public CommonMenuLayoutHelper(JMenuItem menuItem,
                                Icon checkIcon,
                                Icon arrowIcon,
                                Rectangle viewRect,
                                int textIconGap,
                                int leadingGap,
                                int checkedIconGap,
                                String acceleratorDelimiter,
                                boolean isLeftToRight,
                                Font menuFont,
                                Font acceleratorFont,
                                boolean useCheckAndArrow,
                                String propertyPrefix) {
    super(menuItem, checkIcon, arrowIcon, viewRect, textIconGap, acceleratorDelimiter, isLeftToRight, menuFont, acceleratorFont,
          useCheckAndArrow, propertyPrefix);

    myLeadingGap = leadingGap;
    myCheckedIconGap = checkedIconGap;
  }

  @Override
  public int getLeadingGap() {
    return myLeadingGap;
  }

  @Override
  public int getAfterCheckIconGap() {
    return myCheckedIconGap;
  }

  @Override
  public LayoutResult layoutMenuItem() {
    MenuItemLayoutHelper.LayoutResult result = super.layoutMenuItem();
    // STUDIO customization - ignore non-column layout at the moment.
    if (isLeftToRight()) {
      doLTRColumnLayout(result, getLTRColumnAlignment());
    }
    else {
      doRTLColumnLayout(result, getRTLColumnAlignment());
    }

    //if (isColumnLayout()) {
    //  if (isLeftToRight()) {
    //    doLTRColumnLayout(var1, getLTRColumnAlignment());
    //  }
    //  else {
    //    doRTLColumnLayout(var1, getRTLColumnAlignment());
    //  }
    //}
    //else if (isLeftToRight()) {
    //  doLTRComplexLayout(var1, getLTRColumnAlignment());
    //}
    //else {
    //  doRTLComplexLayout(var1, getRTLColumnAlignment());
    //}
    return result;
  }

  private void doLTRColumnLayout(MenuItemLayoutHelper.LayoutResult layoutResult, MenuItemLayoutHelper.ColumnAlignment columnAlignment) {
    layoutResult.getIconRect().width = getIconSize().getMaxWidth();
    layoutResult.getTextRect().width = getTextSize().getMaxWidth();
    calcXPositionsLTR(getViewRect().x, getLeadingGap(), getGap(), layoutResult.getCheckRect(), layoutResult.getIconRect(),
                      layoutResult.getTextRect());
    Rectangle tmp;
    if (layoutResult.getCheckRect().width > 0) {
      tmp = layoutResult.getIconRect();
      tmp.x += getAfterCheckIconGap() - getGap();
      tmp = layoutResult.getTextRect();
      tmp.x += getAfterCheckIconGap() - getGap();
    }

    calcXPositionsRTL(getViewRect().x + getViewRect().width, getLeadingGap(), getGap(), layoutResult.getArrowRect(),
                      layoutResult.getAccRect());
    int textStartX = layoutResult.getTextRect().x - getViewRect().x;
    if (textStartX < getMinTextOffset()) {
      Rectangle textRect = layoutResult.getTextRect();
      textRect.x += getMinTextOffset() - textStartX;
    }

    alignRects(layoutResult, columnAlignment);
    // STUDIO Customization - already done in the base class.
    //calcTextAndIconYPositions(layoutResult);
    layoutResult.setLabelRect(layoutResult.getTextRect().union(layoutResult.getIconRect()));
  }

  private void doRTLColumnLayout(MenuItemLayoutHelper.LayoutResult layoutResult, MenuItemLayoutHelper.ColumnAlignment columnAlignment) {
    layoutResult.getIconRect().width = getIconSize().getMaxWidth();
    layoutResult.getTextRect().width = getTextSize().getMaxWidth();
    calcXPositionsRTL(getViewRect().x + getViewRect().width, getLeadingGap(), getGap(), layoutResult.getCheckRect(),
                      layoutResult.getIconRect(),
                      layoutResult.getTextRect());
    Rectangle tmp;
    if (layoutResult.getCheckRect().width > 0) {
      tmp = layoutResult.getIconRect();
      tmp.x -= getAfterCheckIconGap() - getGap();
      tmp = layoutResult.getTextRect();
      tmp.x -= getAfterCheckIconGap() - getGap();
    }

    calcXPositionsLTR(getViewRect().x, getLeadingGap(), getGap(), layoutResult.getArrowRect(), layoutResult.getAccRect());
    int textStartX = getViewRect().x + getViewRect().width - (layoutResult.getTextRect().x + layoutResult.getTextRect().width);
    if (textStartX < getMinTextOffset()) {
      Rectangle textRect = layoutResult.getTextRect();
      textRect.x -= getMinTextOffset() - textStartX;
    }

    alignRects(layoutResult, columnAlignment);
    // STUDIO Customization - already done in the base class.
    //calcTextAndIconYPositions(layoutResult);
    layoutResult.setLabelRect(layoutResult.getTextRect().union(layoutResult.getIconRect()));
  }

  private void alignRects(MenuItemLayoutHelper.LayoutResult layoutResult, MenuItemLayoutHelper.ColumnAlignment columnAlignment) {
    alignRect(layoutResult.getCheckRect(), columnAlignment.getCheckAlignment(), getCheckSize().getOrigWidth());
    alignRect(layoutResult.getIconRect(), columnAlignment.getIconAlignment(), getIconSize().getOrigWidth());
    alignRect(layoutResult.getTextRect(), columnAlignment.getTextAlignment(), getTextSize().getOrigWidth());
    alignRect(layoutResult.getAccRect(), columnAlignment.getAccAlignment(), getAccSize().getOrigWidth());
    alignRect(layoutResult.getArrowRect(), columnAlignment.getArrowAlignment(), getArrowSize().getOrigWidth());
  }

  private void alignRect(Rectangle rect, int horizontalAlignment, int width) {
    if (horizontalAlignment == SwingConstants.RIGHT) {
      rect.x = rect.x + rect.width - width;
    }

    rect.width = width;
  }

  private void calcXPositionsLTR(int startX, int leadingGap, int gap, Rectangle... rects) {
    startX += leadingGap;
    for (int i = 0; i < rects.length; ++i) {
      Rectangle rect = rects[i];
      rect.x = startX;
      if (rect.width > 0) {
        startX += rect.width + gap;
      }
    }
  }

  private void calcXPositionsRTL(int startX, int leadingGap, int gap, Rectangle... rects) {
    startX -= leadingGap;
    for (int i = 0; i < rects.length; ++i) {
      Rectangle rect = rects[i];
      rect.x = startX - rect.width;
      if (rect.width > 0) {
        startX -= rect.width + gap;
      }
    }
  }

  public static void applyInsets(Rectangle rect, Insets insets) {
    if (insets == null) {
      return;
    }

    rect.x += insets.left;
    rect.y += insets.top;
    rect.width -= (insets.right + rect.x);
    rect.height -= (insets.bottom + rect.y);
  }
}

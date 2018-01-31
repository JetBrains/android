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

import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.adtui.stdui.StandardDimensions;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.MenuItemLayoutHelper;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.IconUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.*;

/**
 * This implementation does not consider the arrow icon. Also, the layout logic is slightly different than the base class, as we don't take
 * into account the leading gap when calculating bounds (the leading gap introduces additional, undesirable left+right paddings).
 */
public class CommonMenuItemUI extends BasicMenuItemUI {

  @Override
  public void installUI(JComponent component) {
    super.installUI(component);
    component.setOpaque(false);
    Border border = component.getBorder();
    if (border == null || border instanceof UIResource) {
      component
        .setBorder(new BorderUIResource(BorderFactory.createEmptyBorder(0,
                                                                        (int)StandardDimensions.INSTANCE.getMENU_LEFT_PADDING(),
                                                                        0,
                                                                        (int)StandardDimensions.INSTANCE.getMENU_RIGHT_PADDING())));
    }
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();

    defaultTextIconGap = (int)StandardDimensions.INSTANCE.getMENU_ICON_TEXT_GAP();
    if (selectionBackground == null || selectionBackground instanceof UIResource) {
      selectionBackground = new ColorUIResource(StandardColors.SELECTED_BACKGROUND_COLOR);
    }
    if (selectionForeground == null || selectionForeground instanceof UIResource) {
      selectionForeground = new ColorUIResource(StandardColors.SELECTED_TEXT_COLOR);
    }
    if (disabledForeground == null || disabledForeground instanceof UIResource) {
      disabledForeground = new ColorUIResource(StandardColors.DISABLED_TEXT_COLOR);
    }
    if (checkIcon == null || checkIcon instanceof UIResource) {
      checkIcon = new IconUIResource(StudioIcons.Common.CHECKED);
    }
    // CommonMenuItem does not support having submenus.
    arrowIcon = null;
  }

  @Override
  protected Dimension getPreferredMenuItemSize(@NotNull JComponent component,
                                               @Nullable Icon checkIcon,
                                               @Nullable Icon arrowIcon,
                                               int defaultTextIconGap) {
    CommonMenuItem menuItem = (CommonMenuItem)component;
    assert menuItem != null;
    CommonMenuLayoutHelper layoutHelper = new CommonMenuLayoutHelper(menuItem, checkIcon,
                                                                     arrowIcon, MenuItemLayoutHelper.createMaxRect(),
                                                                     defaultTextIconGap,
                                                                     0,
                                                                     (int)StandardDimensions.INSTANCE.getMENU_CHECK_ICON_GAP(),

                                                                     acceleratorDelimiter,
                                                                     // STUDIO customization
                                                                     // BasicGraphicsUtils.isLeftToRight(mi),
                                                                     component.getComponentOrientation().isLeftToRight(),
                                                                     menuItem.getFont(), acceleratorFont,
                                                                     MenuItemLayoutHelper.useCheckAndArrow(this.menuItem),
                                                                     getPropertyPrefix());
    Dimension result = new Dimension();

    // STUDIO customization
    // Calculate the result width
    //result.width = lh.getLeadingGap();
    MenuItemLayoutHelper.addMaxWidth(layoutHelper.getCheckSize(), layoutHelper.getAfterCheckIconGap(), result);
    // Take into account minimal text offset.
    if ((!layoutHelper.isTopLevelMenu()) && (layoutHelper.getMinTextOffset() > 0) && (result.width < layoutHelper.getMinTextOffset())) {
      result.width = layoutHelper.getMinTextOffset();
    }
    MenuItemLayoutHelper.addMaxWidth(layoutHelper.getLabelSize(), layoutHelper.getGap(), result);
    MenuItemLayoutHelper.addMaxWidth(layoutHelper.getAccSize(), layoutHelper.getGap(), result);
    // STUDIO customization
    //MenuItemLayoutHelper.addMaxWidth(lh.getArrowSize(), lh.getGap(), result);

    // Calculate the result height
    result.height = CommonMenuLayoutHelper.max(layoutHelper.getCheckSize().getHeight(),
                                               layoutHelper.getLabelSize().getHeight(),
                                               layoutHelper.getAccSize().getHeight(),
                                               layoutHelper.getArrowSize().getHeight(),
                                               // STUDIO customization
                                               // Make the menu at least as short as the spec height
                                               (int)StandardDimensions.INSTANCE.getMENU_HEIGHT());

    // Take into account menu item insets
    Insets insets = layoutHelper.getMenuItem().getInsets();
    if (insets != null) {
      result.width += insets.left + insets.right;
      result.height += insets.top + insets.bottom;
    }

    // if the width is even, bump it up one. This is critical
    // for the focus dash line to draw properly
    if (result.width % 2 == 0) {
      result.width++;
    }

    // if the height is even, bump it up one. This is critical
    // for the text to center properly
    if (result.height % 2 == 0 && Boolean.TRUE != UIManager.get(getPropertyPrefix() + ".evenHeight")) {
      result.height++;
    }

    return result;
  }

  @Override
  protected void paintMenuItem(@NotNull Graphics graphics,
                               @NotNull JComponent component,
                               @Nullable Icon checkIcon,
                               @Nullable Icon arrowIcon,
                               @Nullable Color background,
                               @Nullable Color foreground,
                               int defaultTextIconGap) {
    // Save original graphics font and color
    Font holdf = graphics.getFont();
    Color holdc = graphics.getColor();

    CommonMenuItem menuItem = (CommonMenuItem)component;
    graphics.setFont(menuItem.getFont());

    Rectangle viewRect = new Rectangle(0, 0, menuItem.getWidth(), menuItem.getHeight());
    CommonMenuLayoutHelper.applyInsets(viewRect, menuItem.getInsets());

    CommonMenuLayoutHelper layoutHelper = new CommonMenuLayoutHelper(menuItem, checkIcon,
                                                                     arrowIcon, viewRect, defaultTextIconGap, 0,
                                                                     (int)StandardDimensions.INSTANCE.getMENU_CHECK_ICON_GAP(),
                                                                     acceleratorDelimiter,
                                                                     // STUDIO customization
                                                                     // BasicGraphicsUtils.isLeftToRight(mi),
                                                                     menuItem.getComponentOrientation().isLeftToRight(),
                                                                     menuItem.getFont(),
                                                                     acceleratorFont, MenuItemLayoutHelper.useCheckAndArrow(this.menuItem),
                                                                     getPropertyPrefix());
    MenuItemLayoutHelper.LayoutResult layoutResult = layoutHelper.layoutMenuItem();

    paintBackground(graphics, menuItem, background);
    // STUDIO customization
    if (menuItem.isActionSelected()) {
      CommonMenuPaintHelper.paintCheckIcon(graphics, layoutHelper, layoutResult, holdc, foreground);
    }
    CommonMenuPaintHelper.paintIcon(graphics, layoutHelper, layoutResult, holdc);
    // STUDIO customization
    //paintText(g, lh, lr);
    if (!layoutHelper.getText().isEmpty()) {
      paintText(graphics, layoutHelper.getMenuItem(), layoutResult.getTextRect(), layoutHelper.getText());
    }
    // STUDIO customization - shared acc paint logic
    CommonMenuPaintHelper
      .paintAccText(graphics, layoutHelper, layoutResult, disabledForeground, acceleratorForeground, acceleratorSelectionForeground);
    // STUDIO customization - no arrow icon
    //paintArrowIcon(g, lh, lr, foreground);

    // Restore original graphics font and color
    graphics.setColor(holdc);
    graphics.setFont(holdf);
  }
}

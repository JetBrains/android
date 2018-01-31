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
import com.intellij.util.ui.JBUI;
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
import javax.swing.plaf.basic.BasicMenuUI;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Differing from the base class, the layout logic does not take into account the leading gap when calculating bounds (the leading gap
 * introduces additional, undesirable left+right paddings).
 */
public class CommonMenuUI extends BasicMenuUI {

  @NotNull
  private static final IconUIResource ARROW_ICON =
    new IconUIResource(new ImageIcon(new BufferedImage(JBUI.scale(16), JBUI.scale(16), BufferedImage.TYPE_INT_ARGB)));

  @Override
  public void installUI(@NotNull JComponent component) {
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
    if (arrowIcon == null || arrowIcon instanceof UIResource) {
      // Use the dummy arrow image for layout purposes, during paint, the arrow is drawn manually instead.
      arrowIcon = ARROW_ICON;
    }
  }

  @Override
  protected Dimension getPreferredMenuItemSize(@NotNull JComponent component,
                                               @Nullable Icon checkIcon,
                                               @Nullable Icon arrowIcon,
                                               int defaultTextIconGap) {
    CommonMenu menu = (CommonMenu)component;
    assert menu != null;
    CommonMenuLayoutHelper layoutHelper = new CommonMenuLayoutHelper(menu, checkIcon,
                                                                     arrowIcon, MenuItemLayoutHelper.createMaxRect(),
                                                                     defaultTextIconGap,
                                                                     0,
                                                                     (int)StandardDimensions.INSTANCE.getMENU_CHECK_ICON_GAP(),
                                                                     acceleratorDelimiter,
                                                                     // STUDIO customization
                                                                     // BasicGraphicsUtils.isLeftToRight(mi),
                                                                     component.getComponentOrientation().isLeftToRight(),
                                                                     menu.getFont(), acceleratorFont,
                                                                     MenuItemLayoutHelper.useCheckAndArrow(menuItem),
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
    MenuItemLayoutHelper.addMaxWidth(layoutHelper.getArrowSize(), 0, result);

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

    CommonMenu menu = (CommonMenu)component;
    graphics.setFont(menu.getFont());

    Rectangle viewRect = new Rectangle(0, 0, menu.getWidth(), menu.getHeight());
    CommonMenuLayoutHelper.applyInsets(viewRect, menu.getInsets());

    CommonMenuLayoutHelper layoutHelper = new CommonMenuLayoutHelper(menu, checkIcon,
                                                                     arrowIcon, viewRect, defaultTextIconGap, 0,
                                                                     (int)StandardDimensions.INSTANCE.getMENU_CHECK_ICON_GAP(),
                                                                     acceleratorDelimiter,
                                                                     // STUDIO customization
                                                                     // BasicGraphicsUtils.isLeftToRight(mi),
                                                                     menu.getComponentOrientation().isLeftToRight(),
                                                                     menu.getFont(),
                                                                     acceleratorFont, MenuItemLayoutHelper.useCheckAndArrow(menuItem),
                                                                     getPropertyPrefix());
    MenuItemLayoutHelper.LayoutResult layoutResult = layoutHelper.layoutMenuItem();

    paintBackground(graphics, menu, background);
    // STUDIO customization
    if (menu.isActionSelected()) {
      CommonMenuPaintHelper.paintCheckIcon(graphics, layoutHelper, layoutResult, holdc, foreground);
    }
    CommonMenuPaintHelper.paintIcon(graphics, layoutHelper, layoutResult, holdc);
    // STUDIO customization
    //paintText(g, lh, lr);
    if (!layoutHelper.getText().equals("")) {
      paintText(graphics, layoutHelper.getMenuItem(), layoutResult.getTextRect(), layoutHelper.getText());
    }
    // STUDIO customization - shared acc paint logic
    CommonMenuPaintHelper
      .paintAccText(graphics, layoutHelper, layoutResult, disabledForeground, acceleratorForeground, acceleratorSelectionForeground);
    if (arrowIcon == ARROW_ICON) {
      // STUDIO customization - shared custom arrow painting
      CommonMenuPaintHelper.paintArrowIconCustom(graphics, layoutHelper, layoutResult);
    }
    else {
      CommonMenuPaintHelper.paintArrowIcon(graphics, layoutHelper, layoutResult, foreground);
    }

    // Restore original graphics font and color
    graphics.setColor(holdc);
    graphics.setFont(holdf);
  }
}


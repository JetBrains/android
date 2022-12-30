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

import com.android.tools.adtui.stdui.GraphicsUtilKt;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.adtui.stdui.StandardDimensions;
import com.intellij.util.ui.UIUtilities;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JMenu;
import org.jetbrains.annotations.NotNull;
import sun.swing.MenuItemLayoutHelper;

/**
 * The paint methods here are extract from the private method equivalents inside {@link javax.swing.plaf.basic.BasicMenuUI} and
 * {@link javax.swing.plaf.basic.BasicMenuItemUI}.
 */
public final class CommonMenuPaintHelper {

  private CommonMenuPaintHelper() {
  }

  public static void paintIcon(@NotNull Graphics graphics,
                               @NotNull MenuItemLayoutHelper layoutHelper,
                               @NotNull MenuItemLayoutHelper.LayoutResult layoutResult,
                               @NotNull Color holdColor) {
    if (layoutHelper.getIcon() == null) {
      return;
    }

    Icon icon;
    ButtonModel model = layoutHelper.getMenuItem().getModel();
    if (!model.isEnabled()) {
      icon = layoutHelper.getMenuItem().getDisabledIcon();
    }
    else if (model.isPressed() && model.isArmed()) {
      icon = layoutHelper.getMenuItem().getPressedIcon();
      if (icon == null) {
        // Use default icon
        icon = layoutHelper.getMenuItem().getIcon();
      }
    }
    else {
      icon = layoutHelper.getMenuItem().getIcon();
    }

    if (icon != null) {
      icon.paintIcon(layoutHelper.getMenuItem(), graphics, layoutResult.getIconRect().x,
                     layoutResult.getIconRect().y);
      graphics.setColor(holdColor);
    }
  }

  public static void paintAccText(@NotNull Graphics graphics, @NotNull MenuItemLayoutHelper layoutHelper,
                                  @NotNull MenuItemLayoutHelper.LayoutResult layoutResult,
                                  @NotNull Color disabledForeground,
                                  @NotNull Color acceleratorForeground,
                                  @NotNull Color acceleratorSelectionForeground) {
    if (layoutHelper.getAccText().isEmpty()) {
      return;
    }

    ButtonModel model = layoutHelper.getMenuItem().getModel();
    graphics.setFont(layoutHelper.getAccFontMetrics().getFont());
    if (!model.isEnabled()) {
      // *** paint the accText disabled
      if (disabledForeground != null) {
        graphics.setColor(disabledForeground);
        UIUtilities.drawString(layoutHelper.getMenuItem(), graphics,
                                   layoutHelper.getAccText(), layoutResult.getAccRect().x,
                                   layoutResult.getAccRect().y + layoutHelper.getAccFontMetrics().getAscent());
      }
      else {
        graphics.setColor(layoutHelper.getMenuItem().getBackground().brighter());
        UIUtilities.drawString(layoutHelper.getMenuItem(), graphics,
                                   layoutHelper.getAccText(), layoutResult.getAccRect().x,
                                   layoutResult.getAccRect().y + layoutHelper.getAccFontMetrics().getAscent());
        graphics.setColor(layoutHelper.getMenuItem().getBackground().darker());
        UIUtilities.drawString(layoutHelper.getMenuItem(), graphics,
                                   layoutHelper.getAccText(), layoutResult.getAccRect().x - 1,
                                   layoutResult.getAccRect().y + layoutHelper.getFontMetrics().getAscent() - 1);
      }
    }
    else {
      // *** paint the accText normally
      if (model.isArmed()
          || (layoutHelper.getMenuItem() instanceof JMenu
              && model.isSelected())) {
        graphics.setColor(acceleratorSelectionForeground);
      }
      else {
        graphics.setColor(acceleratorForeground);
      }
      UIUtilities.drawString(layoutHelper.getMenuItem(), graphics, layoutHelper.getAccText(),
                                 layoutResult.getAccRect().x, layoutResult.getAccRect().y +
                                                              layoutHelper.getAccFontMetrics().getAscent());
    }
  }

  public static void paintArrowIcon(@NotNull Graphics graphics, @NotNull MenuItemLayoutHelper layoutHelper,
                                    @NotNull MenuItemLayoutHelper.LayoutResult layoutResult,
                                    @NotNull Color foreground) {
    if (layoutHelper.getArrowIcon() != null) {
      ButtonModel model = layoutHelper.getMenuItem().getModel();
      if (model.isArmed() || (layoutHelper.getMenuItem() instanceof JMenu && model.isSelected())) {
        graphics.setColor(foreground);
      }
      if (layoutHelper.useCheckAndArrow()) {
        layoutHelper.getArrowIcon().paintIcon(layoutHelper.getMenuItem(), graphics,
                                              layoutResult.getArrowRect().x, layoutResult.getArrowRect().y);
      }
    }
  }

  public static void paintArrowIconCustom(@NotNull Graphics graphics, @NotNull MenuItemLayoutHelper layoutHelper,
                                          @NotNull MenuItemLayoutHelper.LayoutResult layoutResult) {
    if (layoutHelper.getArrowIcon() != null && layoutHelper.useCheckAndArrow()) {
      int arrowWidth = (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_WIDTH();
      int arrowHeight = (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_HEIGHT();
      Graphics2D g2d = (Graphics2D)graphics;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


      Rectangle rect = layoutResult.getArrowRect();
      float x = rect.x + (rect.width - arrowHeight);
      float y = rect.y + rect.height / 2f;
      Path2D.Float triangle = new Path2D.Float();
      triangle.moveTo(x + arrowHeight, y);
      triangle.lineTo(x, y - arrowWidth / 2f);
      triangle.lineTo(x, y + arrowWidth / 2f);
      triangle.lineTo(x + arrowHeight, y);
      GraphicsUtilKt.setColorAndAlpha(g2d, StandardColors.DROPDOWN_ARROW_COLOR);
      g2d.fill(triangle);
    }
  }

  public static void paintCheckIcon(@NotNull Graphics graphics, @NotNull MenuItemLayoutHelper layoutHelper,
                                    @NotNull MenuItemLayoutHelper.LayoutResult layoutResult,
                                    @NotNull Color holdColor, @NotNull Color foreground) {
    if (layoutHelper.getCheckIcon() != null) {
      ButtonModel model = layoutHelper.getMenuItem().getModel();
      if (model.isArmed() || (layoutHelper.getMenuItem() instanceof JMenu
                              && model.isSelected())) {
        graphics.setColor(foreground);
      }
      else {
        graphics.setColor(holdColor);
      }
      if (layoutHelper.useCheckAndArrow()) {
        layoutHelper.getCheckIcon().paintIcon(layoutHelper.getMenuItem(), graphics,
                                              layoutResult.getCheckRect().x, layoutResult.getCheckRect().y);
      }
      graphics.setColor(holdColor);
    }
  }
}

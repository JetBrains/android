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
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import org.jetbrains.annotations.NotNull;

/**
 * Handles hover state, sizing and rendering of the dropdown arrow if it should be present.
 */
public class CommonDropDownButtonUI extends BasicButtonUI {
  private static final int ARROW_REGION_WIDTH = (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_WIDTH() +
                                                (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_HORIZONTAL_PADDING() * 2;
  private static final int ARROW_REGION_HEIGHT = (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_HEIGHT() +
                                                 (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_VERTICAL_PADDING_TOP() +
                                                 (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_VERTICAL_PADDING_BOTTOM();

  @NotNull private final MouseAdapter myAdapter;
  private boolean myHover;

  public CommonDropDownButtonUI() {
    myAdapter = new MouseAdapter() {
      @Override
      public void mouseEntered(@NotNull MouseEvent event) {
        myHover = true;
        event.getComponent().repaint();
      }

      @Override
      public void mouseExited(@NotNull MouseEvent event) {
        myHover = false;
        event.getComponent().repaint();
      }
    };
  }

  @Override
  protected void installDefaults(@NotNull AbstractButton button) {
    super.installDefaults(button);
    LookAndFeel.installProperty(button, "opaque", false);
    Border border = button.getBorder();
    if (border == null || border instanceof UIResource) {
      button.setBorder(new BorderUIResource(JBUI.Borders.empty(4)));
    }
  }

  @Override
  protected void installListeners(@NotNull AbstractButton button) {
    super.installListeners(button);
    button.addMouseListener(myAdapter);
  }

  @Override
  protected void uninstallListeners(@NotNull AbstractButton button) {
    super.uninstallListeners(button);
    button.removeMouseListener(myAdapter);
  }

  @Override
  public Dimension getMinimumSize(@NotNull JComponent component) {
    return getPreferredSize(component);
  }

  @Override
  public Dimension getPreferredSize(@NotNull JComponent component) {
    Dimension dim = super.getPreferredSize(component);

    CommonDropDownButton dropdown = (CommonDropDownButton)component;
    if (dropdown.getAction().getShowExpandArrow()) {
      dim = new Dimension(dim.width + ARROW_REGION_WIDTH, Math.max(dim.height, ARROW_REGION_HEIGHT));
    }

    return dim;
  }

  @Override
  public void paint(@NotNull Graphics graphics, @NotNull JComponent component) {
    Graphics2D g2d = (Graphics2D)graphics;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    CommonDropDownButton button = (CommonDropDownButton)component;
    if ((myHover && button.isEnabled()) || button.isSelected()) {
      GraphicsUtilKt.paintBackground(graphics, component);
    }
    super.paint(graphics, component);

    if (button.getAction().getShowExpandArrow()) {
      Dimension dim = component.getSize();
      int arrowWidth = (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_WIDTH();
      int arrowHeight = (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_HEIGHT();
      int arrowRegionWidth = arrowWidth + (int)StandardDimensions.INSTANCE.getDROPDOWN_ARROW_HORIZONTAL_PADDING() * 2;
      float x = dim.width - arrowRegionWidth / 2f;
      float y = (dim.height - arrowHeight) / 2f;
      Path2D.Float triangle = new Path2D.Float();
      triangle.moveTo(x, y + arrowHeight);
      triangle.lineTo(x - arrowWidth / 2f, y);
      triangle.lineTo(x + arrowWidth / 2f, y);
      triangle.lineTo(x, y + arrowHeight);
      GraphicsUtilKt.setColorAndAlpha(g2d, StandardColors.DROPDOWN_ARROW_COLOR);
      g2d.fill(triangle);
    }

    if (button.hasFocus()) {
      DarculaUIUtil.paintFocusBorder(g2d, component.getWidth(), component.getHeight(), 0f, true);
    }
  }

  @Override
  protected void paintIcon(@NotNull Graphics graphics, @NotNull JComponent component, @NotNull Rectangle iconRect) {
    CommonDropDownButton button = (CommonDropDownButton)component;
    if (button.getAction().getShowExpandArrow()) {
      // Offset the text rect to reserve space for the arrow
      iconRect.x -= ARROW_REGION_WIDTH / 2;
    }
    super.paintIcon(graphics, component, iconRect);
  }

  @Override
  protected void paintText(@NotNull Graphics graphics,
                           @NotNull AbstractButton component,
                           @NotNull Rectangle textRect,
                           @NotNull String text) {
    CommonDropDownButton button = (CommonDropDownButton)component;
    if (button.getAction().getShowExpandArrow()) {
      // Offset the text rect to reserve space for the arrow
      textRect.x -= ARROW_REGION_WIDTH / 2;
    }
    super.paintText(graphics, button, textRect, text);
  }
}
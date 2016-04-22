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
package com.android.tools.idea.assistant.view;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * Button UI to address edge cases where default treatment is innapropriate (primarily due to our lighter background than most buttons
 * are placed over. This is a fork of {@see DarculaButtonUI} with the colors changed.
 */
public class StatefulButtonUI extends BasicButtonUI {

  // Values stolen from IntelliJ and Darcula themes.
  private static final JBColor DISABLED_TEXT = new JBColor(0x999999, 0x777777);
  private static final JBColor COLOR_1 = new JBColor(0xeeeeee, 0x555a5c);
  private static final JBColor COLOR_2 = new JBColor(0xc0c0c0, 0x414648);
  private static final JBColor SELECTION_COLOR_1 = new JBColor(0x4985e4, 0x384f6b);
  private static final JBColor SELECTION_COLOR_2 = new JBColor(0x4074c9, 0x233143);
  private static final JBColor SELECTED_BUTTON_FOREGROUND = new JBColor(0xf0f0f0, 0xbbbbbb);
  private static final JBColor DISABLED_TEXT_SHADOW = new JBColor(0xffffff, 0x00000000);

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new StatefulButtonUI();
  }

  public static boolean isSquare(Component c) {
    return c instanceof JButton && "square".equals(((JButton)c).getClientProperty("JButton.buttonType"));
  }

  public static boolean isDefaultButton(JComponent c) {
    return c instanceof JButton && ((JButton)c).isDefaultButton();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    int w = c.getWidth();
    int h = c.getHeight();
    if (isHelpButton(c)) {
      ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));
      int off = JBUI.scale(22);
      int x = (w - off) / 2;
      int y = (h - off) / 2;
      g.fillOval(x, y, off, off);
      AllIcons.Actions.Help.paintIcon(c, g, x + JBUI.scale(3), y + JBUI.scale(3));
    }
    else {
      final Border border = c.getBorder();
      final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      final boolean square = isSquare(c);
      if (c.isEnabled() && border != null) {
        final Insets ins = border.getBorderInsets(c);
        final int yOff = (ins.top + ins.bottom) / 4;
        if (!square) {
          if (isDefaultButton(c)) {
            ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, getSelectedButtonColor1(), 0, h, getSelectedButtonColor2()));
          }
          else {
            ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));
          }
        }
        int rad = JBUI.scale(square ? 3 : 5);
        g.fillRoundRect(JBUI.scale(square ? 2 : 4), yOff, w - 2 * JBUI.scale(4), h - 2 * yOff, rad, rad);
      }
      config.restore();
      super.paint(g, c);
    }
  }

  @Override
  protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
    if (isHelpButton(c)) {
      return;
    }

    AbstractButton button = (AbstractButton)c;
    ButtonModel model = button.getModel();
    Color fg = button.getForeground();
    if (fg instanceof UIResource && isDefaultButton(button)) {
      final Color selectedFg = SELECTED_BUTTON_FOREGROUND;
      if (selectedFg != null) {
        fg = selectedFg;
      }
    }
    g.setColor(fg);

    FontMetrics metrics = SwingUtilities2.getFontMetrics(c, g);
    int mnemonicIndex = DarculaLaf.isAltPressed() ? button.getDisplayedMnemonicIndex() : -1;
    if (model.isEnabled()) {

      SwingUtilities2.drawStringUnderlineCharAt(c, g, text, mnemonicIndex,
                                                textRect.x + getTextShiftOffset(),
                                                textRect.y + metrics.getAscent() + getTextShiftOffset());
    }
    else {
      paintDisabledText(g, text, c, textRect, metrics);
    }
  }

  protected void paintDisabledText(Graphics g, String text, JComponent c, Rectangle textRect, FontMetrics metrics) {
    g.setColor(DISABLED_TEXT_SHADOW);
    SwingUtilities2.drawStringUnderlineCharAt(c, g, text, -1,
                                              textRect.x + getTextShiftOffset() + 1,
                                              textRect.y + metrics.getAscent() + getTextShiftOffset() + 1);
    g.setColor(DISABLED_TEXT);
    SwingUtilities2.drawStringUnderlineCharAt(c, g, text, -1,
                                              textRect.x + getTextShiftOffset(),
                                              textRect.y + metrics.getAscent() + getTextShiftOffset());
  }

  @Override
  protected void paintIcon(Graphics g, JComponent c, Rectangle iconRect) {
    Border border = c.getBorder();
    if (border != null && isSquare(c)) {
      int xOff = 1;
      Insets ins = border.getBorderInsets(c);
      int yOff = (ins.top + ins.bottom) / 4;
      Rectangle iconRect2 = new Rectangle(iconRect);
      iconRect2.x += xOff;
      iconRect2.y += yOff;
      super.paintIcon(g, c, iconRect2);
    }
    else {
      super.paintIcon(g, c, iconRect);
    }
  }

  @Override
  public void update(Graphics g, JComponent c) {
    super.update(g, c);
    if (isDefaultButton(c) && !SystemInfo.isMac) {
      if (!c.getFont().isBold()) {
        c.setFont(new FontUIResource(c.getFont().deriveFont(Font.BOLD)));
      }
    }
  }

  public static boolean isHelpButton(JComponent button) {
    return SystemInfo.isMac
           && button instanceof JButton
           && "help".equals(button.getClientProperty("JButton.buttonType"));
  }

  protected Color getButtonColor1() {
    return COLOR_1;
  }

  protected Color getButtonColor2() {
    return COLOR_2;
  }

  protected Color getSelectedButtonColor1() {
    return SELECTION_COLOR_1;
  }

  protected Color getSelectedButtonColor2() {
    return SELECTION_COLOR_2;
  }
}


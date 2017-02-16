/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.common;

import com.android.tools.profilers.ProfilerColors;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Borderless button with hover effect used by studio profilers.
 */
public class ProfilerButton extends JButton {

  private static final Color ON_HOVER_COLOR = new Color(0, 0, 0, (int)(0.1 * 255));

  private static final int RADIUS = 8;

  private static final int PADDING = 5;

  public ProfilerButton() {
    setOpaque(false);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        setBackground(ON_HOVER_COLOR);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setBackground(UIUtil.TRANSPARENT_COLOR);
      }
    });
    setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
  }

  @Override
  protected void paintComponent(Graphics g) {
    // As the background has a transparency level, we need to manually add it.
    g.setColor(getBackground());
    g.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS, RADIUS);
    super.paintComponent(g);
  }

  @Override
  public void updateUI() {
    setUI((ButtonUI)MyButtonUI.createUI());
  }

  private static class MyButtonUI extends BasicButtonUI {

    private static MyButtonUI ourInstance;

    public static ComponentUI createUI() {
      if (ourInstance == null) {
        ourInstance = new MyButtonUI();
      }
      return ourInstance;
    }

    @Override
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
      if (b.getModel().isEnabled()) {
        g.setColor(ProfilerColors.BUTTON_TEXT);
      } else {
        // The disabled button label text color in BasicButtonUI is the same as IntelliJ's toolbar,
        // where most of the profiler buttons are located at. As a result, disabled buttons might
        // get their text invisible and that's why we set a custom color here.
        g.setColor(ProfilerColors.DISABLED_BUTTON_TEXT);
      }
      // BasicButtonUI default behavior has some logic to underline a character that might be used as shortcut.
      // We don't have shortcuts for now, so we simplify the drawing logic by simply drawing the text.
      // TODO: in a future accessibility/keyboard shortcuts pass, review if we need to change this logic.
      FontMetrics fm = SwingUtilities2.getFontMetrics(b, g);
      SwingUtilities2.drawString(b, g, text, textRect.x + getTextShiftOffset(), textRect.y + fm.getAscent());
    }
  }
}

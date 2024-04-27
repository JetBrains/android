/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.ui;

import com.android.tools.adtui.common.StudioColorsKt;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicPanelUI;

/** Sets the UI for toolbars used on the DesignSurface. */
public class DesignSurfaceToolbarUI extends BasicPanelUI {
  private static final int ARC_LENGTH = 5;
  private static final int BORDER_ARC_LENGTH = 8;
  private static final int BORDER_SIZE = 1;
  private static final JBColor DEFAULT_BORDER = JBColor.lightGray;

  @Override
  public void update(Graphics g, JComponent c) {
    Insets insets = c.getInsets();
    Dimension preferredSize = c.getPreferredSize();
    if (insets.left + insets.right == preferredSize.width && insets.top + insets.bottom == preferredSize.height) {
      // Do not paint anything if there's nothing visible.
      return;
    }
    if (c.isOpaque()) {
      int width = c.getWidth();
      int height = c.getHeight();
      Graphics2D g2D = (Graphics2D)g.create();
      g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // TODO: Do shadows instead.
      int borderArcLength = JBUI.scale(BORDER_ARC_LENGTH);
      g2D.setColor(DEFAULT_BORDER);
      g2D.fillRoundRect(0, 0, width, height, borderArcLength, borderArcLength);

      int borderSize = JBUI.scale(BORDER_SIZE);
      int backgroundArcLength = JBUI.scale(ARC_LENGTH);
      g2D.setColor(StudioColorsKt.getPrimaryContentBackground());
      g2D.fillRoundRect(borderSize, borderSize, width - borderSize - borderSize, height - borderSize - borderSize, backgroundArcLength,
                        backgroundArcLength);
      g2D.dispose();
    }
    paint(g, c);
  }

  public static void applyToPanel(JPanel c) {
    c.setUI(new DesignSurfaceToolbarUI());
    c.setOpaque(true);
    c.setBackground(JBColor.white);
  }

  public static JPanel createPanel(JComponent c){
    JPanel container = new JPanel(){
      @Override
      public void updateUI() {
        setUI(new DesignSurfaceToolbarUI());
      }
    };
    container.setOpaque(true);
    container.setBorder(JBUI.Borders.empty(1));
    container.setBackground(JBColor.white);
    container.add(c);
    return container;
  }
}

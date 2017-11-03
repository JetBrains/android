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
package com.android.tools.adtui.flat;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class FlatUiUtils {

  /**
   * Background based on {@link com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook}
   * to match IJ style in toolbars.
   */
  public static void paintBackground(Graphics graphics, JComponent component) {
    Graphics2D g = (Graphics2D)graphics;
    Dimension size = component.getSize();
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    Component opaque = UIUtil.findNearestOpaque(component);
    Color bg = opaque != null ? opaque.getBackground() : component.getBackground();

    RoundRectangle2D.Double rect = new RoundRectangle2D.Double(1, 1, size.width - 3, size.height - 3, 4, 4);
    if (UIUtil.isUnderAquaLookAndFeel() || (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF())) {
      Color darker = ColorUtil.darker(bg, 1);
      g.setColor(darker);
      g.fill(rect);
      g.setColor(Gray.xCC);
      g.draw(rect);
    } else {
      boolean dark = UIUtil.isUnderDarcula();
      Color color = UIUtil.isUnderWin10LookAndFeel() ? Gray.xE6 : dark ? ColorUtil.shift(bg, 1d / 0.7d) : Gray.xD0;
      g.setColor(color);
      g.fill(rect);
      double shift = UIUtil.isUnderDarcula() ? 1 / 0.49 : 0.49;
      g.setColor(ColorUtil.shift(UIUtil.getPanelBackground(), shift));
      g.draw(rect);
    }
    config.restore();
  }
}

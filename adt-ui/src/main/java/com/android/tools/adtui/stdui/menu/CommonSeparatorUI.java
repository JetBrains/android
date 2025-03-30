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
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JSeparator;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicSeparatorUI;

public class CommonSeparatorUI extends BasicSeparatorUI {
  @Override
  protected void installDefaults(JSeparator separator) {
    super.installDefaults(separator);
    LookAndFeel.installProperty(separator, "opaque", Boolean.TRUE);

    Color fgColor = separator.getForeground();
    if (fgColor == null || fgColor instanceof UIResource) {
      separator.setForeground(new ColorUIResource(StandardColors.DISABLED_INNER_BORDER_COLOR));
    }
  }

  @Override
  public void paint(Graphics graphics, JComponent component) {
    Dimension dim = component.getSize();
    graphics.setColor(component.getForeground());
    graphics.drawRect(0, 0, dim.width, dim.height);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return new Dimension(0, JBUI.scale(1));
  }
}

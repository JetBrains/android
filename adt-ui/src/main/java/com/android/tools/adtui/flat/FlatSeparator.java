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

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JComponent;

/**
 * Based on {@link com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.MySeparator}
 * to match IJ style in toolbars.
 */
public class FlatSeparator extends JComponent {
  private final Dimension mySize;

  public FlatSeparator(int width, int height) {
    mySize = JBUI.size(width, height);
  }

  public FlatSeparator() {
    this(6, 24);
  }


  @Override
  public Dimension getPreferredSize() {
    return mySize;
  }

  @Override
  public Dimension getMinimumSize() {
    return mySize;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    final Insets i = getInsets();
    if (UIUtil.isUnderAquaBasedLookAndFeel() || UIUtil.isUnderDarcula()) {
      if (getParent() != null) {
        final JBColor col = new JBColor(Gray._128, Gray._111);
        final Graphics2D g2 = (Graphics2D)g;
        UIUtil.drawDoubleSpaceDottedLine(g2, i.top + 2, getParent().getSize().height - 2 - i.top - i.bottom, 3, col, false);
      }
    }
    else {
      g.setColor(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
      if (getParent() != null) {
        LinePainter2D.paint((Graphics2D)g, 3, 2, 3, getParent().getSize().height - 2);
      }
    }
  }
}

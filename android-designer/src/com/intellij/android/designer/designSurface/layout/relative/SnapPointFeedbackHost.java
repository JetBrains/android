/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface.layout.relative;

import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class SnapPointFeedbackHost extends JComponent {
  public static final int EXPAND_SIZE = 10;
  public static final String KEY = "SnapPointFeedbackHost";

  public static SimpleTextAttributes SNAP_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, new Color(60, 139, 186));

  private final List<Rectangle> myLines = new ArrayList<Rectangle>();
  private final List<Rectangle> myArrows = new ArrayList<Rectangle>();

  public void addHorizontalLine(int x, int y, int length) {
    myLines.add(new Rectangle(x, y, length, -1));
  }

  public void addVerticalLine(int x, int y, int length) {
    myLines.add(new Rectangle(x, y, -1, length));
  }

  public void addHorizontalArrow(int x, int y, int length) {
    myArrows.add(new Rectangle(x, y, length, -1));
  }

  public void addVerticalArrow(int x, int y, int length) {
    myArrows.add(new Rectangle(x, y, -1, length));
  }

  public void clearAll() {
    myLines.clear();
    myArrows.clear();
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x - EXPAND_SIZE, y - EXPAND_SIZE, width + 2 * EXPAND_SIZE, height + 2 * EXPAND_SIZE);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    DesignerGraphics.useStroke(DrawingStyle.GUIDELINE_DASHED_STROKE, g);

    Rectangle bounds = getBounds();

    for (Rectangle line : myLines) {
      int x = line.x - bounds.x;
      int y = line.y - bounds.y;

      if (line.width == -1) {
        g.drawLine(x, y, x, y + line.height);
      }
      else {
        g.drawLine(x, y, x + line.width, y);
      }
    }

    for (Rectangle line : myArrows) {
      int x = line.x - bounds.x;
      int y = line.y - bounds.y;

      if (line.width == -1) {
        DesignerGraphics.drawArrow(DrawingStyle.GUIDELINE, g, x, y + line.height, x, y);
      }
      else {
        DesignerGraphics.drawArrow(DrawingStyle.GUIDELINE, g, x + line.width, y, x, y);
      }
    }
  }
}
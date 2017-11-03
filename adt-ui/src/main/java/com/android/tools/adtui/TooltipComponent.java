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
package com.android.tools.adtui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public final class TooltipComponent extends AnimatedComponent {

  private final Component myComponent;

  @Nullable
  private Point myLastPoint;

  public TooltipComponent(Component component) {
    myComponent = component;
    add(component);
  }

  public void registerListenersOn(Component component) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myLastPoint = null;
        opaqueRepaint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMove(e);
      }

      private void handleMove(MouseEvent e) {
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        opaqueRepaint();
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (myLastPoint == null) {
      myComponent.setVisible(false);
      return;
    }
    myComponent.setVisible(true);

    Dimension size = myComponent.getPreferredSize();
    Dimension minSize = myComponent.getMinimumSize();
    size = new Dimension(Math.max(size.width, minSize.width), Math.max(size.height, minSize.height));

    g.setColor(Color.WHITE);
    int gap = 10;
    int x1 = Math.max(Math.min((myLastPoint.x + gap), dim.width - size.width - gap), 0);
    int y1 = Math.max(Math.min(myLastPoint.y + gap, dim.height - size.height - gap), gap);
    int width = size.width;
    int height = size.height;

    g.fillRect(x1, y1, width, height);

    g.setStroke(new BasicStroke(1.0f));

    int lines = 4;
    int[] alphas = new int[]{40, 30, 20, 10};
    RoundRectangle2D.Float rect = new RoundRectangle2D.Float();
    for (int i = 0; i < lines; i++) {
      g.setColor(new Color(0, 0, 0, alphas[i]));
      rect.setRoundRect(x1 - 1 - i, y1 - 1 - i, width + 1 + i * 2, height + 1 + i * 2, i * 2 + 2, i * 2 + 2);
      g.draw(rect);
    }
    myComponent.setBounds(x1, y1, width, height);
    myComponent.repaint();
  }
}
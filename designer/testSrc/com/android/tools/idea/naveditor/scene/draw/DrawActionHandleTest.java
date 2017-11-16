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
package com.android.tools.idea.naveditor.scene.draw;

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.naveditor.scene.targets.ActionHandleTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.intellij.ui.JBColor;
import junit.framework.TestCase;
import org.mockito.InOrder;

import java.awt.*;

import static org.mockito.Mockito.*;

public class DrawActionHandleTest extends TestCase {
  private static final Color FRAMES_COLOR = JBColor.WHITE;
  private static final Color BACKGROUND_COLOR = JBColor.BLACK;
  private static final int X = 10;
  private static final int Y = 10;
  private static final int INTERVAL = 50;
  private static final int DURATION = 200;

  public void testDrawActionHandle() {
    for (int initialRadius = 0; initialRadius <= ActionHandleTarget.LARGE_RADIUS; initialRadius++) {
      for (int finalRadius = 0; finalRadius <= ActionHandleTarget.LARGE_RADIUS; finalRadius++) {
        drawActionHandleTest(initialRadius, finalRadius);
      }
    }
  }

  private static void drawActionHandleTest(int initialRadius, int finalRadius) {
    ColorSet colorSet = mock(ColorSet.class);
    SceneContext sceneContext = mock(SceneContext.class);
    Graphics2D g = mock(Graphics2D.class);
    DrawActionHandle drawActionHandle = new DrawActionHandle(X, Y, initialRadius, finalRadius, FRAMES_COLOR, DURATION);

    when(colorSet.getBackground()).thenReturn(BACKGROUND_COLOR);
    when(colorSet.getFrames()).thenReturn(FRAMES_COLOR);
    when(sceneContext.getTime()).thenReturn(0L, 50L, 100L, 150L, 200L);
    when(sceneContext.getColorSet()).thenReturn(colorSet);
    when(g.create()).thenReturn(g);

    int delta = (finalRadius - initialRadius);

    int n = DURATION / INTERVAL;

    if (DURATION % INTERVAL != 0) {
      n++;
    }

    for (int i = 0; i <= n; i++) {
      int t = INTERVAL * i;

      int expectedRadius = finalRadius;
      if (t < DURATION) {
        expectedRadius = initialRadius + delta * t / DURATION;
      }

      InOrder inOrder = inOrder(g);
      drawActionHandle.paint(g, sceneContext);

      verifyCircle(inOrder, g, expectedRadius, BACKGROUND_COLOR);
      expectedRadius *= DrawActionHandle.INNER_CIRCLE_FRACTION;
      verifyCircle(inOrder, g, expectedRadius, FRAMES_COLOR);
      expectedRadius -= DrawActionHandle.INNER_CIRCLE_THICKNESS;
      verifyCircle(inOrder, g, expectedRadius, BACKGROUND_COLOR);

      verify(sceneContext, times(Math.min(i + 1, n))).repaint();
    }
  }

  private static void verifyCircle(InOrder inOrder, Graphics2D g, int r, Color color) {
    inOrder.verify(g).setColor(color);
    inOrder.verify(g).fillOval(X - r, Y - r, 2 * r, 2 * r);
  }
}

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

import com.android.tools.idea.uibuilder.scene.SceneContext;
import junit.framework.TestCase;
import org.mockito.InOrder;

import java.awt.*;

import static org.mockito.Mockito.*;

public class DrawActionHandleTest extends TestCase {
  private static final Color BACKGROUND = Color.WHITE;
  private static final Color CENTER = Color.BLACK;
  private static final int X = 10;
  private static final int Y = 10;

  private static final int MAXRADIUS = 10;
  private static final long MAXDURATION = 5;

  public void testDrawActionHandle() {
    for (int initialRadius = 0; initialRadius <= MAXRADIUS; initialRadius++) {
      for (int finalRadius = 0; finalRadius <= MAXRADIUS; finalRadius++) {
        for (int duration = 0; duration <= MAXDURATION; duration++) {
          testDrawActionHandle(initialRadius, finalRadius, duration);
        }
      }
    }
  }

  private static void testDrawActionHandle(int initialRadius, int finalRadius, int duration) {
    SceneContext sceneContext = mock(SceneContext.class);
    Graphics2D g = mock(Graphics2D.class);
    DrawActionHandle drawActionHandle = new DrawActionHandle(X, Y, initialRadius, finalRadius, BACKGROUND, CENTER, duration);

    when(sceneContext.getTime()).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

    for (int i = 0; i <= duration; i++) {
      int expectedRadius = finalRadius;
      if (duration > 0) {
        expectedRadius = initialRadius + (finalRadius - initialRadius) * i / duration;
      }

      InOrder inOrder = inOrder(g);

      drawActionHandle.paint(g, sceneContext);

      verifyCircle(inOrder, g, expectedRadius, BACKGROUND);
      verifyCircle(inOrder, g, expectedRadius - DrawActionHandle.BORDER_THICKNESS, CENTER);

      if (initialRadius == finalRadius) {
        verify(sceneContext, never()).repaint();
        break;
      }
      else {
        verify(sceneContext, times(Math.min(i + 1, duration))).repaint();
      }
    }
  }

  private static void verifyCircle(InOrder inOrder, Graphics2D g, int r, Color color) {
    inOrder.verify(g).setColor(color);
    inOrder.verify(g).fillOval(X - r, Y - r, 2 * r, 2 * r);
  }
}

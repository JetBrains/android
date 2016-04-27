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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.BOUNDS_RECT_DELTA;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DASHED_STROKE;

public class CanvasResizeInteraction extends Interaction {
  private final ScreenView myScreenView;
  private int myCurrentX;
  private int myCurrentY;

  public CanvasResizeInteraction(ScreenView screenView) {
    myScreenView = screenView;
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, int modifiers) {
    super.update(x, y, modifiers);
    myCurrentX = x;
    myCurrentY = y;
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);
    myScreenView.getModel().overrideConfigurationScreenSize(Coordinates.getAndroidX(myScreenView, x),
                                                            Coordinates.getAndroidY(myScreenView, y));
  }

  @Override
  public List<Layer> createOverlays() {
    return Collections.singletonList(new ResizeLayer());
  }

  /**
   * An {@link Layer} for the {@link CanvasResizeInteraction}; paints an outline of what the canvas
   * size will be after resizing.
   */
  private class ResizeLayer extends Layer {
    public ResizeLayer() {
    }

    @Override
    public void create() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      int x = myScreenView.getX();
      int y = myScreenView.getY();

      if (myCurrentX > x && myCurrentY > y) {
        Stroke prevStroke = g2d.getStroke();
        g2d.setColor(new Color(0xFF, 0x99, 0x00, 255));
        g2d.setStroke(DASHED_STROKE);

        g2d.drawLine(x - 1, y - BOUNDS_RECT_DELTA, x - 1, myCurrentY + BOUNDS_RECT_DELTA);
        g2d.drawLine(x - BOUNDS_RECT_DELTA, y - 1, myCurrentX + BOUNDS_RECT_DELTA, y - 1);
        g2d.drawLine(myCurrentX, y - BOUNDS_RECT_DELTA, myCurrentX, myCurrentY + BOUNDS_RECT_DELTA);
        g2d.drawLine(x - BOUNDS_RECT_DELTA, myCurrentY, myCurrentX + BOUNDS_RECT_DELTA, myCurrentY);

        g2d.setStroke(prevStroke);
      }
    }
  }
}

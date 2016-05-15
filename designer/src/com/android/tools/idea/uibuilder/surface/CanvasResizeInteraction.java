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
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.BOUNDS_RECT_DELTA;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DASHED_STROKE;

public class CanvasResizeInteraction extends Interaction {
  private final DesignSurface myDesignSurface;
  private final boolean isPreviewSurface;
  private int myCurrentX;
  private int myCurrentY;

  public CanvasResizeInteraction(DesignSurface designSurface) {
    myDesignSurface = designSurface;
    isPreviewSurface = designSurface.isPreviewSurface();
  }


  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int startMask) {
    super.begin(x, y, startMask);

    ScreenView screenView = myDesignSurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }
    screenView.getSurface().setResizeMode(true);
  }

  public void updatePosition(int x, int y) {
    ScreenView screenView = myDesignSurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    screenView.getModel().overrideConfigurationScreenSize(Coordinates.getAndroidX(screenView, x),
                                                          Coordinates.getAndroidY(screenView, y));
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
    super.update(x, y, modifiers);
    myCurrentX = x;
    myCurrentY = y;

    // Only do live updating of the file if we are in preview mode
    if (isPreviewSurface) {
      updatePosition(x, y);
    }
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);

    ScreenView screenView = myDesignSurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    // Set the surface in resize mode so it doesn't try to re-center the screen views all the time
    screenView.getSurface().setResizeMode(false);

    // When disabling the resize mode, add a render handler to call zoomToFit
    screenView.getModel().addListener(new ModelListener() {
      @Override
      public void modelChanged(@NotNull NlModel model) {
      }

      @Override
      public void modelRendered(@NotNull NlModel model) {
        model.removeListener(this);
      }
    });

    updatePosition(x, y);
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
      ScreenView screenView = myDesignSurface.getCurrentScreenView();
      if (screenView == null) {
        return;
      }

      int x = screenView.getX();
      int y = screenView.getY();

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

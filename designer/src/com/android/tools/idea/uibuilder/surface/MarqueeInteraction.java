/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link MarqueeInteraction} is an interaction for swiping out a selection rectangle.
 * With a modifier key, items that intersect the rectangle can be toggled
 * instead of added to the new selection set.
 */
public class MarqueeInteraction extends Interaction {
  /** The {@link Layer} drawn for the marquee. */
  private MarqueeLayer myOverlay;

  /** The surface associated with this interaction. */
  private final SceneView mySceneView;

  /** A copy of the initial selection, when we're toggling the marquee. */
  private final Collection<NlComponent> myInitialSelection;

  /**
   * Creates a new marquee selection (selection swiping).
   *
   * @param surface The canvas where selection is performed.
   * @param toggle If true, toggle the membership of contained elements
   *            instead of adding it.
   */
  public MarqueeInteraction(@NotNull SceneView surface, boolean toggle) {
    mySceneView = surface;

    if (toggle) {
      myInitialSelection = mySceneView.getSelectionModel().getSelection();
    } else {
      myInitialSelection = Collections.emptySet();
    }
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
    if (myOverlay == null) {
      return;
    }

    int xp = Math.min(x, myStartX);
    int yp = Math.min(y, myStartY);
    int w = Math.abs(x - myStartX);
    int h = Math.abs(y - myStartY);

    myOverlay.updateSize(xp, yp, w, h);

    // Convert to Android coordinates and compute selection overlaps
    int ax = Coordinates.getAndroidXDip(mySceneView, xp);
    int ay = Coordinates.getAndroidYDip(mySceneView, yp);
    int aw = Coordinates.getAndroidDimensionDip(mySceneView, w);
    int ah = Coordinates.getAndroidDimensionDip(mySceneView, h);
    Collection<SceneComponent> within = mySceneView.getScene().findWithin(ax, ay, aw, ah);
    List<NlComponent> result = within.stream().map(SceneComponent::getNlComponent).collect(Collectors.toList());
    mySceneView.getSelectionModel().setSelection(result);
    mySceneView.getSurface().repaint();
  }

  @Override
  public List<Layer> createOverlays() {
    myOverlay = new MarqueeLayer();
    return Collections.<Layer>singletonList(myOverlay);
  }

  /**
   * An {@link Layer} for the {@link MarqueeInteraction}; paints a selection
   * overlay rectangle matching the mouse coordinate delta between gesture
   * start and the current position.
   */
  private static class MarqueeLayer extends Layer {
    public int x;
    public int y;
    public int w;
    public int h;

    /**
     * Constructs a new {@link MarqueeLayer}.
     */
    public MarqueeLayer() {
    }

    /**
     * Updates the size of the marquee rectangle.
     *
     * @param x The top left corner of the rectangle, x coordinate.
     * @param y The top left corner of the rectangle, y coordinate.
     * @param w Rectangle w.
     * @param h Rectangle h.
     */
    public void updateSize(int x, int y, int w, int h) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }

    @Override
    public void create() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void paint(@NotNull Graphics2D gc) {
      NlGraphics.drawFilledRect(NlDrawingStyle.SELECTION, gc, x, y, w, h);
    }
  }
}

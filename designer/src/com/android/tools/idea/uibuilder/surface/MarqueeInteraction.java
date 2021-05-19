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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DrawLassoUtil;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.MouseDraggedEvent;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Creates a new marquee selection (selection swiping).
   *
   * @param surface The canvas where selection is performed.
   */
  public MarqueeInteraction(@NotNull SceneView surface) {
    mySceneView = surface;
  }

  @Override
  public void begin(@NotNull InteractionEvent event) {
    assert event instanceof MouseDraggedEvent : "The instance of event should be MouseDraggedEvent but it is " + event.getClass() +
                                                "; The SceneView is " + mySceneView +
                                                ", start (x, y) = " + myStartX + ", " + myStartY + ", start mask is " + myStartMask;

    MouseEvent mouseEvent = ((MouseDraggedEvent)event).getEventObject();
    begin(mouseEvent.getX(), mouseEvent.getY(), mouseEvent.getModifiersEx());
  }

  @Override
  public void update(@NotNull InteractionEvent event) {
    if (event instanceof MouseDraggedEvent) {
      MouseEvent mouseEvent = ((MouseDraggedEvent)event).getEventObject();
      update(mouseEvent.getX(), mouseEvent.getY(), mouseEvent.getModifiersEx());
    }
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
    if (myOverlay == null) {
      return;
    }

    int xp = Math.min(x, myStartX);
    int yp = Math.min(y, myStartY);
    int w = Math.abs(x - myStartX);
    int h = Math.abs(y - myStartY);

    // Convert to Android coordinates and compute selection overlaps
    int ax = Coordinates.getAndroidXDip(mySceneView, xp);
    int ay = Coordinates.getAndroidYDip(mySceneView, yp);
    int aw = Coordinates.getAndroidDimensionDip(mySceneView, w);
    int ah = Coordinates.getAndroidDimensionDip(mySceneView, h);

    myOverlay.updateValues(xp, yp, w, h, x, y, aw, ah);

    Collection<SceneComponent> within = mySceneView.getScene().findWithin(ax, ay, aw, ah);
    List<NlComponent> result = ContainerUtil.map(within, SceneComponent::getNlComponent);
    mySceneView.getSelectionModel().setSelection(result);
    mySceneView.getSurface().repaint();
  }

  @Override
  public void commit(@NotNull InteractionEvent event) {
    // Do nothing
  }

  @Override
  public void cancel(@NotNull InteractionEvent event) {
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    cancel(event.getInfo().getX(), event.getInfo().getY(), event.getInfo().getModifiersEx());
  }

  @Override
  public void cancel(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
    mySceneView.getSelectionModel().clear();
  }

  @Nullable
  @Override
  public Cursor getCursor() {
    return null;
  }

  @NotNull
  @Override
  public List<Layer> createOverlays() {
    ColorSet colorSet = mySceneView.getColorSet();
    myOverlay = new MarqueeLayer(colorSet);
    return Collections.<Layer>singletonList(myOverlay);
  }

  /**
   * An {@link Layer} for the {@link MarqueeInteraction}; paints a selection
   * overlay rectangle matching the mouse coordinate delta between gesture
   * start and the current position.
   */
  private static class MarqueeLayer extends Layer {
    @NotNull ColorSet myColorSet;
    @SwingCoordinate private int x;
    @SwingCoordinate private int y;
    @SwingCoordinate private int w;
    @SwingCoordinate private int h;
    @SwingCoordinate private int mouseX;
    @SwingCoordinate private int mouseY;
    @AndroidDpCoordinate private int androidWidth;
    @AndroidDpCoordinate private int androidHeight;

    /**
     * Constructs a new {@link MarqueeLayer}.
     */
    private MarqueeLayer(@NotNull ColorSet colorSet) {
      myColorSet = colorSet;
    }

    /**
     * Updates the attribute of the marquee rectangle.
     *
     * @param x             The top left corner of the rectangle, x coordinate. The unit is swing (pixel)
     * @param y             The top left corner of the rectangle, y coordinate. The unit is swing (pixel)
     * @param w             Rectangle w. The unit is swing (pixel)
     * @param h             Rectangle h. The unit is swing (pixel)
     * @param mouseX        The x position of mouse. The unit is swing (pixel).
     * @param mouseY        The y position of mouse. The unit is swing (pixel).
     * @param androidWidth  The width of rectangle. The unit is android dp.
     * @param androidHeight The height of rectangle. The unit is android dp.
     */
    private void updateValues(@SwingCoordinate int x,
                              @SwingCoordinate int y,
                              @SwingCoordinate int w,
                              @SwingCoordinate int h,
                              @SwingCoordinate int mouseX,
                              @SwingCoordinate int mouseY,
                              @AndroidDpCoordinate int androidWidth,
                              @AndroidDpCoordinate int androidHeight) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.androidWidth = androidWidth;
      this.androidHeight = androidHeight;
    }

    @Override
    public void paint(@NotNull Graphics2D gc) {
      DrawLassoUtil.drawLasso(gc, myColorSet, x, y, w, h, mouseX, mouseY, androidWidth, androidHeight, true);
    }
  }
}

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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;

/**
 * Implements a mouse interaction started on a ConstraintLayout view group handler
 */
public class ConstraintInteraction extends Interaction {

  /**
   * The surface associated with this interaction.
   */
  private final ScreenView myScreenView;

  /**
   * The component where we start the interaction
   */
  private final NlComponent myComponent;

  /**
   * Base constructor
   *
   * @param screenView the ScreenView we belong to
   * @param component  the component we belong to
   */
  public ConstraintInteraction(@NotNull ScreenView screenView,
                               @NotNull NlComponent component) {
    myScreenView = screenView;
    myComponent = component;
  }

  /**
   * Start the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param startMask The initial AWT mask for the interaction
   */
  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int startMask) {
    super.begin(x, y, startMask);
    int androidX = Coordinates.getAndroidX(myScreenView, myStartX);
    int androidY = Coordinates.getAndroidY(myScreenView, myStartY);

    DrawConstraintModel model = ConstraintModel.getDrawConstraintModel(myScreenView);
    model.updateModifiers(startMask);
    model.setInteractionComponent(myComponent.getParent() != null ? myComponent.getParent() : myComponent);
    model.mousePressed(androidX, androidY);
  }

  /**
   * Update the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiers current modifier key mask
   */
  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
    super.update(x, y, modifiers);
    DrawConstraintModel drawModel = ConstraintModel.getDrawConstraintModel(myScreenView);
    drawModel.updateModifiers(modifiers);
    int androidX = Coordinates.getAndroidX(myScreenView, x);
    int androidY = Coordinates.getAndroidY(myScreenView, y);
    drawModel.mouseDragged(androidX, androidY);
  }

  /**
   * Ends the mouse interaction and commit the modifications if any
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiers current modifier key mask
   * @param canceled  True if the interaction was canceled, and false otherwise.
   */
  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);
    ConstraintModel model = ConstraintModel.getConstraintModel(myScreenView.getModel());
    if (canceled) {
      model.rollbackXml();
      return;
    }

    final int ax = Coordinates.getAndroidX(myScreenView, x);
    final int ay = Coordinates.getAndroidY(myScreenView, y);

    DrawConstraintModel drawConstraintModel = ConstraintModel.getDrawConstraintModel(myScreenView);
    drawConstraintModel.updateModifiers(modifiers);
    drawConstraintModel.mouseReleased(ax, ay);

    model.updateMemoryXML(); // first do a memory update
    model.saveToXML(true);
    model.requestLayout(true);

    myScreenView.getSurface().repaint();
  }

}

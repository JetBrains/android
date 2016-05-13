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
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetCompanion;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Implements a mouse interaction started on a ConstraintLayout view group handler
 */
public class ConstraintInteraction extends Interaction {

  /**
   * The surface associated with this interaction.
   */
  private final ScreenView myScreenView;

  /**
   * Base constructor
   *
   * @param screenView the ScreenView we belong to
   * @param component  the component we belong to
   */
  public ConstraintInteraction(@NotNull ScreenView screenView,
                               @NotNull NlComponent component) {
    myScreenView = screenView;
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
    DrawConstraintModel model = ConstraintModel.getDrawConstraintModel(myScreenView);
    model.updateModifiers(modifiers);
    int androidX = Coordinates.getAndroidX(myScreenView, x);
    int androidY = Coordinates.getAndroidY(myScreenView, y);
    model.mouseDragged(androidX, androidY);
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
    if (canceled) {
      ConstraintModel model = ConstraintModel.getConstraintModel(myScreenView.getModel());
      model.allowsUpdate(true);
      return;
    }

    final int ax = Coordinates.getAndroidX(myScreenView, x);
    final int ay = Coordinates.getAndroidY(myScreenView, y);

    DrawConstraintModel model = ConstraintModel.getDrawConstraintModel(myScreenView);
    model.updateModifiers(modifiers);
    model.mouseReleased(ax, ay);

    myScreenView.getSurface().repaint();
  }

}

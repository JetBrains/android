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
package com.android.tools.idea.naveditor.scene.decorator;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawTruncatedText;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.draw.DrawFilledRectangle;
import com.android.tools.idea.naveditor.scene.draw.DrawRectangle;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.*;


/**
 * {@link SceneDecorator} for the whole of a navigation flow (that is, the root component).
 */
public class NavigationDecorator extends SceneDecorator {
  // Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
  @NavCoordinate private static final int NAVIGATION_ARC_SIZE = 12;
  @NavCoordinate private static final int NAVIGATION_BORDER_THICKNESS = 2;

  @Override
  protected void addBackground(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
  }

  @Override
  protected void addFrame(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
  }

  @Override
  protected void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (isDisplayRoot(sceneContext, component)) {
      return;
    }

    Rectangle bounds = Coordinates.getSwingRect(sceneContext, component.fillDrawRect(0, null));

    @SwingCoordinate int arcSize = Coordinates.getSwingDimension(sceneContext, NAVIGATION_ARC_SIZE);
    list.add(new DrawFilledRectangle(bounds, sceneContext.getColorSet().getComponentBackground(), arcSize));

    @SwingCoordinate int strokeThickness = strokeThickness(sceneContext, component, NAVIGATION_BORDER_THICKNESS);
    Rectangle frameRectangle = new Rectangle(bounds);
    frameRectangle.grow(strokeThickness, strokeThickness);

    Color frameColor = frameColor(sceneContext, component);
    list.add(new DrawRectangle(frameRectangle, frameColor, strokeThickness, arcSize));

    String text = NavComponentHelperKt.getIncludeFileName(component.getNlComponent());
    if (text == null) {
      text = "Nested Graph";
    }

    Font font = scaledFont(sceneContext, Font.BOLD);
    list.add(new DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, text, bounds,
                                   textColor(sceneContext, component), font, true));
  }

  @Override
  public void buildList(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (isDisplayRoot(sceneContext, component)) {
      super.buildList(list, time, sceneContext, component);
      return;
    }

    DisplayList displayList = new DisplayList();
    super.buildList(displayList, time, sceneContext, component);
    list.add(createDrawCommand(displayList, component));
  }

  @Override
  protected void buildListChildren(@NotNull DisplayList list,
                                   long time,
                                   @NotNull SceneContext sceneContext,
                                   @NotNull SceneComponent component) {
    if (isDisplayRoot(sceneContext, component)) {
      super.buildListChildren(list, time, sceneContext, component);
      return;
    }

    // TODO: Either set an appropriate clip here, or make this the default behavior in the base class
    for (SceneComponent child : component.getChildren()) {
      child.buildDisplayList(time, list, sceneContext);
    }
  }

  private static boolean isDisplayRoot(@NotNull SceneContext sceneContext, @NotNull SceneComponent sceneComponent) {
    NavDesignSurface navSurface = (NavDesignSurface)sceneContext.getSurface();
    return navSurface != null && sceneComponent.getNlComponent() == navSurface.getCurrentNavigation();
  }
}

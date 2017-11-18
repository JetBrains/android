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

import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.draw.DrawTextRegion;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.scene.draw.DrawNavigationBackground;
import com.android.tools.idea.naveditor.scene.draw.DrawNavigationFrame;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


/**
 * {@link SceneDecorator} for the whole of a navigation flow (that is, the root component).
 */
public class NavigationDecorator extends SceneDecorator {
  private static final int BASELINE_OFFSET = 22;
  private static final int FONT_SIZE = 12;

  @Override
  protected void addBackground(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (isDisplayRoot(sceneContext, component)) {
      return;
    }

    Rectangle rect = Coordinates.getSwingRect(sceneContext, component.fillRect(null));
    list.add(new DrawNavigationBackground(rect));
  }

  @Override
  protected void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (isDisplayRoot(sceneContext, component)) {
      return;
    }

    Rectangle bounds = Coordinates.getSwingRect(sceneContext, component.fillDrawRect(0, null));
    // TODO: baseline based on text size
    // TODO: add resource resolver here?
    String label = NavComponentHelperKt.getUiName(component.getNlComponent(), null);
    double scale = sceneContext.getScale();
    list.add(new DrawTextRegion(bounds.x, bounds.y, bounds.width, bounds.height, DecoratorUtilities.ViewStates.NORMAL.getVal(),
                                (int)(scale * BASELINE_OFFSET), label, true, false, DrawTextRegion.TEXT_ALIGNMENT_CENTER,
                                DrawTextRegion.TEXT_ALIGNMENT_CENTER, FONT_SIZE, (float)scale));
  }

  @Override
  protected void addFrame(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (isDisplayRoot(sceneContext, component)) {
      return;
    }

    Rectangle rect = Coordinates.getSwingRect(sceneContext, component.fillRect(null));
    list.add(new DrawNavigationFrame(rect, component.isSelected(),
                                     component.getDrawState() == SceneComponent.DrawState.HOVER || component.isDragging()));
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

  private static boolean isDisplayRoot(@NotNull SceneContext sceneContext, @NotNull SceneComponent sceneComponent) {
    NavDesignSurface navSurface = (NavDesignSurface)sceneContext.getSurface();
    return navSurface != null && sceneComponent.getNlComponent() == navSurface.getCurrentNavigation();
  }

  public static DrawCommand createDrawCommand(DisplayList list, SceneComponent component) {
    int level = DrawCommand.COMPONENT_LEVEL;

    if (component.isDragging()) {
      level = DrawCommand.TOP_LEVEL;
    }
    else if (component.isSelected()) {
      level = DrawCommand.COMPONENT_SELECTED_LEVEL;
    }

    return list.getCommand(level);
  }
}

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

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawComponentFrame;
import com.android.tools.idea.uibuilder.scene.draw.DrawTextRegion;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@link SceneDecorator} for the whole of a navigation flow (that is, the root component).
 */
public class NavigationDecorator extends SceneDecorator {
  static final int BASELINE_OFFSET = 35;
  static final int FONT_SIZE = 30;

  @Override
  protected void addBackground(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    // TODO: nothing?
  }

  @Override
  protected void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    DesignSurface surface = sceneContext.getSurface();
    NavDesignSurface navSurface = (NavDesignSurface)surface;
    if (navSurface != null && component.getNlComponent() != navSurface.getCurrentNavigation()) {
      Rectangle bounds = Coordinates.getSwingRectDip(sceneContext, component.fillDrawRect(0, null));
      // TODO: baseline based on text size
      String label = component.getNlComponent().getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL);
      if (label == null) {
        label = NlComponent.stripId(component.getId());
      }
      if (label == null) {
        label = "navigation";
      }
      double scale = sceneContext.getScale();
      list.add(new DrawTextRegion(bounds.x, bounds.y, bounds.width, bounds.height, DecoratorUtilities.ViewStates.NORMAL.getVal(),
                                  (int)(scale * BASELINE_OFFSET), label, true, false, DrawTextRegion.TEXT_ALIGNMENT_CENTER,
                                  DrawTextRegion.TEXT_ALIGNMENT_CENTER, FONT_SIZE, (float)scale));
    }
  }

  @Override
  protected void addFrame(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    DesignSurface surface = sceneContext.getSurface();
    NavDesignSurface navSurface = (NavDesignSurface)surface;
    if (navSurface != null && component.getNlComponent() != navSurface.getCurrentNavigation()) {
      DrawComponentFrame.add(list, sceneContext, component.fillRect(null), component.getDrawState().ordinal(), true);
    }
  }
}

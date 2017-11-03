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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawNlComponentFrame;
import com.android.tools.idea.common.scene.decorator.SceneFrameFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities.getDpValue;

/**
 * A {@link SceneFrameFactory} specific to the layout editor.
 */
public class NlSceneFrameFactory implements SceneFrameFactory {
  @Override
  public void addFrame(@NotNull DisplayList list, @NotNull SceneComponent component, @NotNull SceneContext sceneContext) {
    Rectangle rect = new Rectangle();
    component.fillRect(rect); // get the rectangle from the component

    int layout_width = layoutDimToMode(component.getAuthoritativeNlComponent(), SdkConstants.ATTR_LAYOUT_WIDTH);
    int layout_height = layoutDimToMode(component.getAuthoritativeNlComponent(), SdkConstants.ATTR_LAYOUT_HEIGHT);
    SceneComponent.DrawState mode = component.getDrawState();
    boolean paint = sceneContext.showOnlySelection() ? mode == SceneComponent.DrawState.SELECTED : true;
    if (paint) {
      DrawNlComponentFrame.add(list, sceneContext, rect, mode.ordinal(), layout_width, layout_height); // add to the list
    }
  }

  private static int layoutDimToMode(@NotNull NlComponent component, @NotNull String attr) {
    String value = component.getAttribute(SdkConstants.ANDROID_URI, attr);
    if (SdkConstants.VALUE_WRAP_CONTENT.equalsIgnoreCase(value)) return -2;
    if (SdkConstants.VALUE_MATCH_PARENT.equalsIgnoreCase(value)) return -1;
    return getDpValue(component, value);
  }
}

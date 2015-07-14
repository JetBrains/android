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
package com.android.tools.idea.uibuilder.handlers;

import com.android.annotations.NonNull;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.ScreenView;

import java.awt.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.model.Coordinates.getSwingX;
import static com.android.tools.idea.uibuilder.model.Coordinates.getSwingY;

public class LinearLayoutHandler extends ViewHandler {
  @Override
  public boolean paintConstraints(@NonNull ScreenView screenView, @NonNull Graphics2D graphics, @NonNull NlComponent component) {
    NlComponent prev = null;
    boolean vertical = isVertical(component);
    for (NlComponent child : component.getChildren()) {
      if (prev != null) {
        if (vertical) {
          int middle = getSwingY(screenView, (prev.y + prev.h + child.y) / 2);
          NlGraphics.drawLine(NlDrawingStyle.GUIDELINE_DASHED, graphics, getSwingX(screenView, component.x), middle,
                              getSwingX(screenView, component.x + component.w), middle);
        } else {
          int middle = getSwingX(screenView, (prev.x + prev.w + child.x) / 2);
          NlGraphics.drawLine(NlDrawingStyle.GUIDELINE_DASHED, graphics, middle, getSwingY(screenView, component.y), middle,
                              getSwingY(screenView, component.y + component.h));
        }
      }
      prev = child;
    }
    return false;
  }

  /**
   * Returns true if the given node represents a vertical linear layout.
   * @param component the node to check layout orientation for
   * @return true if the layout is in vertical mode, otherwise false
   */
  private static boolean isVertical(NlComponent component) {
    // Horizontal is the default, so if no value is specified it is horizontal.
    String orientation = AndroidPsiUtils.getAttributeSafely(component.tag, ANDROID_URI, ATTR_ORIENTATION);
    return VALUE_VERTICAL.equals(orientation);
  }
}

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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawLasso;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

/**
 * Implementation of a Lasso
 */
public class LassoTarget extends BaseTarget {

  private static final boolean DEBUG_RENDERER = false;
  private float myOriginX;
  private float myOriginY;
  private float myLastX;
  private float myLastY;
  private boolean myShowRect;

  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    myLeft = l;
    myTop = t;
    myRight = r;
    myBottom = b;
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.blue);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, Color.blue);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, Color.blue);
    }
    if (myShowRect) {
      float x1 = Math.min(myOriginX, myLastX);
      float x2 = Math.max(myOriginX, myLastX);
      float y1 = Math.min(myOriginY, myLastY);
      float y2 = Math.max(myOriginY, myLastY);
      DrawLasso.add(list, sceneContext, x1, y1, x2, y2);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return 0;
  }

  @Override
  public void mouseDown(int x, int y) {
    myOriginX = x;
    myOriginY = y;
    myLastX = x;
    myLastY = y;
    myShowRect = true;
    myComponent.getScene().needsRebuildList();
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    myLastX = x;
    myLastY = y;
    myComponent.getScene().needsRebuildList();
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    myShowRect = false;
    myComponent.getScene().needsRebuildList();
  }

  /**
   * Fills the given array with selected components, if any
   *
   * @param components
   */
  public void fillSelectedComponents(ArrayList<SceneComponent> components) {
    components.clear();
    int count = myComponent.getChildCount();
    float x1 = Math.min(myOriginX, myLastX);
    float x2 = Math.max(myOriginX, myLastX);
    float y1 = Math.min(myOriginY, myLastY);
    float y2 = Math.max(myOriginY, myLastY);
    if ((int) (x2 - x1) == 0 && (int) (y2 - y1) == 0) {
      return;
    }
    Rectangle bounds = new Rectangle((int) x1, (int) y1, (int) (x2 - x1), (int) (y2 - y1));
    for (int i = 0; i < count; i++) {
      SceneComponent component = myComponent.getChild(i);
      if (component.intersects(bounds)) {
        components.add(component);
      }
    }
  }

  //endregion
}

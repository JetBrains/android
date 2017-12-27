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
package com.android.tools.idea.common.scene.target;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawLasso;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a Lasso
 */
public class LassoTarget extends BaseTarget {

  private static final boolean DEBUG_RENDERER = false;
  @AndroidDpCoordinate private float myOriginX;
  @AndroidDpCoordinate private float myOriginY;
  @AndroidDpCoordinate private float myLastX;
  @AndroidDpCoordinate private float myLastY;
  private boolean myShowRect;

  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
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
    return Target.LASSO_LEVEL;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myOriginX = x;
    myOriginY = y;
    myLastX = x;
    myLastY = y;
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    myLastX = x;
    myLastY = y;
    myShowRect = true;
    myComponent.getScene().needsRebuildList();
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    myShowRect = false;
    myComponent.getScene().needsRebuildList();
  }

  /**
   * Fills the given array with selected components, if any
   *
   * @param components
   */
  public void fillSelectedComponents(ArrayList<SceneComponent> components) {
    int count = myComponent.getChildCount();
    float x1 = Math.min(myOriginX, myLastX);
    float x2 = Math.max(myOriginX, myLastX);
    float y1 = Math.min(myOriginY, myLastY);
    float y2 = Math.max(myOriginY, myLastY);
    if ((int) (x2 - x1) == 0 && (int) (y2 - y1) == 0) {
      return;
    }
    components.clear();
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

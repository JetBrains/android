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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawHorizontalLine;
import com.android.tools.idea.uibuilder.scene.draw.DrawResize;
import com.android.tools.idea.uibuilder.scene.draw.DrawVerticalLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

import static com.android.SdkConstants.*;

public abstract class ResizeWithSnapBaseTarget extends ResizeBaseTarget {
  /**
   * The maximum number of dp will be considered a "match" when snapping
   */
  @AndroidDpCoordinate
  private static final int MAX_MATCH_DISTANCE = 10;
  private static final int CANNOT_SNAP = Integer.MAX_VALUE;

  protected Dimension myWrapSize;

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public ResizeWithSnapBaseTarget(@NotNull Type type) {
    super(type);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (!myComponent.isSelected()) {
      return;
    }

    DrawResize.add(list, sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? DrawResize.OVER : DrawResize.NORMAL);

    if (myWrapSize != null) {
      renderWrapSizeSnapLines(list, sceneContext, myWrapSize.width, myWrapSize.height);
    }
  }

  private void renderWrapSizeSnapLines(@NotNull DisplayList list,
                                       @NotNull SceneContext sceneContext,
                                       @AndroidDpCoordinate int wrapX,
                                       @AndroidDpCoordinate int wrapY) {
    switch (myType) {
      case LEFT_TOP:
        DrawVerticalLine.add(list, sceneContext, myStartX2 - wrapX, myStartY2 - wrapY, myStartY2);
        DrawHorizontalLine.add(list, sceneContext, myStartX2 - wrapX, myStartY2 - wrapY, myStartX2);
        break;
      case LEFT:
        DrawVerticalLine.add(list, sceneContext, myStartX2 - wrapX, myStartY1, myStartY2);
        break;
      case LEFT_BOTTOM:
        DrawVerticalLine.add(list, sceneContext, myStartX2 - wrapX, myStartY1, myStartY1 + wrapY);
        DrawHorizontalLine.add(list, sceneContext, myStartX2 - wrapX, myStartY2 - wrapY, myStartX2);
        break;
      case TOP:
        DrawHorizontalLine.add(list, sceneContext, myStartX1, myStartY2 - wrapY, myStartX2);
        break;
      case BOTTOM:
        DrawHorizontalLine.add(list, sceneContext, myStartX1, myStartY1 + wrapY, myStartX2);
        break;
      case RIGHT_TOP:
        DrawVerticalLine.add(list, sceneContext, myStartX1 + wrapX, myStartY2 - wrapY, myStartY2);
        DrawHorizontalLine.add(list, sceneContext, myStartX1, myStartY2 - wrapY, myStartX1 + wrapX);
        break;
      case RIGHT:
        DrawVerticalLine.add(list, sceneContext, myStartX1 + wrapX, myStartY1, myStartY2);
        break;
      case RIGHT_BOTTOM:
        DrawVerticalLine.add(list, sceneContext, myStartX1 + wrapX, myStartY1, myStartY1 + wrapY);
        DrawHorizontalLine.add(list, sceneContext, myStartX1, myStartY1 + wrapY, myStartX1 + wrapX);
        break;
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myWrapSize = myComponent.getScene().measureWrapSize(myComponent);
    super.mouseDown(x, y);
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    x = snapX(x);
    y = snapY(y);
    super.mouseDrag(x, y, closestTargets);
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    x = snapX(x);
    y = snapY(y);
    super.mouseRelease(x, y, closestTargets);
    myWrapSize = null;
  }

  @AndroidDpCoordinate
  protected int getNewXPos(@AndroidDpCoordinate int x) {
    SceneComponent parent = myComponent.getParent();
    int parentX = parent != null ? parent.getDrawX() : 0;
    switch (myType) {
      case LEFT_TOP:
      case LEFT:
      case LEFT_BOTTOM:
        return Math.min(x, myStartX2) - parentX;
      case RIGHT_TOP:
      case RIGHT:
      case RIGHT_BOTTOM:
        return Math.min(x, myStartX1) - parentX;
      default:
        return myStartX1 - parentX;
    }
  }

  @AndroidDpCoordinate
  protected int getNewYPos(@AndroidDpCoordinate int y) {
    SceneComponent parent = myComponent.getParent();
    int parentY = parent != null ? parent.getDrawY() : 0;
    switch (myType) {
      case LEFT_TOP:
      case TOP:
      case RIGHT_TOP:
        return Math.min(y, myStartY2) - parentY;
      case LEFT_BOTTOM:
      case BOTTOM:
      case RIGHT_BOTTOM:
        return Math.min(y, myStartY1) - parentY;
      default:
        return myStartY1 - parentY;
    }
  }

  protected String getNewWidth(@AndroidDpCoordinate int x) {
    int width = getNewDpWidth(x);

    SceneComponent parent = myComponent.getParent();
    if (parent != null && width == parent.getDrawWidth() && getNewXPos(x) == 0) {
      return VALUE_MATCH_PARENT;
    }
    if (myWrapSize != null && width == myWrapSize.width) {
      return VALUE_WRAP_CONTENT;
    }
    return String.format(VALUE_N_DP, width);
  }

  protected String getNewHeight(@AndroidDpCoordinate int y) {
    int height = getNewDpHeight(y);

    SceneComponent parent = myComponent.getParent();
    if (parent != null && height == parent.getDrawHeight() && getNewYPos(y) == 0) {
      return VALUE_MATCH_PARENT;
    }
    if (myWrapSize != null && height == myWrapSize.height) {
      return VALUE_WRAP_CONTENT;
    }
    return String.format(VALUE_N_DP, height);
  }

  @AndroidDpCoordinate
  private int getNewDpWidth(@AndroidDpCoordinate int x) {
    switch (myType) {
      case LEFT_TOP:
      case LEFT:
      case LEFT_BOTTOM:
        return Math.abs(myStartX2 - x);
      case RIGHT_TOP:
      case RIGHT:
      case RIGHT_BOTTOM:
        return Math.abs(myStartX1 - x);
      default:
        return myStartX2 - myStartX1;
    }
  }

  @AndroidDpCoordinate
  private int getNewDpHeight(@AndroidDpCoordinate int y) {
    switch (myType) {
      case LEFT_TOP:
      case TOP:
      case RIGHT_TOP:
        return Math.abs(myStartY2 - y);
      case LEFT_BOTTOM:
      case BOTTOM:
      case RIGHT_BOTTOM:
        return Math.abs(myStartY1 - y);
      default:
        return myStartY2 - myStartY1;
    }
  }

  @AndroidDpCoordinate
  private int snapX(@AndroidDpCoordinate int x) {
    int dx = snapToParentBoundaryX(x);
    if (Math.abs(dx) < MAX_MATCH_DISTANCE) {
      return x + dx;
    }
    dx = snapToWrapWidth(x);
    if (Math.abs(dx) < MAX_MATCH_DISTANCE) {
      return x + dx;
    }
    return x;
  }

  @AndroidDpCoordinate
  private int snapY(@AndroidDpCoordinate int y) {
    int dy = snapToParentBoundaryY(y);
    if (Math.abs(dy) < MAX_MATCH_DISTANCE) {
      return y + dy;
    }
    dy = snapToWrapHeight(y);
    if (Math.abs(dy) < MAX_MATCH_DISTANCE) {
      return y + dy;
    }
    return y;
  }

  @AndroidDpCoordinate
  private int snapToWrapWidth(@AndroidDpCoordinate int x) {
    if (myWrapSize == null) {
      return CANNOT_SNAP;
    }
    int width = myWrapSize.width;
    switch (myType) {
      case LEFT_TOP:
      case LEFT:
      case LEFT_BOTTOM:
        return x < myStartX2 ? (myStartX2 - x) - width : width - (x - myStartX2);
      case RIGHT_TOP:
      case RIGHT:
      case RIGHT_BOTTOM:
        return x < myStartX1 ? (myStartX1 - x) - width : width - (x - myStartX1);
      default:
        return CANNOT_SNAP;
    }
  }

  @AndroidDpCoordinate
  private int snapToWrapHeight(@AndroidDpCoordinate int y) {
    if (myWrapSize == null) {
      return CANNOT_SNAP;
    }
    int height = myWrapSize.height;
    switch (myType) {
      case LEFT_TOP:
      case TOP:
      case RIGHT_TOP:
        return y < myStartY2 ? (myStartY2 - y) - height : height - (y - myStartY2);
      case LEFT_BOTTOM:
      case BOTTOM:
      case RIGHT_BOTTOM:
        return y < myStartY1 ? (myStartY1 - y) - height : height - (y - myStartY1);
      default:
        return CANNOT_SNAP;
    }
  }

  @AndroidDpCoordinate
  private int snapToParentBoundaryX(@AndroidDpCoordinate int x) {
    SceneComponent parent = myComponent.getParent();
    if (parent == null) {
      return CANNOT_SNAP;
    }
    switch (myType) {
      case LEFT_TOP:
      case LEFT:
      case LEFT_BOTTOM:
        return x < myStartX2 ? -x : parent.getDrawWidth() - (x - myStartX2);
      case RIGHT_TOP:
      case RIGHT:
      case RIGHT_BOTTOM:
        return x < myStartX1 ? -x : parent.getDrawWidth() - (x - myStartX1);
      default:
        return CANNOT_SNAP;
    }
  }

  @AndroidDpCoordinate
  private int snapToParentBoundaryY(@AndroidDpCoordinate int y) {
    SceneComponent parent = myComponent.getParent();
    if (parent == null) {
      return CANNOT_SNAP;
    }
    switch (myType) {
      case LEFT_TOP:
      case TOP:
      case RIGHT_TOP:
        return y < myStartY2 ? - y : parent.getDrawHeight() - (y - myStartY2);
      case LEFT_BOTTOM:
      case BOTTOM:
      case RIGHT_BOTTOM:
        return y < myStartY1 ? - y : parent.getDrawHeight() - (y - myStartY1);
      default:
        return CANNOT_SNAP;
    }
  }
}

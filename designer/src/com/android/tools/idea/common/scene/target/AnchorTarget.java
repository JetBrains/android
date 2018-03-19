/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Optional;

/**
 * Implements a target anchor for the ConstraintLayout.
 */
abstract public class AnchorTarget extends BaseTarget implements Notch.Provider {

  private static final boolean DEBUG_RENDERER = false;

  private static final int ANCHOR_SIZE = 3;
  private static final int EXPANDED_SIZE = 200;

  @NotNull protected final Type myType;
  @AndroidDpCoordinate protected int myLastX = -1;
  @AndroidDpCoordinate protected int myLastY = -1;
  private boolean myExpandArea = false;

  protected final TargetSnapper mySnapper = new TargetSnapper();

  /**
   * Type of possible anchors.
   */
  public enum Type {
    LEFT, TOP, RIGHT, BOTTOM, BASELINE;

    public int getMask() {
      switch (this) {
        case LEFT:
          return DecoratorUtilities.MASK_LEFT;
        case TOP:
          return DecoratorUtilities.MASK_TOP;
        case RIGHT:
          return DecoratorUtilities.MASK_RIGHT;
        case BOTTOM:
          return DecoratorUtilities.MASK_BOTTOM;
        case BASELINE:
          return DecoratorUtilities.MASK_BASELINE;
      }
      return 0;
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public AnchorTarget(@NotNull Type type) {
    myType = type;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  @NotNull
  public Type getType() {
    return myType;
  }

  protected abstract boolean isConnected();

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Override
  public void setExpandSize(boolean expand) {
    myExpandArea = expand;
  }

  @Override
  public void setMouseHovered(boolean over) {
    if (over != mIsOver) {
      changeMouseOverState(over);
      myComponent.getScene().needsRebuildList();
      myComponent.getScene().repaint();
    }
  }

  private void changeMouseOverState(boolean newValue) {
    mIsOver = newValue;
    String dir;
    switch (myType) {
      case LEFT:
        dir = DecoratorUtilities.LEFT_CONNECTION;
        break;
      case TOP:
        dir = DecoratorUtilities.TOP_CONNECTION;
        break;
      case RIGHT:
        dir = DecoratorUtilities.RIGHT_CONNECTION;
        break;
      case BOTTOM:
        dir = DecoratorUtilities.BOTTOM_CONNECTION;
        break;
      default:
        dir = DecoratorUtilities.BASELINE_CONNECTION;
        break;
    }
    DecoratorUtilities.ViewStates mode = DecoratorUtilities.ViewStates.SELECTED;
    if (isTargeted()) {
      mode = DecoratorUtilities.ViewStates.WILL_DESTROY;
    }
    DecoratorUtilities.setTimeChange(myComponent.getNlComponent(), dir, mode);
  }

  /**
   * Returns true if this anchor is currently the destination target in a new constraint creation.
   */
  protected boolean isTargeted() {
    return mIsOver && !myComponent.isSelected();
  }

  @Override
  public void onComponentSelectionChanged(boolean selection) {
    String dir;
    switch (myType) {
      case LEFT:
        dir = DecoratorUtilities.LEFT_CONNECTION;
        break;
      case TOP:
        dir = DecoratorUtilities.TOP_CONNECTION;
        break;
      case RIGHT:
        dir = DecoratorUtilities.RIGHT_CONNECTION;
        break;
      case BOTTOM:
        dir = DecoratorUtilities.BOTTOM_CONNECTION;
        break;
      default:
        dir = DecoratorUtilities.BASELINE_CONNECTION;
        break;
    }
    DecoratorUtilities.ViewStates mode = (selection) ? DecoratorUtilities.ViewStates.SELECTED : DecoratorUtilities.ViewStates.NORMAL;

    DecoratorUtilities.setTimeChange(myComponent.getNlComponent(), dir, mode);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    float ratio = 1f / (float)sceneTransform.getScale();
    if (ratio > 2) {
      ratio = 2;
    }
    float size = (ANCHOR_SIZE * ratio);
    float minWidth = 4 * size;
    float minHeight = 4 * size;
    if (r - l < minWidth) {
      float d = (minWidth - (r - l)) / 2;
      l -= d;
      r += d;
    }
    if (b - t < minHeight) {
      float d = (minHeight - (b - t)) / 2;
      t -= d;
      b += d;
    }
    int w = r - l;
    int h = b - t;
    int mw = l + w / 2;
    int mh = t + h / 2;
    switch (myType) {
      case LEFT: {
        myLeft = l - size;
        myTop = mh - size;
        myRight = l + size;
        myBottom = mh + size;
        if (myExpandArea) {
          myLeft = l - EXPANDED_SIZE;
          myTop = t;
          myBottom = b;
        }
      }
      break;
      case TOP: {
        myLeft = mw - size;
        myTop = t - size;
        myRight = mw + size;
        myBottom = t + size;
        if (myExpandArea) {
          myTop = t - EXPANDED_SIZE;
          myLeft = l;
          myRight = r;
        }
      }
      break;
      case RIGHT: {
        myLeft = r - size;
        myTop = mh - size;
        myRight = r + size;
        myBottom = mh + size;
        if (myExpandArea) {
          myRight = r + EXPANDED_SIZE;
          myTop = t;
          myBottom = b;
        }
      }
      break;
      case BOTTOM: {
        myLeft = mw - size;
        myTop = b - size;
        myRight = mw + size;
        myBottom = b + size;
        if (myExpandArea) {
          myBottom = b + EXPANDED_SIZE;
          myLeft = l;
          myRight = r;
        }
      }
      break;
      case BASELINE: {
        myLeft = l + size;
        myTop = t + myComponent.getBaseline() - size / 2;
        myRight = r - size;
        myBottom = t + myComponent.getBaseline() + size / 2;
      }
      break;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @SuppressWarnings("UseJBColor")
  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    // TODO: Refactor this condition later.
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    if (myComponent.isDragging() && !isConnected()) {
      return;
    }

    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.green);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, Color.red);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, Color.red);
    }

    DrawAnchor.Mode mode = getDrawMode();
    DrawAnchor.Type type = getDrawType();
    boolean drawAsConnected = getDrawAsConnected();

    if (mode != DrawAnchor.Mode.DO_NOT_DRAW) {
      DrawAnchor.add(list, sceneContext, myLeft, myTop, myRight, myBottom, type, drawAsConnected, mode);
    }
  }

  @NotNull
  protected abstract DrawAnchor.Mode getDrawMode();

  @NotNull
  private DrawAnchor.Type getDrawType() {
    return myType == Type.BASELINE? DrawAnchor.Type.BASELINE : DrawAnchor.Type.NORMAL;
  }

  private boolean getDrawAsConnected() {
    return isConnected() && !isTargeted();
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastX = -1;
    myLastY = -1;
    mySnapper.reset();
    mySnapper.gatherNotches(myComponent);
  }

  /**
   * On mouse drag, we can connect (in memory) to existing targets, or revert to the original state that we captured on mouseDown.
   */
  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> ignored) {
    Optional<Point> p = mySnapper.trySnapCircle(x, y);
    myLastX = p.map(point -> point.x).orElse(x);
    myLastY = p.map(point -> point.y).orElse(y);
  }

  /**
   * On mouseRelease, we can either disconnect the current anchor(if the mouse release is on itself).
   * or connect the anchor to a given target. Modifications are applied first in memory then committed
   * to the XML model.
   */
  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> ignored) {
    myLastX = -1;
    myLastY = -1;
  }

  @Override
  public String getToolTipText() {
    return isConnected() ? "Delete Connection" : "Create Connection";
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void fill(@NotNull SceneComponent owner,
                   @NotNull SceneComponent snappableComponent,
                   @NotNull ImmutableList.Builder<Notch> notchBuilder) {
    // TODO: Refactor this condition later.
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    if (myComponent.isDragging() && !isConnected()) {
      return;
    }

    int x, y;
    switch (getType()) {
      case LEFT:
        x = owner.getDrawX();
        y = owner.getDrawY() + owner.getDrawHeight() / 2;
        break;
      case TOP:
        x = owner.getDrawX() + owner.getDrawWidth() / 2;
        y = owner.getDrawY();
        break;
      case RIGHT:
        x = owner.getDrawX() + owner.getDrawWidth();
        y = owner.getDrawY() + owner.getDrawHeight() / 2;
        break;
      case BOTTOM:
        x = owner.getDrawX() + owner.getDrawWidth() / 2;
        y = owner.getDrawY() + owner.getDrawHeight();
        break;
      default:
        x = -1;
        y = -1;
    }
    Notch notch = new Notch.Circle(owner, x, y, null);
    // Make it bigger for snapping.
    notch.setGap(ANCHOR_SIZE * 5);
    notch.setTarget(this);
    notchBuilder.add(notch);
  }

  @Nullable
  public static AnchorTarget findAnchorTarget(@NotNull SceneComponent component, @NotNull AnchorTarget.Type type) {
    for (Target target : component.getTargets()) {
      if (target instanceof AnchorTarget && ((AnchorTarget)target).myType == type) {
        return (AnchorTarget) target;
      }
    }
    return null;
  }
}

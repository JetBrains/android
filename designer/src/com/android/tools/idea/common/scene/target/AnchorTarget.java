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

import static com.intellij.util.ui.JBUI.scale;

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper;
import com.google.common.collect.ImmutableList;
import java.awt.Color;
import java.awt.Point;
import java.util.List;
import java.util.Optional;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements a target anchor for the ConstraintLayout.
 */
abstract public class AnchorTarget extends BaseTarget implements Notch.Provider {

  private static final boolean DEBUG_RENDERER = false;

  @SwingCoordinate public static final int ANCHOR_SIZE = scale(6);
  @SwingCoordinate public static final int EXPANDED_SIZE = scale(400);

  @AndroidDpCoordinate private float myPositionX;
  @AndroidDpCoordinate private float myPositionY;

  @NotNull protected final Type myType;
  @AndroidDpCoordinate protected int myLastX = -1;
  @AndroidDpCoordinate protected int myLastY = -1;

  protected final boolean myIsEdge;

  /**
   * If this Anchor is dragging.
   * Note that this doesn't mean the associated component is dragging. This means Anchor itself is dragging.
   */
  protected boolean myIsDragging = false;

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

    @Override
    public String toString() {
      switch (this) {
        case LEFT:
          return DecoratorUtilities.LEFT_CONNECTION;
        case TOP:
          return DecoratorUtilities.TOP_CONNECTION;
        case RIGHT:
          return DecoratorUtilities.RIGHT_CONNECTION;
        case BOTTOM:
          return DecoratorUtilities.BOTTOM_CONNECTION;
        default:
          return DecoratorUtilities.BASELINE_CONNECTION;
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public AnchorTarget(@NotNull Type type, boolean isEdge) {
    myType = type;
    myIsEdge = isEdge;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////


  @Override
  @AndroidDpCoordinate
  public float getCenterX() {
    return myPositionX;
  }

  @Override
  @AndroidDpCoordinate
  public float getCenterY() {
    return myPositionY;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  public boolean isEdge() {
    return myIsEdge;
  }

  protected abstract boolean isConnected();

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Override
  public void componentSelectionChanged(boolean selected) {
    DecoratorUtilities.ViewStates mode = selected ? DecoratorUtilities.ViewStates.SELECTED : DecoratorUtilities.ViewStates.NORMAL;
    DecoratorUtilities.setTimeChange(myComponent.getNlComponent(), myType.toString(), mode);
  }

  @Override
  public void setMouseHovered(boolean over) {
    if (over != mIsOver) {
      mIsOver = over;
      myComponent.getScene().needsRebuildList();
      myComponent.getScene().repaint();
    }
  }

  /**
   * Returns true if this anchor is currently the destination target in a new constraint creation.
   */
  protected boolean isTargeted() {
    return mIsOver && !myComponent.isSelected();
  }

  /** Returns true if this anchor can disconnect itself. */
  public boolean canDisconnect() {
    // TODO: Make use of this on a common function to disconnect for constraint and relative anchor targets.
    return true;
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
    float mw = (l + r) / 2f;
    float mh = (t + b) / 2f;
    switch (myType) {
      case LEFT: {
        myPositionX = l;
        myPositionY = mh;
      }
      break;
      case TOP: {
        myPositionX = mw;
        myPositionY = t;
      }
      break;
      case RIGHT: {
        myPositionX = r;
        myPositionY = mh;
      }
      break;
      case BOTTOM: {
        myPositionX = mw;
        myPositionY = b;
      }
      break;
      case BASELINE: {
        myPositionX = mw;
        myPositionY = t + myComponent.getBaseline();
        return false;
      }
    }

    // When width or height is too small, move the anchor outer to avoid anchors overlap each other.
    // The minimal distance between anchors is 4 * ANCHOR_SIZE in Swing Coordinate.
    int anchorSizeDip = Coordinates.getAndroidDimensionDip(myComponent.getScene().getSceneManager().getSceneView(), ANCHOR_SIZE);
    float xDiff = myPositionX - mw;
    if (sceneTransform.getSwingDimensionDip(Math.abs(xDiff)) < ANCHOR_SIZE * 2) {
      float sign = Math.signum(xDiff);
      if (sign == 0) {
        sign = myType == Type.LEFT ? -1f : myType == Type.RIGHT ? 1f : 0;
      }
      myPositionX = mw + sign * 2 * anchorSizeDip;
    }
    float yDiff = myPositionY - mh;
    if (sceneTransform.getSwingDimensionDip(Math.abs(yDiff)) < ANCHOR_SIZE * 2) {
      float sign = Math.signum(yDiff);
      if (sign == 0) {
        sign = myType == Type.TOP ? -1f : myType == Type.BOTTOM ? 1f : 0;
      }
      myPositionY = mh + sign * 2 * anchorSizeDip;
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
    if (!isEnabled()) {
      return;
    }
    if (myComponent.isDragging() && !isConnected()) {
      return;
    }

    if (DEBUG_RENDERER) {
      int swingX = sceneContext.getSwingXDip(myPositionX);
      int swingY = sceneContext.getSwingYDip(myPositionY);
      int left = swingX - ANCHOR_SIZE;
      int top = swingY - ANCHOR_SIZE;
      int right = swingX + ANCHOR_SIZE;
      int bottom = swingY + ANCHOR_SIZE;

      list.addRect(left, top, right, bottom, mIsOver ? Color.yellow : Color.green);
      list.addLine(sceneContext, left, top, right, bottom, Color.red);
      list.addLine(sceneContext, left, bottom, right, top, Color.red);
    }

    DrawAnchor.Mode mode = getDrawMode();
    DrawAnchor.Type type = getDrawType();
    boolean drawAsConnected = getDrawAsConnected();
    if (mode == DrawAnchor.Mode.DELETE && !canDisconnect()) {
      // TODO: This should be done in getDrawMode().
      mode = DrawAnchor.Mode.OVER;
    }

    if (mode != DrawAnchor.Mode.DO_NOT_DRAW) {
      if (type != DrawAnchor.Type.BASELINE) {
        DrawAnchor.add(list, sceneContext, myPositionX, myPositionY, type, drawAsConnected, mode);
      }
      else {
        DrawAnchor.addBaseline(list, sceneContext, myPositionX, myPositionY, myComponent.getDrawWidth(), type, drawAsConnected, mode);
      }
    }
  }

  @Override
  public void addHit(@NotNull SceneContext transform,
                     @NotNull ScenePicker picker,
                     @JdkConstants.InputEventMask int modifiersEx) {
    if (!isEnabled()) {
      return;
    }

    int swingX = transform.getSwingXDip(myPositionX);
    int swingY = transform.getSwingYDip(myPositionY);

    if (myIsEdge) {
      switch (myType) {
        case LEFT:
        case RIGHT:
          int swingHeight = transform.getSwingDimensionDip(myComponent.getDrawHeight());
          picker.addRect(this, 0, swingX - ANCHOR_SIZE, swingY - swingHeight / 2, swingX + ANCHOR_SIZE, swingY + swingHeight / 2);
          break;
        case TOP:
        case BOTTOM:
          int swingWidth = transform.getSwingDimensionDip(myComponent.getDrawWidth());
          picker.addRect(this, 0, swingX - swingWidth / 2, swingY - ANCHOR_SIZE, swingX + swingWidth / 2, swingY + ANCHOR_SIZE);
          break;
        case BASELINE:
          // should not happen here since baseline anchor should never expand.
          break;
      }
    }
    else if (myType != Type.BASELINE) {
      // The height of Baseline Anchor is smaller.
      picker.addRect(this, 0, swingX - ANCHOR_SIZE / 2, swingY - ANCHOR_SIZE / 2, swingX + ANCHOR_SIZE, swingY + ANCHOR_SIZE);
    }
    else {
      // baseline anchor
      int swingWidth = transform.getSwingDimensionDip(myComponent.getDrawWidth());
      int left = swingX - swingWidth / 2 + ANCHOR_SIZE;
      int right = swingX + swingWidth / 2 - ANCHOR_SIZE;
      picker.addRect(this, 0, left, swingY - ANCHOR_SIZE, right, swingY + ANCHOR_SIZE);
    }
  }

  protected boolean isEnabled() {
    Target interactingTarget = myComponent.getScene().getInteractingTarget();
    if (interactingTarget instanceof AnchorTarget) {
      return ((AnchorTarget) interactingTarget).isConnectible(this);
    }
    return true;
  }

  /**
   * Function to determine if the given Target is connectible.
   */
  abstract public boolean isConnectible(@NotNull AnchorTarget dest);

  @NotNull
  protected abstract DrawAnchor.Mode getDrawMode();

  @NotNull
  private DrawAnchor.Type getDrawType() {
    return myType == Type.BASELINE? DrawAnchor.Type.BASELINE : DrawAnchor.Type.NORMAL;
  }

  private boolean getDrawAsConnected() {
    return isConnected() || myIsDragging;
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
  public void mouseDrag(@AndroidDpCoordinate int x,
                        @AndroidDpCoordinate int y,
                        @NotNull List<Target> ignored,
                        @NotNull SceneContext ignored2) {
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
  public void mouseCancel() {
    myLastX = -1;
    myLastY = -1;
    myComponent.getScene().needsRebuildList();
    myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
    myIsDragging = false;
  }

  @Override
  public String getToolTipText() {
    boolean supportRtl = myComponent.getScene().supportsRTL();
    boolean isRtl = myComponent.getScene().isInRTL();
    return createAnchorToolTips(myType, !isConnected(), supportRtl, isRtl);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void fill(@NotNull SceneComponent owner,
                   @NotNull SceneComponent snappableComponent,
                   @NotNull ImmutableList.Builder<Notch> notchBuilder) {
    // TODO: Refactor this condition later.
    if (!isEnabled()) {
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
    notch.setGap(Coordinates.getAndroidDimensionDip(snappableComponent.getScene(), ANCHOR_SIZE * 2));
    notch.setTarget(this);
    notchBuilder.add(notch);
  }

  /**
   * Create the tool tips for anchors.
   *
   * @param type           type of anchor direction. May be null if there is no specified anchor type.
   * @param isCreated      true for having the tool tips of creating constraint anchor, false for deleting.
   * @param isRtlSupported true if left and right type should be treat as start and end depends on {@param isRtl}.
   * @param isRtl          true if the type is in rtl mode. This parameter is meaningless when {@param isSupportRtl} is false.
   *
   */
  @NotNull
  public static String createAnchorToolTips(@Nullable Type type, boolean isCreated, boolean isRtlSupported, boolean isRtl) {
    StringBuilder builder = new StringBuilder();
    builder.append(isCreated ? "Create " : "Delete ");
    if (type != null) {
      String directionText;
      switch (type) {
        case LEFT:
          directionText = isRtlSupported ? isRtl ? "End" : "Start" : "Left";
          break;
        case TOP:
          directionText = "Top";
          break;
        case RIGHT:
          directionText = isRtlSupported ? isRtl ? "Start" : "End" : "Right";
          break;
        case BOTTOM:
          directionText = "Bottom";
          break;
        case BASELINE:
          directionText = "Baseline";
          break;
        default:
          // Logically this shouldn't happen.
          directionText = "";
      }
      builder.append(directionText).append(" ");
    }
    builder.append("Constraint");
    return builder.toString();
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

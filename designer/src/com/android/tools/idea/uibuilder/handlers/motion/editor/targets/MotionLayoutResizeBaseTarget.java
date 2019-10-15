/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.targets;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.handlers.motion.MotionUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.scene.draw.DrawResize;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.Cursor;
import java.util.List;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for resizing targets in MotionLayout.
 *
 * TODO: refactor with ResizeBaseTarget
 */
public abstract class MotionLayoutResizeBaseTarget extends BaseTarget {

  protected final Type myType;

  @AndroidDpCoordinate protected int myStartX1;
  @AndroidDpCoordinate protected int myStartY1;
  @AndroidDpCoordinate protected int myStartX2;
  @AndroidDpCoordinate protected int myStartY2;

  public Type getType() {
    return myType;
  }

  // Type of possible resize handles
  public enum Type {
    LEFT, LEFT_TOP, LEFT_BOTTOM, TOP, BOTTOM, RIGHT, RIGHT_TOP, RIGHT_BOTTOM
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public MotionLayoutResizeBaseTarget(@NotNull Type type) {
    myType = type;
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
    int width = r - l;
    int height = b - t;
    int horizontalCenter = l + width / 2;
    int verticalCenter = t + height / 2;
    switch (myType) {
      case LEFT: {
        myLeft = l;
        myTop = verticalCenter;
      }
      break;
      case TOP: {
        myLeft = horizontalCenter;
        myTop = t;
      }
      break;
      case RIGHT: {
        myLeft = r;
        myTop = verticalCenter;
      }
      break;
      case BOTTOM: {
        myLeft = horizontalCenter;
        myTop = b;
      }
      break;
      case LEFT_TOP: {
        myLeft = l;
        myTop = t;
      }
      break;
      case LEFT_BOTTOM: {
        myLeft = l;
        myTop = b;
      }
      break;
      case RIGHT_TOP: {
        myLeft = r;
        myTop = t;
      }
      break;
      case RIGHT_BOTTOM: {
        myLeft = r;
        myTop = b;
      }
      break;
    }
    myRight = myLeft;
    myBottom = myTop;
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public Cursor getMouseCursor(@JdkConstants.InputEventMask int modifiersEx) {
    switch (myType) {
      case LEFT:
        return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
      case RIGHT:
        return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
      case TOP:
        return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
      case BOTTOM:
        return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
      case LEFT_TOP:
        return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
      case LEFT_BOTTOM:
        return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
      case RIGHT_TOP:
        return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
      case RIGHT_BOTTOM:
        return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
      default:
        return Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    }
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (isHittable()) {
      DrawResize.add(list, sceneContext, myLeft, myTop, mIsOver ? DrawResize.OVER : DrawResize.NORMAL);
    }
  }

  @Override
  public void addHit(@NotNull SceneContext transform,
                     @NotNull ScenePicker picker,
                     @JdkConstants.InputEventMask int modifiersEx) {
    if (isHittable()) {
      int halfSize = DrawResize.SIZE / 2;
      picker.addRect(this, 0, transform.getSwingXDip(myLeft) - halfSize,
                     transform.getSwingYDip(myTop) - halfSize,
                     transform.getSwingXDip(myRight) + halfSize,
                     transform.getSwingYDip(myBottom) + halfSize);
    }
  }

  @Override
  protected boolean isHittable() {
    SceneComponent component = getComponent();
    if (component.getScene().getSelection().size() > 1) {
      // Disable resize target when selecting multiple components.
      return false;
    }
    if (component.isSelected()) {
      if (component.canShowBaseline()) {
        return true;
      }
      return !component.isDragging();
    }
    Scene.FilterType filterType = component.getScene().getFilterType();
    if (filterType == Scene.FilterType.RESIZE || filterType == Scene.FilterType.ALL) {
      return true;
    }
    return false;
  }

  protected abstract void updateAttributes(@NotNull NlAttributesHolder attributes,
                                           @AndroidDpCoordinate int x,
                                           @AndroidDpCoordinate int y);

  protected abstract void updateAttributes(@NotNull MTag.TagWriter attributes,
                                           @AndroidDpCoordinate int x,
                                           @AndroidDpCoordinate int y);

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return Target.RESIZE_LEVEL;
  }

  class Attributes implements NlAttributesHolder {
    @Override
    public void setAttribute(@Nullable String namespace, @NotNull String attribute, @Nullable String value) {

    }

    @Nullable
    @Override
    public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
      return null;
    }
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myStartX1 = myComponent.getDrawX();
    myStartY1 = myComponent.getDrawY();
    myStartX2 = myComponent.getDrawX() + myComponent.getDrawWidth();
    myStartY2 = myComponent.getDrawY() + myComponent.getDrawHeight();
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    MotionLayoutComponentHelper motionLayout = new MotionLayoutComponentHelper(myComponent.getNlComponent().getParent());
    if (!motionLayout.isInTransition()) {
      ComponentModification modification = new ComponentModification(component, "Resize " + StringUtil.getShortName(component.getTagName()));
      updateAttributes(modification, x, y);
      modification.apply();
      myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    MotionLayoutComponentHelper motionLayout = new MotionLayoutComponentHelper(myComponent.getNlComponent().getParent());
    if (!motionLayout.isInTransition()) {
      String state = motionLayout.getState();
      if (state == null) {
        NlComponent component = myComponent.getAuthoritativeNlComponent();
        ComponentModification modification = new ComponentModification(component, "Resize " + StringUtil.getShortName(component.getTagName()));
        updateAttributes(modification, x, y);
        modification.commit();
      } else {
        MTag motionScene = MotionUtils.getMotionScene(myComponent.getNlComponent());
        MTag[] cSet = motionScene.getChildTags("ConstraintSet");
        for (int i = 0; i < cSet.length; i++) {
          MTag set = cSet[i];
          String id = set.getAttributeValue("id");
          id = Utils.stripID(id);
          if (id.equalsIgnoreCase(state)) {
              MTag[] constraints = set.getChildTags(MotionSceneAttrs.Tags.CONSTRAINT);
              for (int j = 0; j < constraints.length; j++) {
                MTag constraint = constraints[j];
                String constraintId = constraint.getAttributeValue("id");
                constraintId = Utils.stripID(constraintId);
                if (constraintId.equalsIgnoreCase(Utils.stripID(myComponent.getId()))) {
                  MTag.TagWriter writer = constraint.getTagWriter();
                  updateAttributes(writer, x, y);
                  writer.commit("Resize component");
                }
              }
            break;
          }
        }
      }
      myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  /**
   * Reset the size and position when mouse resizing is canceled.
   */
  @Override
  public void mouseCancel() {
    myComponent.setPosition(myStartX1, myStartY1);

    // rollback the transaction. The value may be temporarily changed by live rendering.
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.rollback();
    component.fireLiveChangeEvent();

    myComponent.setDragging(false);
    myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public String getToolTipText() {
    return "Resize View";
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
}

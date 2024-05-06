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

import com.android.tools.adtui.common.AdtUiCursorType;
import com.android.tools.adtui.common.AdtUiCursorsProvider;
import com.android.sdklib.AndroidDpCoordinate;
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
import com.android.tools.idea.uibuilder.scene.draw.DrawResize;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.Cursor;
import java.util.List;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for resizing targets.
 */
public abstract class ResizeBaseTarget extends BaseTarget {

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

  public ResizeBaseTarget(@NotNull Type type) {
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
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.W_RESIZE);
      case RIGHT:
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.E_RESIZE);
      case TOP:
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.N_RESIZE);
      case BOTTOM:
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.S_RESIZE);
      case LEFT_TOP:
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.NW_RESIZE);
      case LEFT_BOTTOM:
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.SW_RESIZE);
      case RIGHT_TOP:
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.NE_RESIZE);
      case RIGHT_BOTTOM:
        return AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.SE_RESIZE);
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

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return Target.RESIZE_LEVEL;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myStartX1 = myComponent.getDrawX();
    myStartY1 = myComponent.getDrawY();
    myStartX2 = myComponent.getDrawX() + myComponent.getDrawWidth();
    myStartY2 = myComponent.getDrawY() + myComponent.getDrawHeight();
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x,
                        @AndroidDpCoordinate int y,
                        @NotNull List<Target> closestTargets,
                        @NotNull SceneContext ignored) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    updateAttributes(attributes, x, y);
    attributes.apply();
    myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    ComponentModification modification = new ComponentModification(component, "Resize " + StringUtil.getShortName(component.getTagName()));
    updateAttributes(modification, x, y);
    modification.commit();
    myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
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

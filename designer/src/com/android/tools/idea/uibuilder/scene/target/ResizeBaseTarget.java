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

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.scene.draw.DrawResize;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Base class for resizing targets.
 */
public abstract class ResizeBaseTarget extends BaseTarget {

  protected final Type myType;
  protected final int mySize = 2;

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
    float ratio = 1f / (float)sceneTransform.getScale();
    if (ratio > 2) {
      ratio = 2;
    }
    float size = (mySize * ratio);
    float minWidth = 4 * size;
    float minHeight = 4 * size;
    if (r - l < minWidth) {
      float d = (minWidth - (r - l)) / 2f;
      l -= d;
      r += d;
    }
    if (b - t < minHeight) {
      float d = (minHeight - (b - t)) / 2f;
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
      }
      break;
      case TOP: {
        myLeft = mw - size;
        myTop = t - size;
        myRight = mw + size;
        myBottom = t + size;
      }
      break;
      case RIGHT: {
        myLeft = r - size;
        myTop = mh - size;
        myRight = r + size;
        myBottom = mh + size;
      }
      break;
      case BOTTOM: {
        myLeft = mw - size;
        myTop = b - size;
        myRight = mw + size;
        myBottom = b + size;
      }
      break;
      case LEFT_TOP: {
        myLeft = l - size;
        myTop = t - size;
        myRight = l + size;
        myBottom = t + size;
      }
      break;
      case LEFT_BOTTOM: {
        myLeft = l - size;
        myTop = b - size;
        myRight = l + size;
        myBottom = b + size;
      }
      break;
      case RIGHT_TOP: {
        myLeft = r - size;
        myTop = t - size;
        myRight = r + size;
        myBottom = t + size;
      }
      break;
      case RIGHT_BOTTOM: {
        myLeft = r - size;
        myTop = b - size;
        myRight = r + size;
        myBottom = b + size;
      }
      break;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public Cursor getMouseCursor() {
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
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }

    DrawResize.add(list, sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? DrawResize.OVER : DrawResize.NORMAL);
  }

  protected abstract void updateAttributes(@NotNull AttributesTransaction attributes,
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
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    updateAttributes(attributes, x, y);
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    updateAttributes(attributes, x, y);
    attributes.apply();

    NlWriteCommandAction.run(component, "Resize " + StringUtil.getShortName(component.getTagName()), attributes::commit);
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public String getToolTipText() {
    return "Resize View";
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
}

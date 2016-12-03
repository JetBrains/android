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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawResize;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implements a resize handle for the ConstraintLayout viewgroup
 */
public class ResizeTarget extends BaseTarget {

  private final Type myType;
  private final int mySize = 3;

  private int mStartX1;
  private int mStartY1;
  private int mStartX2;
  private int mStartY2;

  public Type getType() {
    return myType;
  }

  // Type of possible resize handles
  public enum Type { LEFT, LEFT_TOP, LEFT_BOTTOM, TOP, BOTTOM, RIGHT, RIGHT_TOP, RIGHT_BOTTOM }

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public ResizeTarget(@NotNull Type type) {
    myType = type;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    float ratio = 1f / (float) sceneTransform.getScale();
    if (ratio > 2) {
      ratio = 2;
    }
    float size = (mySize * ratio);
    float minWidth =  4 * size;
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
      /* unused for the moment
      case LEFT: {
        myLeft = l - size;
        myTop = mh - size;
        myRight = l + size;
        myBottom = mh + size;
      } break;
      case TOP: {
        myLeft = mw - size;
        myTop = t - size;
        myRight = mw + size;
        myBottom = t + size;
      } break;
      case RIGHT: {
        myLeft = r - size;
        myTop = mh - size;
        myRight = r + size;
        myBottom = mh + size;
      } break;
      case BOTTOM: {
        myLeft = mw - size;
        myTop = b - size;
        myRight = mw + size;
        myBottom = b + size;
      } break;
      */
      case LEFT_TOP: {
        myLeft = l - size;
        myTop = t - size;
        myRight = l + size;
        myBottom = t + size;
      } break;
      case LEFT_BOTTOM: {
        myLeft = l - size;
        myTop = b - size;
        myRight = l + size;
        myBottom = b + size;
      } break;
      case RIGHT_TOP: {
        myLeft = r - size;
        myTop = t - size;
        myRight = r + size;
        myBottom = t + size;
      } break;
      case RIGHT_BOTTOM: {
        myLeft = r - size;
        myTop = b - size;
        myRight = r + size;
        myBottom = b + size;
      } break;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getMouseCursor() {
    switch (myType) {
      case LEFT_TOP:
        return Cursor.NW_RESIZE_CURSOR;
      case LEFT_BOTTOM:
        return Cursor.SW_RESIZE_CURSOR;
      case RIGHT_TOP:
        return Cursor.NE_RESIZE_CURSOR;
      case RIGHT_BOTTOM:
        return Cursor.SE_RESIZE_CURSOR;
    }
    return Cursor.DEFAULT_CURSOR;
  }

  @Override
  public void render(@NotNull DisplayList list, SceneContext sceneContext) {
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }

    DrawResize.add(list, sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? DrawResize.OVER : DrawResize.NORMAL);
  }

  private void updateWidth(@NotNull AttributesTransaction attributes, int w) {
    if (w < 0) {
      w = 0;
    }
    String position = String.format(SdkConstants.VALUE_N_DP, w);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH, position);
  }

  private void updateHeight(@NotNull AttributesTransaction attributes, int h) {
    if (h < 0) {
      h = 0;
    }
    String position = String.format(SdkConstants.VALUE_N_DP, h);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT, position);
  }

  private void updatePositionX(@NotNull AttributesTransaction attributes, int x) {
    String positionX = String.format(SdkConstants.VALUE_N_DP, x);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
  }

  private void updatePositionY(@NotNull AttributesTransaction attributes, int y) {
    String positionY = String.format(SdkConstants.VALUE_N_DP, y);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, positionY);
  }

  private void updateAttributes(@NotNull AttributesTransaction attributes, int x, int y) {
    switch (myType) {
      case RIGHT_TOP: {
        updateWidth(attributes, x - mStartX1);
        updatePositionY(attributes, y - myComponent.getParent().getDrawY());
        updateHeight(attributes, mStartY2 - y);
      } break;
      case RIGHT_BOTTOM: {
        updateWidth(attributes, x - mStartX1);
        updateHeight(attributes, y - mStartY1);
      } break;
      case LEFT_TOP: {
        updatePositionX(attributes, x - myComponent.getParent().getDrawX());
        updateWidth(attributes, mStartX2 - x);
        updatePositionY(attributes, y - myComponent.getParent().getDrawY());
        updateHeight(attributes, mStartY2 - y);
      } break;
      case LEFT_BOTTOM: {
        updatePositionX(attributes, x - myComponent.getParent().getDrawX());
        updateWidth(attributes, mStartX2 - x);
        updateHeight(attributes, y - mStartY1);
      } break;
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return 10;
  }

  @Override
  public void mouseDown(int x, int y) {
    mStartX1 = myComponent.getDrawX();
    mStartY1 = myComponent.getDrawY();
    mStartX2 = myComponent.getDrawX() + myComponent.getDrawWidth();
    mStartY2 = myComponent.getDrawY() + myComponent.getDrawHeight();
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    updateAttributes(attributes, x, y);
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    updateAttributes(attributes, x, y);
    attributes.apply();

    NlModel nlModel = component.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Constraint";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        attributes.commit();
      }
    };
    action.execute();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}

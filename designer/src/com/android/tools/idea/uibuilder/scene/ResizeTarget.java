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
package com.android.tools.idea.uibuilder.scene;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
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
public class ResizeTarget implements Target {

  private SceneComponent myComponent;
  private final Type myType;
  private final int mySize = 8;
  private boolean mIsOver = false;

  private int myLeft = 0;
  private int myTop = 0;
  private int myRight = 0;
  private int myBottom = 0;
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
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void setOver(boolean over) {
    mIsOver = over;
  }

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

  public int getCenterX() {
    return myLeft + (myRight - myLeft) / 2;
  }

  public int getCenterY() {
    return myTop + (myBottom - myTop) / 2;
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(int l, int t, int r, int b) {
    int minWidth = 4 * mySize;
    int minHeight = 4 * mySize;
    if (r - l < minWidth) {
      int d = (minWidth - (r - l)) / 2;
      l -= d;
      r += d;
    }
    if (b - t < minHeight) {
      int d = (minHeight - (b - t)) / 2;
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
        myLeft = l - mySize;
        myTop = mh - mySize;
        myRight = l + mySize;
        myBottom = mh + mySize;
      } break;
      case TOP: {
        myLeft = mw - mySize;
        myTop = t - mySize;
        myRight = mw + mySize;
        myBottom = t + mySize;
      } break;
      case RIGHT: {
        myLeft = r - mySize;
        myTop = mh - mySize;
        myRight = r + mySize;
        myBottom = mh + mySize;
      } break;
      case BOTTOM: {
        myLeft = mw - mySize;
        myTop = b - mySize;
        myRight = mw + mySize;
        myBottom = b + mySize;
      } break;
      */
      case LEFT_TOP: {
        myLeft = l - mySize;
        myTop = t - mySize;
        myRight = l + mySize;
        myBottom = t + mySize;
      } break;
      case LEFT_BOTTOM: {
        myLeft = l - mySize;
        myTop = b - mySize;
        myRight = l + mySize;
        myBottom = b + mySize;
      } break;
      case RIGHT_TOP: {
        myLeft = r - mySize;
        myTop = t - mySize;
        myRight = r + mySize;
        myBottom = t + mySize;
      } break;
      case RIGHT_BOTTOM: {
        myLeft = r - mySize;
        myTop = b - mySize;
        myRight = r + mySize;
        myBottom = b + mySize;
      } break;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void render(@NotNull DisplayList list) {
    list.addRect(myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.blue);
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

  private void updateAttributes(@NotNull AttributesTransaction attributes, int sx, int sy) {
    int x = sx - myComponent.getParent().getDrawX();
    int y = sy - myComponent.getParent().getDrawY();
    switch (myType) {
      case RIGHT_TOP: {
        updateWidth(attributes, x - mStartX1);
        updatePositionY(attributes, y);
        updateHeight(attributes, mStartY2 - sy);
      } break;
      case RIGHT_BOTTOM: {
        updateWidth(attributes, x - mStartX1);
        updateHeight(attributes, sy - mStartY1);
      } break;
      case LEFT_TOP: {
        updatePositionX(attributes, x);
        updateWidth(attributes, mStartX2 - x);
        updatePositionY(attributes, y);
        updateHeight(attributes, mStartY2 - sy);
      } break;
      case LEFT_BOTTOM: {
        updatePositionX(attributes, x);
        updateWidth(attributes, mStartX2 - x);
        updateHeight(attributes, sy - mStartY1);
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
  public void addHit(@NotNull ScenePicker picker) {
    mIsOver = false;
    picker.addRect(this, 0, myLeft, myTop, myRight, myBottom);
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

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

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(int l, int t, int r, int b) {
    int w = r - l;
    int h = b - t;
    int mw = l + w / 2;
    int mh = t + h / 2;
    switch (myType) {
      case LEFT: {
        myLeft = l - mySize;
        myTop = mh - mySize;
        myRight = l + mySize;
        myBottom = mh + mySize;
      } break;
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
      case BOTTOM: {
        myLeft = mw - mySize;
        myTop = b - mySize;
        myRight = mw + mySize;
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

  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}

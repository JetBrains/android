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
package com.android.tools.idea.uibuilder.handlers.absolute;

import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

public class AbsoluteResizeTarget extends ResizeBaseTarget {

  public AbsoluteResizeTarget(@NotNull Type type) {
    super(type);
  }

  private static void updateXPos(@NotNull AttributesTransaction attributes, int x) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_X, String.format(VALUE_N_DP, Math.max(x, 0)));
  }

  private static void updateYPos(@NotNull AttributesTransaction attributes, int y) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_Y, String.format(VALUE_N_DP, Math.max(y, 0)));
  }

  private static void updateWidth(@NotNull AttributesTransaction attributes, int width) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, String.format(VALUE_N_DP, Math.max(width, 1)));
  }

  private static void updateHeight(@NotNull AttributesTransaction attributes, int height) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, String.format(VALUE_N_DP, Math.max(height, 1)));
  }

  private void updateLeftEdge(@NotNull AttributesTransaction attributes, int x) {
    updateXPos(attributes, Math.min(x, myStartX2) - myComponent.getParent().getDrawX());
    updateWidth(attributes, Math.abs(myStartX2 - x));
  }

  private void updateRightEdge(@NotNull AttributesTransaction attributes, int x) {
    updateXPos(attributes, Math.min(x, myStartX1) - myComponent.getParent().getDrawX());
    updateWidth(attributes, Math.abs(myStartX1 - x));
  }

  private void updateTopEdge(@NotNull AttributesTransaction attributes, int y) {
    updateYPos(attributes, Math.min(y, myStartY2) - myComponent.getParent().getDrawY());
    updateHeight(attributes, Math.abs(myStartY2 - y));
  }

  private void updateBottomEdge(@NotNull AttributesTransaction attributes, int y) {
    updateYPos(attributes, Math.min(y, myStartY1) - myComponent.getParent().getDrawY());
    updateHeight(attributes, Math.abs(myStartY1 - y));
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, int x, int y) {
    switch (myType) {
      case LEFT:
        updateLeftEdge(attributes, x);
        break;
      case TOP:
        updateTopEdge(attributes, y);
        break;
      case RIGHT:
        updateRightEdge(attributes, x);
        break;
      case BOTTOM:
        updateBottomEdge(attributes, y);
        break;
      case LEFT_TOP:
        updateLeftEdge(attributes, x);
        updateTopEdge(attributes, y);
        break;
      case LEFT_BOTTOM:
        updateLeftEdge(attributes, x);
        updateBottomEdge(attributes, y);
        break;
      case RIGHT_TOP:
        updateRightEdge(attributes, x);
        updateTopEdge(attributes, y);
        break;
      case RIGHT_BOTTOM:
        updateRightEdge(attributes, x);
        updateBottomEdge(attributes, y);
        break;
    }
  }
}

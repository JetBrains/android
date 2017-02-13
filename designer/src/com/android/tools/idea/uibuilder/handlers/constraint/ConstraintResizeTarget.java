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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import org.jetbrains.annotations.NotNull;

public class ConstraintResizeTarget extends ResizeBaseTarget {

  public ConstraintResizeTarget(@NotNull Type type) {
    super(type);
  }

  private static void updateWidth(@NotNull AttributesTransaction attributes, int w) {
    if (w < 0) {
      w = 0;
    }
    String position = String.format(SdkConstants.VALUE_N_DP, w);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH, position);
  }

  private static void updateHeight(@NotNull AttributesTransaction attributes, int h) {
    if (h < 0) {
      h = 0;
    }
    String position = String.format(SdkConstants.VALUE_N_DP, h);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT, position);
  }

  private static void updatePositionX(@NotNull AttributesTransaction attributes, int x) {
    String positionX = String.format(SdkConstants.VALUE_N_DP, x);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
  }

  private static void updatePositionY(@NotNull AttributesTransaction attributes, int y) {
    String positionY = String.format(SdkConstants.VALUE_N_DP, y);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, positionY);
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, int x, int y) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myType) {
      case RIGHT_TOP: {
        updateWidth(attributes, x - myStartX1);
        updatePositionY(attributes, y - myComponent.getParent().getDrawY());
        updateHeight(attributes, myStartY2 - y);
      } break;
      case RIGHT_BOTTOM: {
        updateWidth(attributes, x - myStartX1);
        updateHeight(attributes, y - myStartY1);
      } break;
      case LEFT_TOP: {
        updatePositionX(attributes, x - myComponent.getParent().getDrawX());
        updateWidth(attributes, myStartX2 - x);
        updatePositionY(attributes, y - myComponent.getParent().getDrawY());
        updateHeight(attributes, myStartY2 - y);
      } break;
      case LEFT_BOTTOM: {
        updatePositionX(attributes, x - myComponent.getParent().getDrawX());
        updateWidth(attributes, myStartX2 - x);
        updateHeight(attributes, y - myStartY1);
      } break;
    }
  }
}
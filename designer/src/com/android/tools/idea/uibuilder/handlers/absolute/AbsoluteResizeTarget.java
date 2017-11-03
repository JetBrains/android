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

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.target.ResizeWithSnapBaseTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

public class AbsoluteResizeTarget extends ResizeWithSnapBaseTarget {

  public AbsoluteResizeTarget(@NotNull Type type) {
    super(type);
  }

  private static void updateXPos(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int x) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_X, String.format(VALUE_N_DP, Math.max(x, 0)));
  }

  private static void updateYPos(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int y) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_Y, String.format(VALUE_N_DP, Math.max(y, 0)));
  }

  protected static void updateWidth(@NotNull AttributesTransaction attributes, @NotNull String width) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, width);
  }

  protected static void updateHeight(@NotNull AttributesTransaction attributes, @NotNull String height) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, height);
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    switch (myType) {
      case LEFT:
      case RIGHT:
        updateXPos(attributes, getNewXPos(x));
        updateWidth(attributes, getNewWidth(x));
        break;
      case TOP:
      case BOTTOM:
        updateYPos(attributes, getNewYPos(y));
        updateHeight(attributes, getNewHeight(y));
        break;
      case LEFT_TOP:
      case LEFT_BOTTOM:
      case RIGHT_TOP:
      case RIGHT_BOTTOM:
        updateXPos(attributes, getNewXPos(x));
        updateYPos(attributes, getNewYPos(y));
        updateWidth(attributes, getNewWidth(x));
        updateHeight(attributes, getNewHeight(y));
        break;
    }
  }
}

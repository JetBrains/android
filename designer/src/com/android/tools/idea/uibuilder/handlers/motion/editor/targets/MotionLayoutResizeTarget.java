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

import com.android.SdkConstants;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * ResizeTarget for MotionLayout
 *
 * TODO: Refactor with ConstraintResizeTarget
 */
public class MotionLayoutResizeTarget extends MotionLayoutResizeBaseTarget {

  public MotionLayoutResizeTarget(@NotNull Type type) {
    super(type);
  }

  @Override
  public int getPreferenceLevel() {
    if (myType == Type.LEFT
        || myType == Type.RIGHT
        || myType == Type.TOP
        || myType == Type.BOTTOM) {
      return Target.SIDE_RESIZE_LEVEL;
    }
    return Target.RESIZE_LEVEL;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myType == Type.LEFT
      || myType == Type.RIGHT
      || myType == Type.TOP
      || myType == Type.BOTTOM) {
      return;
    }

    NlComponent parent = myComponent.getNlComponent().getParent();
    if (parent != null) {
      MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(parent);
      if (motionLayout.isInTransition()) {
        return;
      }
    }

    super.render(list, sceneContext);
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  private static void updateWidth(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int w) {
    if (w < 0) {
      w = 0;
    }
    String position = String.format(Locale.US, SdkConstants.VALUE_N_DP, w);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH, position);
  }

  private static void updateHeight(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int h) {
    if (h < 0) {
      h = 0;
    }
    String position = String.format(Locale.US, SdkConstants.VALUE_N_DP, h);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT, position);
  }

  private static void updatePositionX(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int x) {
    String positionX = String.format(Locale.US, SdkConstants.VALUE_N_DP, x);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
  }

  private static void updatePositionY(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int y) {
    String positionY = String.format(Locale.US, SdkConstants.VALUE_N_DP, y);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, positionY);
  }

  @Override
  protected void updateAttributes(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
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
      case LEFT:
        updatePositionX(attributes, x - myComponent.getParent().getDrawX());
        updateWidth(attributes, myStartX2 - x);
        break;
      case TOP:
        updatePositionY(attributes, y - myComponent.getParent().getDrawY());
        updateHeight(attributes, myStartY2 - y);
        break;
      case BOTTOM:
        updateHeight(attributes, y - myStartY1);
        break;
      case RIGHT:
        updateWidth(attributes, x - myStartX1);
        break;
    }
  }

  private static void updateWidth(@NotNull MTag.TagWriter attributes, @AndroidDpCoordinate int w) {
    if (w < 0) {
      w = 0;
    }
    String position = String.format(Locale.US, SdkConstants.VALUE_N_DP, w);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH, position);
  }

  private static void updateHeight(@NotNull MTag.TagWriter attributes, @AndroidDpCoordinate int h) {
    if (h < 0) {
      h = 0;
    }
    String position = String.format(Locale.US, SdkConstants.VALUE_N_DP, h);
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT, position);
  }

  private static void updatePositionX(@NotNull MTag.TagWriter attributes, @AndroidDpCoordinate int x) {
    String positionX = String.format(Locale.US, SdkConstants.VALUE_N_DP, x);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
  }

  private static void updatePositionY(@NotNull MTag.TagWriter attributes, @AndroidDpCoordinate int y) {
    String positionY = String.format(Locale.US, SdkConstants.VALUE_N_DP, y);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, positionY);
  }

  @Override
  protected void updateAttributes(@NotNull MTag.TagWriter attributes, int x, int y) {
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
      case LEFT:
        updatePositionX(attributes, x - myComponent.getParent().getDrawX());
        updateWidth(attributes, myStartX2 - x);
        break;
      case TOP:
        updatePositionY(attributes, y - myComponent.getParent().getDrawY());
        updateHeight(attributes, myStartY2 - y);
        break;
      case BOTTOM:
        updateHeight(attributes, y - myStartY1);
        break;
      case RIGHT:
        updateWidth(attributes, x - myStartX1);
        break;
    }
  }
}
/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import org.jetbrains.annotations.NotNull;

public enum CustomAttributeType {

  CUSTOM_STRING("String", MotionSceneAttrs.ATTR_CUSTOM_STRING_VALUE, "Example"),
  CUSTOM_BOOLEAN("Boolean", MotionSceneAttrs.ATTR_CUSTOM_BOOLEAN_VALUE, "true"),
  CUSTOM_COLOR("Color", MotionSceneAttrs.ATTR_CUSTOM_COLOR_VALUE, "#FFFFFF"),
  CUSTOM_COLOR_DRAWABLE("ColorDrawable", MotionSceneAttrs.ATTR_CUSTOM_COLOR_DRAWABLE_VALUE, "#FFFFFF"),
  CUSTOM_DIMENSION("Dimension", MotionSceneAttrs.ATTR_CUSTOM_DIMENSION_VALUE, "20dp"),
  CUSTOM_FLOAT("Float", MotionSceneAttrs.ATTR_CUSTOM_FLOAT_VALUE, "1.0"),
  CUSTOM_INTEGER("Integer", MotionSceneAttrs.ATTR_CUSTOM_INTEGER_VALUE, "2"),
  CUSTOM_PIXEL_DIMENSION("PixelDimension", MotionSceneAttrs.ATTR_CUSTOM_PIXEL_DIMENSION_VALUE, "20px");

  private final String myStringValue;
  private final String myTagName;
  private final String myDefaultValue;

  @NotNull
  public String getTagName() {
    return myTagName;
  }

  @NotNull
  public String getDefaultValue() {
    return myDefaultValue;
  }

  @Override
  public String toString() {
    return myStringValue;
  }

  CustomAttributeType(@NotNull String stringValue, @NotNull String tagName, @NotNull String defaultValue) {
    myStringValue = stringValue;
    myTagName = tagName;
    myDefaultValue = defaultValue;
  }
}

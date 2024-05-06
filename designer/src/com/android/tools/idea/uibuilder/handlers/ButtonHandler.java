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
package com.android.tools.idea.uibuilder.handlers;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

public class ButtonHandler extends TextViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_STATE_LIST_ANIMATOR,
      ATTR_ON_CLICK,
      ATTR_ELEVATION,
      ATTR_INSET_LEFT,
      ATTR_INSET_RIGHT,
      ATTR_INSET_TOP,
      ATTR_INSET_BOTTOM,
      ATTR_BACKGROUND,
      ATTR_BACKGROUND_TINT,
      ATTR_BACKGROUND_TINT_MODE,
      ATTR_ICON,
      ATTR_ICON_PADDING,
      ATTR_ICON_TINT,
      ATTR_ICON_TINT_MODE,
      ATTR_ADDITIONAL_PADDING_START_FOR_ICON,
      ATTR_ADDITIONAL_PADDING_END_FOR_ICON,
      ATTR_STROKE_COLOR,
      ATTR_STROKE_WIDTH,
      ATTR_CORNER_RADIUS,
      ATTR_RIPPLE_COLOR);
  }
}

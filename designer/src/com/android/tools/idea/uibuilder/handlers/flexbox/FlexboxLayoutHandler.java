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

package com.android.tools.idea.uibuilder.handlers.flexbox;

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handles interactions for the FlexboxLayout.
 */
public class FlexboxLayoutHandler extends ViewGroupHandler {

  public static final boolean FLEXBOX_ENABLE_FLAG = false;

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_FLEX_DIRECTION,
      ATTR_FLEX_WRAP,
      ATTR_JUSTIFY_CONTENT,
      ATTR_ALIGN_ITEMS,
      ATTR_ALIGN_CONTENT);
  }

  @Override
  @NotNull
  public List<String> getLayoutInspectorProperties() {
    return ImmutableList.of(
      ATTR_LAYOUT_ORDER,
      ATTR_LAYOUT_FLEX_GROW,
      ATTR_LAYOUT_FLEX_SHRINK,
      ATTR_LAYOUT_ALIGN_SELF,
      ATTR_LAYOUT_FLEX_BASIS_PERCENT,
      ATTR_LAYOUT_WRAP_BEFORE);
  }

  @Override
  @NotNull
  public String getGradleCoordinateId(@NotNull String viewTag) {
    return FLEXBOX_LAYOUT_LIB_ARTIFACT;
  }

  // TODO: Override createDragHandler
}

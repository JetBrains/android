/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_ADJUST_VIEW_BOUNDS;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_BACKGROUND_TINT;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_CROP_TO_PADDING;
import static com.android.SdkConstants.ATTR_ELEVATION;
import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.ATTR_SCALE_TYPE;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.ATTR_TINT;
import static com.android.SdkConstants.ATTR_VISIBILITY;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for the {@code <ImageButton>} widget
 */
public class ImageButtonHandler extends ImageViewHandler {

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SRC,
      TOOLS_NS_NAME_PREFIX + ATTR_SRC,
      ATTR_CONTENT_DESCRIPTION,
      ATTR_STYLE,
      ATTR_TINT,
      ATTR_BACKGROUND,
      ATTR_BACKGROUND_TINT,
      ATTR_SCALE_TYPE,
      ATTR_ELEVATION,
      ATTR_ON_CLICK,
      ATTR_ADJUST_VIEW_BOUNDS,
      ATTR_CROP_TO_PADDING,
      ATTR_VISIBILITY);
  }

  @Override
  @NotNull
  public String getSampleImageSrc() {
    // Builtin graphics available since v1:
    return "@android:drawable/btn_star"; //$NON-NLS-1$
  }
}

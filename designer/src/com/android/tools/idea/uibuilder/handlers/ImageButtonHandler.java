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

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <ImageButton>} widget
 */
public class ImageButtonHandler extends ImageViewHandler {

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SRC,
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

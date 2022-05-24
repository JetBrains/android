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

import static com.android.SdkConstants.ATTR_BACKGROUND_TINT;
import static com.android.SdkConstants.ATTR_COMPLETION_HINT;
import static com.android.SdkConstants.ATTR_DROPDOWN_HEIGHT;
import static com.android.SdkConstants.ATTR_DROPDOWN_WIDTH;
import static com.android.SdkConstants.ATTR_IME_OPTIONS;
import static com.android.SdkConstants.ATTR_POPUP_BACKGROUND;
import static com.android.SdkConstants.ATTR_STYLE;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AutoCompleteTextViewHandler extends TextViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_COMPLETION_HINT,
      ATTR_STYLE,
      ATTR_BACKGROUND_TINT,
      ATTR_POPUP_BACKGROUND,
      ATTR_DROPDOWN_HEIGHT,
      ATTR_DROPDOWN_WIDTH,
      ATTR_IME_OPTIONS);
  }
}

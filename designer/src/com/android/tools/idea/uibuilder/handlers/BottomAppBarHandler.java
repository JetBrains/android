/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

public class BottomAppBarHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_BACKGROUND_TINT,
      ATTR_FAB_ALIGNMENT_MODE,
      ATTR_FAB_ANIMATION_MODE,
      ATTR_FAB_CRADLE_MARGIN,
      ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS,
      ATTR_FAB_CRADLE_VERTICAL_OFFSET);
  }
}

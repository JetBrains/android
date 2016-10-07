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
package com.android.tools.idea.uibuilder.api;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Methods for handling of a component's properties.
 */
public abstract class PropertyComponentHandler extends PaletteComponentHandler {
  /**
   * Return the properties that should be shown in the inspector for this component.
   */
  @NotNull
  public List<String> getInspectorProperties() {
    return Collections.emptyList();
  }

  /**
   * Return the possible base styles for this component.
   * This will be used to determine which styles are shown in the style dropdown in
   * the property table if any.
   * If the styles returned here exist, these styles and all their descendants will
   * be considered for the style dropdown.
   */
  @NotNull
  public List<String> getBaseStyles(@NotNull String tagName) {
    String simpleTagName = getSimpleTagName(tagName);
    if (tagName.startsWith(ANDROID_SUPPORT_DESIGN_PKG)) {
      return ImmutableList.of("Widget.Design." + simpleTagName);
    }
    if (tagName.equals(simpleTagName)) {
      return ImmutableList.of("Widget." + simpleTagName, "Widget.Material." + simpleTagName);
    }
    return ImmutableList.of();
  }
}

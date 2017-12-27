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

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_SUPPORT_DESIGN_PKG;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Methods for handling of a component's properties.
 */
public abstract class PropertyComponentHandler extends PaletteComponentHandler {
  /**
   * Return the properties that should be shown in the inspector for this component.
   * If a property is prefixed with "tools:" then the property must be in the
   * {@link SdkConstants#TOOLS_URI} namespace.
   * Otherwise the following namespaces are checked in order:
   * {@link SdkConstants#AUTO_URI}, {@link SdkConstants#ANDROID_URI}, {@link SdkConstants#TOOLS_URI}.
   */
  @NotNull
  public List<String> getInspectorProperties() {
    return Collections.emptyList();
  }

  /**
   * @return the properties that should be shown in the inspector for a child of this component.
   */
  @NotNull
  public List<String> getLayoutInspectorProperties() {
    return Collections.emptyList();
  }

  /**
   * Return the values that should be presented in the inspector and property table for a given property name.
   * If specified this would override the values determined by the system.
   * The values must be specified in pairs: xmlValue -> displayValue in the order they should appear.
   * Warning: Use a map that preserves the order for the value map.
   */
  @NotNull
  public Map<String, Map<String, String>> getEnumPropertyValues(@SuppressWarnings("unused") @NotNull NlComponent component) {
    return Collections.emptyMap();
  }

  /**
   * Return the preferred property that the user usually would edit the most
   * for the given component (may be null).
   */
  @Nullable
  public String getPreferredProperty() {
    return ATTR_ID;
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

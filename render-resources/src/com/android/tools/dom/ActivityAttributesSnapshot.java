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
package com.android.tools.dom;

import com.android.ide.common.rendering.api.ResourceValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public final class ActivityAttributesSnapshot {
  @NotNull private final Element myElement;
  @Nullable private final ResourceValue myIcon;
  @Nullable private final ResourceValue myLabel;
  @NotNull private final String myName;
  @Nullable private final String myParentActivity;
  @Nullable private final String myTheme;
  @Nullable private final String myUiOptions;

  public ActivityAttributesSnapshot(@NotNull Element element,
                             @Nullable ResourceValue icon,
                             @Nullable ResourceValue label,
                             @NotNull String name,
                             @Nullable String parentActivity,
                             @Nullable String theme, @Nullable String uiOptions) {
    myElement = element;
    myIcon = icon;
    myLabel = label;
    myName = name;
    myParentActivity = parentActivity;
    myTheme = theme;
    myUiOptions = uiOptions;
  }

  @Nullable
  public ResourceValue getIcon() {
    return myIcon;
  }

  @Nullable
  public ResourceValue getLabel() {
    return myLabel;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public String getParentActivity() {
    return myParentActivity;
  }

  @Nullable
  public String getTheme() {
    return myTheme;
  }

  @Nullable
  public String getUiOptions() {
    return myUiOptions;
  }

  @NotNull
  public Element getElement() {
    return myElement;
  }
}

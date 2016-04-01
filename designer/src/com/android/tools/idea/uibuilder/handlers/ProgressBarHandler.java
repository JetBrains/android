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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.util.text.StringUtil;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <ProgressBar>} widget
 */
public class ProgressBarHandler extends ViewHandler {
  private static final String DOT_PROGRESS_BAR_DOT = ".ProgressBar.";
  private static final String PROGRESS_BAR_STYLE = "progressBarStyle";
  private static final String LARGE = "Large";
  private static final String SMALL = "Small";
  private static final String HORIZONTAL = "Horizontal";

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    String style = getStyle(component);
    return StringUtil.isEmpty(style) ? "" : "(" + style + ")";
  }

  /**
   * Returns either (Large, Small, Normal) depending on the style of the ProgressBar.
   * @param component the node to find the progress bar style from
   * @return either (Large, Small, Normal)
   */
  @Nullable
  protected String getStyle(@NotNull NlComponent component) {
    String style = component.getAttribute(null, TAG_STYLE);
    if (style == null) {
      return null;
    }
    if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
      int index = style.indexOf(DOT_PROGRESS_BAR_DOT);
      return findProgressBarType(style.substring(index + DOT_PROGRESS_BAR_DOT.length()));
    }
    if (style.startsWith(ANDROID_THEME_PREFIX)) {
      int index = style.indexOf(PROGRESS_BAR_STYLE);
      return findProgressBarType(style.substring(index + PROGRESS_BAR_STYLE.length()));
    }
    return null;
  }

  @Nullable
  private static String findProgressBarType(@NotNull String style) {
    if (style.startsWith(LARGE)) {
      return LARGE;
    }
    if (style.startsWith(SMALL)) {
      return SMALL;
    }
    if (style.startsWith(HORIZONTAL)) {
      return HORIZONTAL;
    }
    return null;
  }
}

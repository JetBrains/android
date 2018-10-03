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

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_THEME_PREFIX;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_INDETERMINATE;
import static com.android.SdkConstants.ATTR_MAXIMUM;
import static com.android.SdkConstants.ATTR_PROGRESS;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.PROGRESS_BAR;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_STYLE;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import icons.StudioIcons;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for the {@code <ProgressBar>} widget
 */
public class ProgressBarHandler extends ViewHandler {
  private static final String DOT_PROGRESS_BAR_DOT = ".ProgressBar.";
  private static final String PROGRESS_BAR_STYLE = "progressBarStyle";

  private static final String LARGE = "Large";
  private static final String SMALL = "Small";
  private static final String HORIZONTAL = "Horizontal";

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_CONTENT_DESCRIPTION,
      ATTR_STYLE,
      ATTR_MAXIMUM,
      ATTR_PROGRESS,
      ATTR_INDETERMINATE);
  }

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    String style = getStyle(component);
    return StringUtil.isEmpty(style) ? "" : "(" + style + ")";
  }

  /**
   * Returns either {@link #LARGE}, {@link #SMALL}, {@link #HORIZONTAL}, or null(default) depending on the style of the ProgressBar.
   * @param component the node to find the progress bar style from
   * @return either ({@link #LARGE}, {@link #SMALL}, {@link #HORIZONTAL}, null)
   */
  @Nullable
  private static String getStyle(@NotNull NlComponent component) {
    String style = component.getAttribute(null, TAG_STYLE);
    if (style == null) {
      return null;
    }
    if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
      return findProgressBarType(style, DOT_PROGRESS_BAR_DOT);
    }
    if (style.startsWith(ANDROID_THEME_PREFIX)) {
      return findProgressBarType(style, PROGRESS_BAR_STYLE);
    }
    if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
      return findProgressBarType(style, DOT_PROGRESS_BAR_DOT);
    }
    return null;
  }


  /**
   * Find the type of the progress bar from the provided style once the given prefix has been removed.
   * If the prefix is not found, the method returns null.
   */
  @Nullable
  private static String findProgressBarType(@NotNull String style, @NotNull String prefix) {
    int index = style.indexOf(prefix);
    return index >= 0 ? findProgressBarType(style.substring(index + prefix.length())) : null;
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

  @NotNull
  @Override
  public Icon getIcon(@NotNull NlComponent component) {
    if (!component.getTagName().equals(PROGRESS_BAR)) {
      return super.getIcon(component);
    }
    return HORIZONTAL.equals(getStyle(component)) ? StudioIcons.LayoutEditor.Palette.PROGRESS_BAR_HORIZONTAL
                                                  : StudioIcons.LayoutEditor.Palette.PROGRESS_BAR;
  }
}

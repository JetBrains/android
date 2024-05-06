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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class SeekBarHandler extends ViewHandler {
  private static final String DOT_SEEK_BAR_DOT = ".SeekBar.";
  private static final String SEEK_BAR_STYLE = "seekBarStyle";

  private static final String DISCRETE = "Discrete";

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_THUMB,
      ATTR_MAXIMUM,
      ATTR_PROGRESS);
  }

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    String style = getStyle(component);
    return StringUtil.isEmpty(style) ? "" : "(" + style + ")";
  }

  /**
   * Returns either {@link #DISCRETE} or null(default) depending on the style of the SeekBar.
   * @param component the node to find the seek bar style from
   * @return either {@link #DISCRETE} or null
   */
  @Nullable
  private static String getStyle(@NotNull NlComponent component) {
    String style = component.getAttribute(null, TAG_STYLE);
    if (style == null) {
      return null;
    }
    if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
      int index = style.indexOf(DOT_SEEK_BAR_DOT);
      return findSeekBarType(style.substring(index + DOT_SEEK_BAR_DOT.length()));
    }
    if (style.startsWith(ANDROID_THEME_PREFIX)) {
      int index = style.indexOf(SEEK_BAR_STYLE);
      return findSeekBarType(style.substring(index + SEEK_BAR_STYLE.length()));
    }
    if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
      int index = style.indexOf(DOT_SEEK_BAR_DOT);
      return findSeekBarType(style.substring(index + DOT_SEEK_BAR_DOT.length()));
    }
    return null;
  }

  @Nullable
  private static String findSeekBarType(@NotNull String style) {
    if (style.startsWith(DISCRETE)) {
      return DISCRETE;
    }
    return null;
  }

  @NotNull
  @Override
  public Icon getIcon(@NotNull NlComponent component) {
    if (!component.getTagName().equals(SEEK_BAR)) {
      return super.getIcon(component);
    }
    return DISCRETE.equals(getStyle(component)) ? StudioIcons.LayoutEditor.Palette.SEEK_BAR_DISCRETE
                                                : StudioIcons.LayoutEditor.Palette.SEEK_BAR;
  }
}

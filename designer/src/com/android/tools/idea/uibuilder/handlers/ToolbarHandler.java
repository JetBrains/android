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

import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlBuilder;
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.ImageViewAttributes.TINT;
import static com.android.SdkConstants.ViewAttributes.MIN_HEIGHT;

/**
 * Handler for the {@code <Toolbar>} widget from appcompat
 */
public class ToolbarHandler extends ViewHandler {

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
          .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
          .androidAttribute(ATTR_BACKGROUND, "?attr/colorPrimary")
          .androidAttribute(ATTR_THEME, "?attr/actionBarTheme")
          .androidAttribute(MIN_HEIGHT, "?attr/actionBarSize")
          .endTag(tagName)
          .toString();
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        // @formatter:off
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
          .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
          .androidAttribute(ATTR_BACKGROUND, "?attr/colorPrimary")
          .androidAttribute(ATTR_THEME, "?attr/actionBarTheme")
          .androidAttribute(MIN_HEIGHT, "?attr/actionBarSize")
          .attribute(APP_PREFIX, "contentInsetStart", "0dp")
          .attribute(APP_PREFIX, "contentInsetLeft", "0dp")
            .startTag(IMAGE_BUTTON)
            .androidAttribute(ATTR_SRC, "?attr/homeAsUpIndicator")
            .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
            .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
            .androidAttribute(TINT, "?attr/actionMenuTextColor")
            .androidAttribute(ATTR_STYLE, "?attr/toolbarNavigationButtonStyle")
            .endTag(IMAGE_BUTTON)
            .startTag(TEXT_VIEW)
            .androidAttribute(ATTR_TEXT, "v7 Toolbar")
            .androidAttribute(ATTR_TEXT_APPEARANCE, "@style/TextAppearance.Widget.AppCompat.Toolbar.Title")
            .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
            .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
            .androidAttribute(ATTR_GRAVITY, VALUE_CENTER_VERTICAL)
            .androidAttribute("ellipsize", "end")
            .androidAttribute("maxLines", 1)
            .endTag(TEXT_VIEW)
            .startTag(IMAGE_BUTTON)
            .androidAttribute(ATTR_SRC, "@drawable/abc_ic_menu_moreoverflow_mtrl_alpha")
            .androidAttribute(ATTR_LAYOUT_WIDTH, "40dp")
            .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
            .androidAttribute(ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_RIGHT)
            .androidAttribute(ATTR_STYLE, "?attr/toolbarNavigationButtonStyle")
            .androidAttribute(TINT, "?attr/actionMenuTextColor")
            .endTag(IMAGE_BUTTON)
          .endTag(tagName)
          .toString();
        // @formatter:on
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  @Override
  public double getPreviewScale(@NotNull String tagName) {
    return 0.5;
  }
}

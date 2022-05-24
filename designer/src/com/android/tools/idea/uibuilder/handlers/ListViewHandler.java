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

import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_CACHE_COLOR_HINT;
import static com.android.SdkConstants.ATTR_DIVIDER;
import static com.android.SdkConstants.ATTR_DIVIDER_HEIGHT;
import static com.android.SdkConstants.ATTR_ENTRIES;
import static com.android.SdkConstants.ATTR_FOOTER_DIVIDERS_ENABLED;
import static com.android.SdkConstants.ATTR_HEADER_DIVIDERS_ENABLED;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_SCROLLBARS;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.NEW_ID_PREFIX;

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for the {@code <ListView>} layout
 */
public class ListViewHandler extends ViewGroupHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_ENTRIES,
      ATTR_SCROLLBARS,
      ATTR_STYLE,
      ATTR_CACHE_COLOR_HINT,
      ATTR_BACKGROUND,
      ATTR_DIVIDER,
      ATTR_DIVIDER_HEIGHT,
      ATTR_HEADER_DIVIDERS_ENABLED,
      ATTR_FOOTER_DIVIDERS_ENABLED);
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(ATTR_ID, NEW_ID_PREFIX + tagName)
          .androidAttribute(ATTR_LAYOUT_WIDTH, "200dip")
          .androidAttribute(ATTR_LAYOUT_HEIGHT, "60dip")
          .androidAttribute("divider", "#333333")
          .androidAttribute("dividerHeight", "1px")
          .endTag(tagName)
          .toString();
      default:
        return super.getXml(tagName, xmlType);
    }
  }
}

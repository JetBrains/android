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

import com.android.tools.idea.uibuilder.api.XmlBuilder;
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <TabHost>} layout
 */
public class TabHostHandler extends FrameLayoutHandler {
  /**
   * Generate something visible.
   * TODO: add an onCreate method to include other required changes.
   * TODO: the current implementation uses hardcoded ID which may create duplicated IDs
   */
  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
      case DRAG_PREVIEW:
        return getXmlWithTabs(tagName, 3);
      default:
        // This component does not look very good on the palette preview.
        return NO_PREVIEW;
    }
  }

  @Language("XML")
  @NotNull
  private static String getXmlWithTabs(@NotNull String tagName, int tabs) {
    // @formatter:off
    XmlBuilder builder = new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_LAYOUT_WIDTH, "200dip")
      .androidAttribute(ATTR_LAYOUT_HEIGHT, "300dip")
        .startTag(LINEAR_LAYOUT)
        .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
        .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT)
        .androidAttribute(ATTR_ORIENTATION, VALUE_VERTICAL)
          .startTag(TAB_WIDGET)
          .androidAttribute(ATTR_ID, "@android:id/tabs")
          .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
          .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
          .endTag(TAB_WIDGET)
          .startTag(FRAME_LAYOUT)
          .androidAttribute(ATTR_ID, "@android:id/tabcontent")
          .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
          .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT);
    // @formatter:on

    for (int tab = 0; tab < tabs; tab++) {
      builder
        .startTag(LINEAR_LAYOUT)
        .androidAttribute(ATTR_ID, "@+id/tab" + (tab + 1))
        .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
        .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT)
        .androidAttribute(ATTR_ORIENTATION, VALUE_VERTICAL)
        .endTag(LINEAR_LAYOUT);
    }

    // @formatter:off
    return builder
          .endTag(FRAME_LAYOUT)
        .endTag(LINEAR_LAYOUT)
      .endTag(tagName)
      .toString();
    // @formatter:on
  }
}

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

import static com.android.SdkConstants.ATTR_BACKGROUND_TINT;
import static com.android.SdkConstants.ATTR_FAB_ALIGNMENT_MODE;
import static com.android.SdkConstants.ATTR_FAB_ANIMATION_MODE;
import static com.android.SdkConstants.ATTR_FAB_CRADLE_MARGIN;
import static com.android.SdkConstants.ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS;
import static com.android.SdkConstants.ATTR_FAB_CRADLE_VERTICAL_OFFSET;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_NAVIGATION_ICON;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRAVITY_VALUE_BOTTOM;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BottomAppBarHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_BACKGROUND_TINT,
      ATTR_NAVIGATION_ICON,
      ATTR_FAB_ALIGNMENT_MODE,
      ATTR_FAB_ANIMATION_MODE,
      ATTR_FAB_CRADLE_MARGIN,
      ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS,
      ATTR_FAB_CRADLE_VERTICAL_OFFSET);
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_BOTTOM)
      .endTag(tagName)
      .toString();
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (!Material3UtilKt.hasMaterial3Dependency(editor.getModel().getFacet())) {
      // The BottomAppBar uses the Colored style in Material 2
      NlWriteCommandActionUtil.run(newChild, "Setup BottomAppBar", () ->
        newChild.setAttribute(null, ATTR_STYLE, STYLE_RESOURCE_PREFIX + "Widget.MaterialComponents.BottomAppBar.Colored")
      );
    }
    return super.onCreate(editor, parent, newChild, insertType);
  }
}

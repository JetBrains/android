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

import com.android.AndroidXConstants;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <TextInputLayout>} layout
 */
public class TextInputLayoutHandler extends LinearLayoutHandler {

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_TEXT_COLOR_HINT,
      ATTR_HINT,
      ATTR_HINT_ENABLED,
      ATTR_HINT_ANIMATION_ENABLED,
      ATTR_HINT_TEXT_APPEARANCE,
      ATTR_HELPER_TEXT,
      ATTR_HELPER_TEXT_ENABLED,
      ATTR_HELPER_TEXT_TEXT_APPEARANCE,
      ATTR_ERROR_ENABLED,
      ATTR_ERROR_TEXT_APPEARANCE,
      ATTR_COUNTER_ENABLED,
      ATTR_COUNTER_MAX_LENGTH,
      ATTR_COUNTER_TEXT_APPEARANCE,
      ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE,
      ATTR_PASSWORD_TOGGLE_ENABLED,
      ATTR_PASSWORD_TOGGLE_DRAWABLE,
      ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION,
      ATTR_PASSWORD_TOGGLE_TINT,
      ATTR_PASSWORD_TOGGLE_TINT_MODE,
      ATTR_BOX_BACKGROUND_MODE,
      ATTR_BOX_COLLAPSED_PADDING_TOP,
      ATTR_BOX_STROKE_COLOR,
      ATTR_BOX_BACKGROUND_COLOR,
      ATTR_BOX_STROKE_WIDTH
    );
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    boolean isMaterial2 = tagName.startsWith(MATERIAL2_PKG);
    switch (xmlType) {
      case COMPONENT_CREATION:
        String textInputEditTextTag = isMaterial2 ? AndroidXConstants.TEXT_INPUT_EDIT_TEXT.newName() : AndroidXConstants.TEXT_INPUT_EDIT_TEXT.oldName();
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
          .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
            .startTag(textInputEditTextTag)
            .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
            .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
            .androidAttribute(ATTR_HINT, "hint")
            .endTag(textInputEditTextTag)
          .endTag(tagName)
          .toString();
      default:
        return super.getXml(tagName, xmlType);
    }
  }
}

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

import com.android.xml.XmlBuilder;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <TextInputLayout>} layout
 */
public class TextInputLayoutHandler extends LinearLayoutHandler {

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
            .startTag(TEXT_INPUT_EDIT_TEXT)
            .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
            .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
            .androidAttribute(ATTR_HINT, "hint")
            .endTag(TEXT_INPUT_EDIT_TEXT)
          .endTag(tagName)
          .toString();
      default:
        return super.getXml(tagName, xmlType);
    }
  }
}

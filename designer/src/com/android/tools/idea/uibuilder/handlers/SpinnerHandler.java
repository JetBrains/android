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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlBuilder;
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for the {@code <Spinner>} widget.
 */
public final class SpinnerHandler extends ViewHandler {
  // Note: This handler is derived from ViewHandler to avoid being treated as a {@code ViewGroup}.

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
      case DRAG_PREVIEW:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_WRAP_CONTENT)
          .endTag(tagName)
          .toString();
      case PREVIEW_ON_PALETTE:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_WRAP_CONTENT)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_WRAP_CONTENT)
          .androidAttribute("entries", "@android:array/postalAddressTypes")
          .endTag(tagName)
          .toString();
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  // TODO: add an onCreate method to give options of how the spinner should be populated.
}

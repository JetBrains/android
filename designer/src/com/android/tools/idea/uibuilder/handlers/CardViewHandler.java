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
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;

import static com.android.SdkConstants.CARD_VIEW_LIB_ARTIFACT;

/**
 * Handler for the {@code <CardView>} widget.
 */
public class CardViewHandler extends FrameLayoutHandler {

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return String.format("<%1$s\n" +
                             "  android:layout_width=\"match_parent\"\n" +
                             "  android:layout_height=\"wrap_content\">\n" +
                             "</%1$s>\n", tagName);
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  @Override
  @NotNull
  public String getGradleCoordinate(@NotNull String viewTag) {
    return CARD_VIEW_LIB_ARTIFACT;
  }
}

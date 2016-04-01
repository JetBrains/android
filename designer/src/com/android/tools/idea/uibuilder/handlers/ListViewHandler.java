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

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;

/**
 * Handler for the {@code <ListView>} layout
 */
public class ListViewHandler extends ViewGroupHandler {

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return String.format("<%1$s\n" +
                             "  android:id=\"@+id/%1$s\"\n" +  // id must be present or the preview will be empty
                             "  android:layout_width=\"200dip\"\n" +
                             "  android:layout_height=\"60dip\"\n" +
                             "  android:divider=\"#333333\"\n" +
                             "  android:dividerHeight=\"1px\">\n" +
                             "</%1$s>\n", tagName);
      default:
        return super.getXml(tagName, xmlType);
    }
  }
}

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
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;

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
        return String.format("<%1$s\n" +
                             "  android:layout_width=\"match_parent\"\n" +
                             "  android:layout_height=\"wrap_content\"\n" +
                             "  android:background=\"?attr/colorPrimary\"\n" +
                             "  android:theme=\"?attr/actionBarTheme\"\n" +
                             "  android:minHeight=\"?attr/actionBarSize\">\n" +
                             "</%1$s>\n", tagName);
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return String.format("<%1$s\n" +
                             "  android:layout_width=\"match_parent\"\n" +
                             "  android:layout_height=\"wrap_content\"\n" +
                             "  android:background=\"?attr/colorPrimary\"\n" +
                             "  android:theme=\"?attr/actionBarTheme\"\n" +
                             "  android:minHeight=\"?attr/actionBarSize\"\n" +
                             "  app:contentInsetStart=\"0dp\"\n" +
                             "  app:contentInsetLeft=\"0dp\">\n" +
                             "\n" +
                             "  <ImageButton\n" +
                             "    android:src=\"?attr/homeAsUpIndicator\"\n" +
                             "    android:layout_width=\"wrap_content\"\n" +
                             "    android:layout_height=\"wrap_content\"\n" +
                             "    android:tint=\"?attr/actionMenuTextColor\"\n" +
                             "    android:style=\"?attr/toolbarNavigationButtonStyle\"\n" +
                             "  />\n" +
                             "  <TextView\n" +
                             "    android:text=\"v7 Toolbar\"\n" +
                             "    android:textAppearance=\"@style/TextAppearance.Widget.AppCompat.Toolbar.Title\"\n" +
                             "    android:layout_width=\"wrap_content\"\n" +
                             "    android:layout_height=\"wrap_content\"\n" +
                             "    android:gravity=\"center_vertical\"\n" +
                             "    android:ellipsize=\"end\"\n" +
                             "    android:maxLines=\"1\"\n" +
                             "  />\n" +
                             "\n" +
                             "  <ImageButton\n" +
                             "    android:src=\"@drawable/abc_ic_menu_moreoverflow_mtrl_alpha\"\n" +
                             "    android:layout_width=\"40dp\"\n" +
                             "    android:layout_height=\"wrap_content\"\n" +
                             "    android:layout_gravity=\"right\"\n" +
                             "    android:style=\"?attr/toolbarNavigationButtonStyle\"\n" +
                             "    android:tint=\"?attr/actionMenuTextColor\"\n" +
                             "  />\n" +
                             "</%1$s>\n", tagName);
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  @Override
  public double getPreviewScale(@NotNull String tagName) {
    return 0.5;
  }
}

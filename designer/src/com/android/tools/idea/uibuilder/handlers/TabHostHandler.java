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
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;

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

  @NotNull
  @Language("XML")
  private static String getXmlWithTabs(@NotNull String tagName, int tabs) {
    StringBuilder builder = new StringBuilder();
    for (int tab = 0; tab < tabs; tab++) {
      builder.append(String.format("<LinearLayout\n" +
                                   "    android:id=\"@+id/tab%1$d\"\n" +
                                   "    android:layout_width=\"match_parent\"\n" +
                                   "    android:layout_height=\"match_parent\"\n" +
                                   "    android:orientation=\"vertical\">\n" +
                                   "</LinearLayout>\n", tab + 1));
    }
    return String.format("<%1$s\n" +
                         "    android:layout_width=\"200dip\"\n" +
                         "    android:layout_height=\"300dip\">\n" +
                         "  <LinearLayout\n" +
                         "      android:layout_width=\"match_parent\"\n" +
                         "      android:layout_height=\"match_parent\"\n" +
                         "      android:orientation=\"vertical\">\n" +
                         "    <TabWidget\n" +
                         "        android:id=\"@android:id/tabs\"\n" +
                         "        android:layout_width=\"match_parent\"\n" +
                         "        android:layout_height=\"wrap_content\">\n" +
                         "    </TabWidget>\n" +
                         "    <FrameLayout\n" +
                         "        android:id=\"@android:id/tabcontent\"\n" +
                         "        android:layout_width=\"match_parent\"\n" +
                         "        android:layout_height=\"match_parent\">\n" +
                         "        %2$s" +
                         "    </FrameLayout>\n" +
                         "  </LinearLayout>\n" +
                         "</%1$s>\n", tagName, builder);
  }
}

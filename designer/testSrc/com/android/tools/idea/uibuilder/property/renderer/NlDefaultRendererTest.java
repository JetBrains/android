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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.property.MockNlComponent;
import com.android.tools.idea.uibuilder.property.NlProperties;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class NlDefaultRendererTest extends LayoutTestCase {
  public void testSimple() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <TextView android:id=\"@+id/textView\"/>" +
                    "</RelativeLayout>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);

    XmlTag[] subTags = xmlFile.getRootTag().getSubTags();
    assertEquals(1, subTags.length);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(ImmutableList.of(MockNlComponent.create(subTags[0])));

    NlDefaultRenderer renderer = new NlDefaultRenderer();

    NlPropertyItem property = properties.get(SdkConstants.ANDROID_URI, "id");
    validateRendering(renderer, property, "id", "@+id/textView");

    property = properties.get(SdkConstants.ANDROID_URI, "text");
    validateRendering(renderer, property, "text", "");

    property = properties.get(SdkConstants.ANDROID_URI, "focusable");
    validateRendering(renderer, property, "focusable", "");
  }

  private static void validateRendering(@NotNull NlDefaultRenderer renderer,
                                        @NotNull NlPropertyItem property,
                                        @NotNull String name,
                                        @NotNull String value) {
    renderer.getLabel().clear();
    renderer.customize(property, 0);
    assertEquals(name, renderer.getLabel().getCharSequence(true));

    renderer.getLabel().clear();
    renderer.customize(property, 1);
    assertEquals(value, renderer.getLabel().getCharSequence(true));
  }
}

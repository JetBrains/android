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
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.property.MockNlComponent;
import com.android.tools.idea.uibuilder.property.NlProperties;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static org.mockito.Mockito.mock;

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

    NlPropertiesManager manager = mock(NlPropertiesManager.class);
    PTable table = mock(PTable.class);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(manager, ImmutableList.of(MockNlComponent.create(subTags[0])));

    NlDefaultRenderer renderer = new NlDefaultRenderer();

    NlPropertyItem property = properties.get(SdkConstants.ANDROID_URI, "id");
    validateRendering(renderer, table, property, "id", "textView");

    property = properties.get(SdkConstants.ANDROID_URI, "text");
    validateRendering(renderer, table, property, "text", "");

    property = properties.get(SdkConstants.ANDROID_URI, "focusable");
    validateRendering(renderer, table, property, "focusable", "");

    PTableItem item = mock(PTableItem.class);
    validateRendering(renderer, table, item, "", "");
  }

  private static void validateRendering(@NotNull NlDefaultRenderer renderer,
                                        @NotNull PTable table,
                                        @NotNull PTableItem item,
                                        @NotNull String name,
                                        @NotNull String value) {
    renderer.getLabel().clear();
    renderer.customizeCellRenderer(table, item, false, false, 10, 0);
    assertEquals(name, renderer.getLabel().getCharSequence(true));

    renderer.getLabel().clear();
    renderer.customizeCellRenderer(table, item, false, false, 10, 1);
    assertEquals(value, renderer.getLabel().getCharSequence(true));
  }
}

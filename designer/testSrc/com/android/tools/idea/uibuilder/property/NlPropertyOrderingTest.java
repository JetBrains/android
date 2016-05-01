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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.google.common.collect.Table;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NlPropertyOrderingTest extends LayoutTestCase {
  public void testGrouping() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <TextView />" +
                    "</RelativeLayout>";
    NlComponent component = getFirstComponent(source);
    Table<String, String, NlPropertyItem> properties = NlProperties.getInstance().getProperties(component);
    List<NlPropertyItem> propertyList = new ArrayList<>(properties.values());
    List<PTableItem> items = new NlPropertiesGrouper().group(propertyList, component);

    // assert that all padding related attributes are grouped together
    PTableItem padding = findItemByName(items, "Padding");
    assertNotNull(padding);
    assertNotNull("Attribute paddingStart missing inside padding attributes", findItemByName(padding.getChildren(), "paddingStart"));
    assertNotNull("Attribute paddingEnd missing inside padding attributes", findItemByName(padding.getChildren(), "paddingEnd"));

    // assert that textview attributes are grouped together
    PTableItem textView = findItemByName(items, "TextView");
    assertNotNull("Missing group for TextView attributes", textView);
    assertNotNull("Attribute capitalize missing inside textview attributes", findItemByName(textView.getChildren(), "capitalize"));
    assertNotNull("Attribute password missing inside textview attributes", findItemByName(textView.getChildren(), "password"));

    // certain special attrs should be at the top level
                  assertNotNull("Missing attribute id at the top level after grouping", findItemByName(items, "id"));
  }

  public void testSorting() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <TextView android:text=\"Hello\" />" +
                    "</RelativeLayout>";

    NlComponent component = getFirstComponent(source);
    Table<String, String, NlPropertyItem> properties = NlProperties.getInstance().getProperties(component);
    List<NlPropertyItem> propertyList = new ArrayList<>(properties.values());
    List<PTableItem> items = new NlPropertiesGrouper().group(propertyList, component);
    items = new NlPropertiesSorter().sort(items, component);

    assertEquals("id attribute is not the first item", "id", items.get(0).getName());
    assertEquals("Layout_width attribute is not the second item", "layout_width", items.get(1).getName());
    assertEquals("Layout_height attribute is not the third item", "layout_height", items.get(2).getName());
    assertEquals("Layout attributes group is not the fourth item", "Layout", items.get(3).getName());
    assertEquals("Padding attributes group is not the fifth item", "Padding", items.get(4).getName());
    assertTrue("TextView group not within the top 10 items", findItemIndex(items, "TextView") < 10);
    assertTrue("Modified attribute text not in the top 10 items", findItemIndex(items, "text") < 10);
  }

  private NlComponent getFirstComponent(@NotNull String source) {
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);

    XmlTag[] subTags = xmlFile.getRootTag().getSubTags();
    assertEquals(1, subTags.length);

    return MockNlComponent.create(subTags[0]);
  }

  @Nullable
  private static PTableItem findItemByName(@NotNull List<PTableItem> items, @NotNull String name) {
    for (PTableItem item : items) {
      if (item.getName().equals(name)) {
        return item;
      }
    }

    return null;
  }

  private static int findItemIndex(@NotNull List<PTableItem> items, @NotNull String name) {
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).getName().equalsIgnoreCase(name)) {
        return i;
      }
    }

    return -1;
  }
}

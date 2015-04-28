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
package com.android.tools.idea.uibuilder.palette;

import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

public class NlComponentModelTest extends TestCase {
  private NlPaletteModel model = new NlPaletteModel();

  @Language("XML")
  private static final String SINGLE_GROUP_WITH_ITEM_OVERRIDES =
    "<meta-model>\n" +
    "  <palette>\n" +
    "    <group name=\"Layouts\">\n" +
    "      <item tag=\"FrameLayout\"/>\n" +
    "      <item tag=\"LinearLayout\"\n" +
    "            title=\"LinearLayout (Horizontal)\"\n" +
    "            icon=\"AndroidIcons.Views.LinearLayout\"\n" +
    "            tooltip=\"A Layout for a single row.\">\n" +
    "        <item title=\"LinearLayout (Vertical)\"\n" +
    "              icon=\"AndroidIcons.Views.VerticalLinearLayout\"\n" +
    "              tooltip=\"A Layout for a single column.\">\n" +
    "          <creation>\n" +
    "            <![CDATA[\n" +
    "            <LinearLayout\n" +
    "              android:orientation=\"vertical\"\n" +
    "              android:layout_width=\"match_parent\"\n" +
    "              android:layout_height=\"match_parent\">\n" +
    "            </LinearLayout>\n" +
    "        ]]>\n" +
    "          </creation>\n" +
    "        </item>\n" +
    "      </item>\n" +
    "    </group>\n" +
    "  </palette>\n" +
    "\n" +
    "  <meta model=\"com.intellij.android.designer.model.RadViewContainer\"\n" +
    "        layout=\"com.intellij.android.designer.model.layout.RadFrameLayout\"\n" +
    "        class=\"android.widget.FrameLayout\"\n" +
    "        tag=\"FrameLayout\"\n" +
    "        fill=\"opposite\">\n" +
    "\n" +
    "    <palette title=\"FrameLayout\" icon=\"AndroidIcons.Views.FrameLayout\"\n" +
    "             tooltip=\"A Layout for a single item.\"/>\n" +
    "\n" +
    "    <morphing to=\"TableLayout GridLayout RelativeLayout\"/>\n" +
    "\n" +
    "    <properties important=\"foreground foregroundGravity\"\n" +
    "                expert=\"layout:gravity.fill layout:gravity.clip layout:gravity.start layout:gravity.end\"\n" +
    "        />\n" +
    "\n" +
    "    <creation>\n" +
    "      <![CDATA[\n" +
    "          <FrameLayout\n" +
    "            android:layout_width=\"match_parent\"\n" +
    "            android:layout_height=\"match_parent\">\n" +
    "          </FrameLayout>\n" +
    "      ]]>\n" +
    "    </creation>\n" +
    "  </meta>\n" +
    "\n" +
    "" +
    "  <meta model=\"com.intellij.android.designer.model.RadViewContainer\"\n" +
    "        layout=\"com.intellij.android.designer.model.layout.RadLinearLayout\"\n" +
    "        class=\"android.widget.LinearLayout\"\n" +
    "        tag=\"LinearLayout\"\n" +
    "        fill=\"opposite\">\n" +
    "\n" +
    "    <presentation title=\" (%orientation%)\"/>\n" +
    "\n" +
    "    <palette title=\"LinearLayout\" icon=\"AndroidIcons.Views.LinearLayout\"\n" +
    "             tooltip=\"A Layout that arranges its children in a single row or column.\"/>\n" +
    "\n" +
    "    <morphing to=\"RadioGroup TableLayout GridLayout RelativeLayout\"/>\n" +
    "\n" +
    "    <properties inplace=\"orientation\"\n" +
    "                important=\"orientation gravity baselineAligned measureWithLargestChild showDividers\"\n" +
    "                expert=\"layout:gravity.fill layout:gravity.clip layout:gravity.start layout:gravity.end\"\n" +
    "                top=\"gravity orientation\"\n" +
    "        />\n" +
    "\n" +
    "    <creation>\n" +
    "      <![CDATA[\n" +
    "          <LinearLayout\n" +
    "            android:orientation=\"horizontal\"\n" +
    "            android:layout_width=\"match_parent\"\n" +
    "            android:layout_height=\"match_parent\">\n" +
    "          </LinearLayout>\n" +
    "      ]]>\n" +
    "    </creation>\n" +
    "  </meta>\n" +
    "\n" +
    "</meta-model>";

  private static final List<NlPaletteGroup> GROUP_WITH_ITEM_OVERRIDES = Arrays.asList(
    makeGroup("Layouts",
              new NlPaletteItem("FrameLayout", "AndroidIcons.Views.FrameLayout", "A Layout for a single item."),
              new NlPaletteItem("LinearLayout (Horizontal)", "AndroidIcons.Views.LinearLayout", "A Layout for a single row."),
              new NlPaletteItem("LinearLayout (Vertical)", "AndroidIcons.Views.VerticalLinearLayout", "A Layout for a single column."))
  );

  public void testSingleGroupWithItemOverrides() throws Exception {
    model.loadPalette(loadDocument(SINGLE_GROUP_WITH_ITEM_OVERRIDES));
    assertGroupsEquals(GROUP_WITH_ITEM_OVERRIDES, model.getGroups());
  }

  @Language("XML")
  private static final String SINGLE_GROUP_WITH_MISSING_DATA =
    "<meta-model>\n" +
    "  <palette>\n" +
    "    <group name=\"Layouts\">\n" +
    "      <item tag=\"SomeOtherLayout\"/>\n" +  // Has no model section
    "      <item tag=\"FrameLayout\"/>\n" +      // Model section is missing a palette subsection
    "      <item tag=\"LinearLayout\"/>\n" +     // Model section has a palette section with a missing title
    "      <item tag=\"ConstraintLayout\"/>\n" + // Model section has a palette section with a missing icon and tooltip
    "    </group>\n" +
    "  </palette>\n" +
    "\n" +
    "  <meta model=\"com.intellij.android.designer.model.RadViewContainer\"\n" +
    "        layout=\"com.intellij.android.designer.model.layout.RadFrameLayout\"\n" +
    "        class=\"android.widget.FrameLayout\"\n" +
    "        tag=\"FrameLayout\"\n" +
    "        fill=\"opposite\">\n" +
    "\n" +   // Missing palette
    "    <morphing to=\"TableLayout GridLayout RelativeLayout\"/>\n" +
    "\n" +
    "    <properties important=\"foreground foregroundGravity\"\n" +
    "                expert=\"layout:gravity.fill layout:gravity.clip layout:gravity.start layout:gravity.end\"\n" +
    "        />\n" +
    "\n" +
    "    <creation>\n" +
    "      <![CDATA[\n" +
    "          <FrameLayout\n" +
    "            android:layout_width=\"match_parent\"\n" +
    "            android:layout_height=\"match_parent\">\n" +
    "          </FrameLayout>\n" +
    "      ]]>\n" +
    "    </creation>\n" +
    "  </meta>\n" +
    "\n" +
    "  <meta model=\"com.intellij.android.designer.model.RadViewContainer\"\n" +
    "        layout=\"com.intellij.android.designer.model.layout.RadLinearLayout\"\n" +
    "        class=\"android.widget.LinearLayout\"\n" +
    "        tag=\"LinearLayout\"\n" +
    "        fill=\"opposite\">\n" +
    "\n" +
    "    <presentation title=\" (%orientation%)\"/>\n" +
    "\n" +
    "    <palette icon=\"AndroidIcons.Views.LinearLayout\"\n" +
    "             tooltip=\"A Layout that arranges its children in a single row or column.\"/>\n" +
    "\n" +
    "    <morphing to=\"RadioGroup TableLayout GridLayout RelativeLayout\"/>\n" +
    "\n" +
    "    <properties inplace=\"orientation\"\n" +
    "                important=\"orientation gravity baselineAligned measureWithLargestChild showDividers\"\n" +
    "                expert=\"layout:gravity.fill layout:gravity.clip layout:gravity.start layout:gravity.end\"\n" +
    "                top=\"gravity orientation\"\n" +
    "        />\n" +
    "\n" +
    "    <creation>\n" +
    "      <![CDATA[\n" +
    "          <LinearLayout\n" +
    "            android:orientation=\"horizontal\"\n" +
    "            android:layout_width=\"match_parent\"\n" +
    "            android:layout_height=\"match_parent\">\n" +
    "          </LinearLayout>\n" +
    "      ]]>\n" +
    "    </creation>\n" +
    "  </meta>\n" +
    "\n" +
    "  <meta model=\"com.intellij.android.designer.model.RadViewContainer\"\n" +
    "        layout=\"com.intellij.android.designer.model.layout.RadConstraintLayout\"\n" +
    "        class=\"android.widget.ConstraintLayout\"\n" +
    "        tag=\"ConstraintLayout\"\n" +
    "        fill=\"opposite\">\n" +
    "\n" +
    "    <palette title=\"ConstraintLayout\"/>\n" +
    "\n" +
    "    <morphing to=\"TableLayout GridLayout RelativeLayout\"/>\n" +
    "\n" +
    "    <properties important=\"foreground foregroundGravity\"\n" +
    "                expert=\"layout:gravity.fill layout:gravity.clip layout:gravity.start layout:gravity.end\"\n" +
    "        />\n" +
    "\n" +
    "    <creation>\n" +
    "      <![CDATA[\n" +
    "          <FrameLayout\n" +
    "            android:layout_width=\"match_parent\"\n" +
    "            android:layout_height=\"match_parent\">\n" +
    "          </FrameLayout>\n" +
    "      ]]>\n" +
    "    </creation>\n" +
    "  </meta>\n" +
    "\n" +
    "</meta-model>";

  private static final List<NlPaletteGroup> GROUP_WITH_MISSING_DATA = Arrays.asList(
    makeGroup("Layouts", new NlPaletteItem("ConstraintLayout", "", ""))
  );

  public void testGroupWithMissingData() throws Exception {
    model.loadPalette(loadDocument(SINGLE_GROUP_WITH_MISSING_DATA));
    assertGroupsEquals(GROUP_WITH_MISSING_DATA, model.getGroups());
  }

  @NotNull
  private static NlPaletteGroup makeGroup(String title, NlPaletteItem... items) {
    NlPaletteGroup group = new NlPaletteGroup(title);
    for (NlPaletteItem item : items) {
      group.add(item);
    }
    return group;
  }

  private void assertGroupsEquals(List<NlPaletteGroup> expectedGroups, List<NlPaletteGroup> actualGroups) {
    for (int i=0; i<expectedGroups.size(); i++) {
      if (i >= actualGroups.size()) {
        break;
      }
      NlPaletteGroup expectedGroup = expectedGroups.get(i);
      NlPaletteGroup actualGroup = actualGroups.get(i);
      assertEquals(String.format("group[%d].title", i), expectedGroup.getTitle(), actualGroup.getTitle());
      for (int j=0; i<expectedGroup.getItems().size(); j++) {
        if (j >= actualGroup.getItems().size()) {
          break;
        }
        NlPaletteItem expectedItem = expectedGroup.getItems().get(j);
        NlPaletteItem actualItem = actualGroup.getItems().get(j);
        assertEquals(String.format("group[%d].item[%d].title", i, j), expectedItem.getTitle(), actualItem.getTitle());
        assertEquals(String.format("group[%d].item[%d].icon", i, j), expectedItem.getIconPath(), actualItem.getIconPath());
        assertEquals(String.format("group[%d].item[%d].tooltip", i, j), expectedItem.getTooltip(), actualItem.getTooltip());
      }
      assertEquals(String.format("group[%d].items.length", i), expectedGroup.getItems().size(), actualGroup.getItems().size());
    }
    assertEquals("groups.length", expectedGroups.size(), actualGroups.size());
  }

  private Document loadDocument(String xml) throws Exception {
    return new SAXBuilder().build(new StringReader(xml));
  }
}

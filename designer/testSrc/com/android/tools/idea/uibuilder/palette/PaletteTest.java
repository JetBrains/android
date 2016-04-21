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

import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.palette.Palette.Group;
import org.intellij.lang.annotations.Language;

import java.io.StringReader;
import java.util.Iterator;

public class PaletteTest extends PaletteTestCase {

  public void testPaletteStructure() throws Exception {
    Palette palette = loadPalette();
    Iterator<Palette.BaseItem> iterator = palette.getItems().iterator();
    Group group1 = assertIsGroup(iterator.next(), "Widgets");
    Group group2 = assertIsGroup(iterator.next(), "Advanced");
    assertFalse(iterator.hasNext());

    iterator = group1.getItems().iterator();
    assertTextViewItem(iterator.next());
    assertLinearLayoutItem(iterator.next());
    assertLargeProgressBarItem(iterator.next());
    assertNormalProgressBarItem(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = group2.getItems().iterator();
    Group group3 = assertIsGroup(iterator.next(), "Distinct");
    assertFalse(iterator.hasNext());

    iterator = group3.getItems().iterator();
    assertIncludeItem(iterator.next());
    assertCoordinatorLayoutItem(iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Language("XML")
  private static final String PALETTE =
    "<palette>\n" +
    "  <group name=\"Widgets\">\n" +
    "    <item tag=\"TextView\"/>\n" +
    "    <item tag=\"LinearLayout\" " +
    "          title=\"LinearLayout (horizontal)\"/>\n" +
    "    <item tag=\"ProgressBar\"\n" +
    "          id=\"LargeProgressBar\"\n" +
    "          title=\"ProgressBar (Large)\">\n" +
    "      <xml reuse=\"preview,drag-preview\">\n" +
    "        <![CDATA[\n" +
    "              <ProgressBar\n" +
    "                style=\"?android:attr/progressBarStyleLarge\"\n" +
    "                android:layout_width=\"wrap_content\"\n" +
    "                android:layout_height=\"wrap_content\"\n" +
    "              />\n" +
    "            ]]>\n" +
    "      </xml>\n" +
    "    </item>\n" +
    "    <item tag=\"ProgressBar\"\n" +
    "          title=\"ProgressBar\">\n" +
    "      <xml reuse=\"preview,drag-preview\">\n" +
    "        <![CDATA[\n" +
    "            <ProgressBar\n" +
    "              style=\"?android:attr/progressBarStyle\"\n" +
    "              android:layout_width=\"wrap_content\"\n" +
    "              android:layout_height=\"wrap_content\"\n" +
    "            />\n" +
    "          ]]>\n" +
    "      </xml>\n" +
    "    </item>\n" +
    "  </group>\n" +
    "  <group name=\"Advanced\">\n" +
    "    <group name=\"Distinct\">\n" +
    "      <item tag=\"include\"/>\n" +
    "      <item tag=\"android.support.design.widget.CoordinatorLayout\"/>\n" +
    "    </group>\n" +
    "  </group>\n" +
    "</palette>\n";

  private Palette loadPalette() throws Exception {
    ViewHandlerManager manager = new ViewHandlerManager(getProject());
    return Palette.parse(new StringReader(PALETTE), manager);
  }

}

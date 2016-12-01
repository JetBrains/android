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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;

import static com.google.common.truth.Truth.assertThat;

public class SingleListTreeProviderTest extends AndroidTestCase {
  private Palette myLayoutPalette;
  private Palette myMenuPalette;
  private Palette.Item myTextView;
  private Palette.Item myLinearLayout;
  private Palette.Item myProgressBar;
  private Palette.Item myInclude;
  private Palette.Item myCoordinatorLayout;
  private Palette.Item myMenuItem;
  private Palette.Item myMenu;
  private Palette.Item myMenuGroup;
  private SingleListTreeProvider provider1;
  private SingleListTreeProvider provider2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myLayoutPalette = loadPalette(PALETTE_V1);
    myMenuPalette = loadPalette(PALETTE_V2);
    Palette.Group widgetGroup = (Palette.Group)myLayoutPalette.getItems().get(0);
    Palette.Group advancedGroup = (Palette.Group)myLayoutPalette.getItems().get(1);
    Palette.Group distinctGroup = (Palette.Group)advancedGroup.getItems().get(0);
    myTextView = (Palette.Item)widgetGroup.getItems().get(0);
    myLinearLayout = (Palette.Item)widgetGroup.getItems().get(1);
    myProgressBar = (Palette.Item)widgetGroup.getItems().get(2);
    myInclude = (Palette.Item)distinctGroup.getItems().get(0);
    myCoordinatorLayout = (Palette.Item)distinctGroup.getItems().get(1);
    myMenuItem = (Palette.Item)myMenuPalette.getItems().get(0);
    myMenu = (Palette.Item)myMenuPalette.getItems().get(1);
    myMenuGroup = (Palette.Item)myMenuPalette.getItems().get(2);

    provider1 = new SingleListTreeProvider(getProject(), myLayoutPalette);
    provider2 = new SingleListTreeProvider(getProject(), myMenuPalette);
  }

  public void testGetRootElement() {
    assertThat(provider1.getRootElement()).isSameAs(myLayoutPalette);
    assertThat(provider2.getRootElement()).isSameAs(myMenuPalette);
  }

  public void testGetChildElements() {
    assertThat(provider1.getChildElements(myLayoutPalette)).asList().containsExactly(TreeCategoryProvider.ALL);
    assertThat(provider2.getChildElements(myMenuPalette)).asList().containsExactly(TreeCategoryProvider.ALL);

    assertThat(provider1.getChildElements(TreeCategoryProvider.ALL)).asList()
      .containsExactly(myTextView, myLinearLayout, myProgressBar, myInclude, myCoordinatorLayout).inOrder();
    assertThat(provider2.getChildElements(TreeCategoryProvider.ALL)).asList()
      .containsExactly(myMenuItem, myMenu, myMenuGroup).inOrder();
  }

  public void testGetParentElement() {
    assertThat(provider1.getParentElement(TreeCategoryProvider.ALL)).isSameAs(myLayoutPalette);
    assertThat(provider2.getParentElement(TreeCategoryProvider.ALL)).isSameAs(myMenuPalette);

    assertThat(provider1.getParentElement(myTextView)).isSameAs(TreeCategoryProvider.ALL);
    assertThat(provider2.getParentElement(myMenu)).isSameAs(TreeCategoryProvider.ALL);
  }

  @Language("XML")
  private static final String PALETTE_V1 =
    "<palette>\n" +
    "  <group name=\"Widgets\">\n" +
    "    <item tag=\"TextView\"/>\n" +
    "    <item tag=\"LinearLayout\" " +
    "          title=\"LinearLayout (horizontal)\">\n" +
    "      <xml>\n" +
    "        <![CDATA[\n" +
    "            <LinearLayout\n" +
    "              android:orientation=\"horizontal\"\n" +
    "              android:layout_width=\"match_parent\"\n" +
    "              android:layout_height=\"match_parent\">\n" +
    "            </LinearLayout>\n" +
    "          ]]>\n" +
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
    "      <item tag=\"android.support.design.widget.CoordinatorLayout\">\n" +
    "          title=\"CoordinatorLayout\">\n" +
    "      </item>\n" +
    "    </group>\n" +
    "  </group>\n" +
    "</palette>\n";

  @Language("XML")
  private static final String PALETTE_V2 =
    "<palette>\n" +
    "  <item tag=\"item\" title=\"Menu Item\">\n" +
    "    <xml><![CDATA[<item android:title=\"Item\" />]]></xml>\n" +
    "  </item>\n" +
    "\n" +
    "  <item tag=\"menu\" title=\"Menu\"/>\n" +
    "  <item tag=\"group\" title=\"Group\"/>\n" +
    "</palette>\n";

  private Palette loadPalette(@NotNull String xml) throws Exception {
    ViewHandlerManager manager = new ViewHandlerManager(getProject());
    return Palette.parse(new StringReader(xml), manager);
  }
}

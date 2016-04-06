/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.tools.idea.configurations.Configuration;
import com.android.utils.SdkUtils;
import com.intellij.openapi.vfs.VirtualFile;
import org.w3c.dom.Element;

import java.lang.reflect.Field;

public class MenuPreviewRendererTest extends RenderTestBase {

  public void test() throws Exception {
    myFixture.copyFileToProject("menus/strings.xml", "res/menu/strings.xml");
    VirtualFile file = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParserFactory.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String layout = XmlPrettyPrinter.prettyPrint(root, true);
    String oldXml =
      "<LinearLayout\n" +
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "layout_width=\"fill_parent\"\n" +
      "layout_height=\"fill_parent\"\n" +
      "orientation=\"vertical\" >\n" +
      "<LinearLayout\n" +
      "    layout_width=\"wrap_content\"\n" +
      "    layout_height=\"wrap_content\"\n" +
      "    layout_gravity=\"right\"\n" +
      "    background=\"@android:drawable/menu_panel_holo_dark\"\n" +
      "    orientation=\"vertical\" >\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Declared 2\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Radio 1\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "\n" +
      "        <RadioButton\n" +
      "            layout_width=\"wrap_content\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            clickable=\"false\"\n" +
      "            duplicateParentState=\"true\"\n" +
      "            focusable=\"false\" />\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Radio 2\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "\n" +
      "        <RadioButton\n" +
      "            layout_width=\"wrap_content\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            clickable=\"false\"\n" +
      "            duplicateParentState=\"true\"\n" +
      "            focusable=\"false\" />\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                enabled=\"false\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Check\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "\n" +
      "        <CheckBox\n" +
      "            layout_width=\"wrap_content\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            clickable=\"false\"\n" +
      "            duplicateParentState=\"true\"\n" +
      "            enabled=\"false\"\n" +
      "            focusable=\"false\" />\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Declared 5\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"@string/key1\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Declared 4\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Declared 1\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "    <ImageView\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"1dp\"\n" +
      "        background=\"?android:attr/dividerHorizontal\" />\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        layout_width=\"fill_parent\"\n" +
      "        layout_height=\"?android:attr/dropdownListPreferredItemHeight\"\n" +
      "        minWidth=\"196dip\"\n" +
      "        paddingEnd=\"16dip\" >\n" +
      "\n" +
      "        <RelativeLayout\n" +
      "            layout_width=\"0dp\"\n" +
      "            layout_height=\"wrap_content\"\n" +
      "            layout_gravity=\"center_vertical\"\n" +
      "            layout_marginLeft=\"16dip\"\n" +
      "            layout_weight=\"1\"\n" +
      "            duplicateParentState=\"true\" >\n" +
      "\n" +
      "            <TextView\n" +
      "                id=\"@+id/title\"\n" +
      "                layout_width=\"wrap_content\"\n" +
      "                layout_height=\"wrap_content\"\n" +
      "                layout_alignParentLeft=\"true\"\n" +
      "                layout_alignParentTop=\"true\"\n" +
      "                duplicateParentState=\"true\"\n" +
      "                ellipsize=\"marquee\"\n" +
      "                fadingEdge=\"horizontal\"\n" +
      "                singleLine=\"true\"\n" +
      "                text=\"Declared 3\"\n" +
      "                textAlignment=\"viewStart\"\n" +
      "                textAppearance=\"?android:attr/textAppearanceLargePopupMenu\" />\n" +
      "        </RelativeLayout>\n" +
      "    </LinearLayout>\n" +
      "\n" +
      "</LinearLayout>\n" +
      "<View\n" +
      "    layout_width=\"fill_parent\"\n" +
      "    layout_height=\"fill_parent\"\n" +
      "    layout_weight=\"1\" />\n" +
      "<TextView\n" +
      "    layout_width=\"fill_parent\"\n" +
      "    layout_height=\"wrap_content\"\n" +
      "    layout_margin=\"5dp\"\n" +
      "    gravity=\"center\"\n" +
      "    text=\"(Note: Menu preview is only approximate)\"\n" +
      "    textColor=\"#ff0000\" />\n" +
      "</LinearLayout>\n";
    String newXml = "<FrameLayout\n" +
               "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
               "android:layout_width=\"match_parent\"\n" +
               "android:layout_height=\"match_parent\" />\n";

    oldXml = oldXml.replace("\n", SdkUtils.getLineSeparator());
    newXml = newXml.replace("\n", SdkUtils.getLineSeparator());

    if (task.getLayoutLib().supports(Features.ACTION_BAR)) {
      checkRendering(task, "menu/menu1.png");
      assertEquals(newXml, layout);
    } else {
      assertEquals(oldXml, layout);
      System.err.println("Not running MenuPreviewRendererTest.test: Associated layoutlib in test SDK needs " +
                         "to use API 21 or higher");
    }
  }

  public void testLightTheme() throws Exception {
    myFixture.copyFileToProject("menus/strings.xml", "res/menu/strings.xml");
    VirtualFile file = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    assertNotNull(file);
    Configuration configuration = getConfiguration(file, DEFAULT_DEVICE_ID, "@android:style/Theme.Holo.Light");
    RenderTask task = createRenderTask(file, configuration);
    assertNotNull(task);

    if (task.getLayoutLib().supports(Features.ACTION_BAR)) {
      checkRendering(task, "menu/menu1-light.png");
    } else {
      System.err.println("Not running MenuPreviewRendererTest.testLightTheme: Associated layoutlib in test SDK needs " +
                         "to use API 21 or higher");
    }

    // TODO: Remove the hack below after LayoutLib has been fixed properly.
    try {
      Field threadInstanceField =
        task.getLayoutLib().getClassLoader().loadClass("android.view.Choreographer").getDeclaredField("sThreadInstance");
      threadInstanceField.setAccessible(true);
      ((ThreadLocal)threadInstanceField.get(null)).remove();
    }
    catch (Exception ignore) {
      // Clearing the field may no longer be necessary if the exception is thrown (updated layoutlib?)
    }
  }
}

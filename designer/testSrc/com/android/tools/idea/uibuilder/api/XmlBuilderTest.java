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
package com.android.tools.idea.uibuilder.api;

import com.android.SdkConstants;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class XmlBuilderTest {
  @Test
  public void toStringTabHost() {
    @Language("XML")
    String expected =
      "<TabHost\n" +
      "    android:layout_width=\"200dip\"\n" +
      "    android:layout_height=\"300dip\">\n" +
      "\n" +
      "    <LinearLayout\n" +
      "        android:layout_width=\"match_parent\"\n" +
      "        android:layout_height=\"match_parent\"\n" +
      "        android:orientation=\"vertical\">\n" +
      "\n" +
      "        <TabWidget\n" +
      "            android:id=\"@android:id/tabs\"\n" +
      "            android:layout_width=\"match_parent\"\n" +
      "            android:layout_height=\"wrap_content\" />\n" +
      "\n" +
      "        <FrameLayout\n" +
      "            android:id=\"@android:id/tab_content\"\n" +
      "            android:layout_width=\"match_parent\"\n" +
      "            android:layout_height=\"match_parent\">\n" +
      "\n" +
      "            <LinearLayout\n" +
      "                android:id=\"@+id/tab_1\"\n" +
      "                android:layout_width=\"match_parent\"\n" +
      "                android:layout_height=\"match_parent\"\n" +
      "                android:orientation=\"vertical\">\n" +
      "\n" +
      "            </LinearLayout>\n" +
      "\n" +
      "            <LinearLayout\n" +
      "                android:id=\"@+id/tab_2\"\n" +
      "                android:layout_width=\"match_parent\"\n" +
      "                android:layout_height=\"match_parent\"\n" +
      "                android:orientation=\"vertical\">\n" +
      "\n" +
      "            </LinearLayout>\n" +
      "\n" +
      "            <LinearLayout\n" +
      "                android:id=\"@+id/tab_3\"\n" +
      "                android:layout_width=\"match_parent\"\n" +
      "                android:layout_height=\"match_parent\"\n" +
      "                android:orientation=\"vertical\">\n" +
      "\n" +
      "            </LinearLayout>\n" +
      "        </FrameLayout>\n" +
      "    </LinearLayout>\n" +
      "</TabHost>\n";

    // @formatter:off
    XmlBuilder builder = new XmlBuilder()
      .startTag("TabHost")
      .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, "200dip")
      .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, "300dip")
        .startTag("LinearLayout")
        .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
        .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_PARENT)
        .androidAttribute("orientation", "vertical")
          .startTag("TabWidget")
          .androidAttribute("id", "@android:id/tabs")
          .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_WRAP_CONTENT)
          .endTag("TabWidget")
          .startTag("FrameLayout")
          .androidAttribute("id", "@android:id/tab_content")
          .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_PARENT);
    // @formatter:on

    for (int i = 0; i < 3; i++) {
      builder
        .startTag("LinearLayout")
        .androidAttribute("id", "@+id/tab_" + (i + 1))
        .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
        .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_PARENT)
        .androidAttribute("orientation", "vertical")
        .endTag("LinearLayout");
    }

    // @formatter:off
    builder
          .endTag("FrameLayout")
        .endTag("LinearLayout")
      .endTag("TabHost");
    // @formatter:on

    assertEquals(expected, builder.toString());
  }

  @Test
  public void toStringEmptyElementNotLayout() {
    @Language("XML")
    String expected = "<TabWidget />\n";

    String actual = new XmlBuilder()
      .startTag("TabWidget")
      .endTag("TabWidget")
      .toString();

    assertEquals(expected, actual);
  }

  @Test
  public void toStringEmptyElementLayout() {
    @Language("XML")
    String expected = "<LinearLayout>\n" +
                      "\n" +
                      "</LinearLayout>\n";

    String actual = new XmlBuilder()
      .startTag("LinearLayout")
      .endTag("LinearLayout")
      .toString();

    assertEquals(expected, actual);
  }

  @Test
  public void toStringNoClosePreviousTagWithoutAttributes() {
    @Language("XML")
    String expected = "<Foo>\n\n" +
                      "    <Bar />\n" +
                      "</Foo>\n";

    // @formatter:off
    String actual = new XmlBuilder()
      .startTag("Foo")
        .startTag("Bar")
        .endTag("Bar")
      .endTag("Foo")
      .toString();
    // @formatter:on

    assertEquals(expected, actual);
  }

  @Test
  public void toStringAttributeWithNoNamespace() {
    @Language("XML")
    String expected = "<Foo\n" +
                      "    name=\"value\" />\n";

    String actual = new XmlBuilder()
      .startTag("Foo")
      .attribute("", "name", "value")
      .endTag("Foo")
      .toString();

    assertEquals(expected, actual);
  }
}

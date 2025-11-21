/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.rendering.parsers;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_TEXT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.util.PathString;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LayoutFilePullParserTest {

  @Rule
  public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Test
  public void testParser() throws Exception {
    File resourceFile = tmpFolder.newFile();
    String resourceContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                             "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                             "              xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                             "              android:orientation=\"vertical\"\n" +
                             "              android:layout_width=\"match_parent\"\n" +
                             "              android:layout_height=\"match_parent\">\n" +
                             "\n" +
                             "    <TextView\n" +
                             "        android:id=\"@+id/first\"\n" +
                             "        android:layout_width=\"wrap_content\"\n" +
                             "        android:layout_height=\"wrap_content\"\n" +
                             "        android:text=\"Normal text\"/>\n" +
                             "\n" +
                             "    <TextView\n" +
                             "        android:id=\"@+id/second\"\n" +
                             "        android:layout_width=\"match_parent\"\n" +
                             "        android:layout_height=\"wrap_content\"\n" +
                             "        android:text=\"Runtime Text\"\n" +
                             "        android:layout_gravity=\"left|center_vertical\"\n" +
                             "        tools:text=\"Designtime Text\"\n" +
                             "        tools:textColor=\"@android:color/darker_gray\"/>\n" +
                             "\n" +
                             "    <TextView\n" +
                             "        android:id=\"@+id/blank\"\n" +
                             "        android:layout_width=\"wrap_content\"\n" +
                             "        android:layout_height=\"wrap_content\"\n" +
                             "        tools:text=\"\"/>\n" +
                             "\n" +
                             "    <!--\n" +
                             "    Reset fastScrollAlwaysVisible attribute at designtime (fastScrollAlwaysVisible breaks\n" +
                             "    rendering, see http://b.android.com/58448\n" +
                             "    -->\n" +
                             "    <ListView\n" +
                             "        android:id=\"@+id/listView\"\n" +
                             "        android:layout_width=\"wrap_content\"\n" +
                             "        android:layout_height=\"wrap_content\"\n" +
                             "        android:fastScrollAlwaysVisible=\"true\"\n" +
                             "        tools:fastScrollAlwaysVisible=\"\"/>\n" +
                             "</LinearLayout>";

    try (FileOutputStream fo = new FileOutputStream(resourceFile)) {
      fo.write(resourceContent.getBytes(StandardCharsets.UTF_8));
    }

    PathString file = new PathString(resourceFile);

    ILayoutPullParser parser = LayoutFilePullParser.create(file, ResourceNamespace.RES_AUTO, null);

    assertNull(parser.getName());

    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("@+id/first", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, ATTR_LAYOUT_WIDTH)); // auto converted from match_parent
    assertEquals("wrap_content", parser.getAttributeValue(ANDROID_URI, ATTR_LAYOUT_HEIGHT));
    assertEquals("Designtime Text", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT)); // overriding runtime text attribute
    assertEquals("@android:color/darker_gray", parser.getAttributeValue(ANDROID_URI, "textColor"));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("@+id/blank", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertEquals("", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT)); // Don't unset when no framework attribute is defined
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("ListView", parser.getName());
    assertEquals("@+id/listView", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertNull(parser.getAttributeValue(ANDROID_URI, "fastScrollAlwaysVisible")); // Cleared by overriding defined framework attribute
  }
}

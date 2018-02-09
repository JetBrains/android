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
package com.android.tools.idea.rendering.parsers;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;

import static com.android.SdkConstants.*;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

public class LayoutFilePullParserTest extends AndroidTestCase {
  public LayoutFilePullParserTest() {
  }

  public void testParser() throws Exception {
    @SuppressWarnings("SpellCheckingInspection")
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout/designtime.xml");
    assertNotNull(virtualFile);
    File file = VfsUtilCore.virtualToIoFile(virtualFile);

    LayoutFilePullParser parser = LayoutFilePullParser.create(file);

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

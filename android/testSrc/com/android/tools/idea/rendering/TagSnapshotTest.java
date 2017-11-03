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
package com.android.tools.idea.rendering;

import com.intellij.psi.xml.XmlTag;
import junit.framework.TestCase;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.XmlTagMockBuilder.*;

public class TagSnapshotTest extends TestCase {

  public void test() {
    XmlTag linearLayout = newBuilder("LinearLayout")
      .setAttribute(ATTR_ORIENTATION, VALUE_VERTICAL)
      .addChild(
        newBuilder("Button")
          .setAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
          .setAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT))
      .addChild(
        newBuilder("TextView")
          .setAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
          .setAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT))
      .build();

    AtomicInteger callCount = new AtomicInteger(0);
    TagSnapshot snapshot = TagSnapshot.createTagSnapshot(linearLayout, (tag) -> {
      callCount.incrementAndGet();

      if ("LinearLayout".equals(tag.tagName)) {
        assertEquals(VALUE_VERTICAL, tag.getAttribute(ATTR_ORIENTATION, ANDROID_URI));
        assertEquals(2, tag.children.size());
      }
      else {
        assertEquals(VALUE_WRAP_CONTENT, tag.getAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI));
        assertEquals(VALUE_WRAP_CONTENT, tag.getAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI));
        assertEquals(0, tag.children.size());
      }
    });
    // Once per element
    assertEquals(3, callCount.get());
    TagSnapshot synthetic = TagSnapshot.createSyntheticTag(newBuilder("transformed").build(), "synthetic", null, null,
                                                           Collections.emptyList(),
                                                           Collections.emptyList());
    synthetic.children = Collections.singletonList(snapshot);
    assertEquals("TagSnapshot{synthetic, attributes=[], children=\n" +
                 "[TagSnapshot{LinearLayout, attributes=[AttributeSnapshot{orientation=\"vertical\"}], children=\n" +
                 "[TagSnapshot{Button, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}], children=\n" +
                 "[]\n" +
                 "}, TagSnapshot{TextView, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}], children=\n" +
                 "[]\n" +
                 "}]\n" +
                 "}]\n" +
                 "}",
                 synthetic.toString());
  }

  public void testAaptAttr() {
    XmlTag image = newBuilder("ImageView")
      .setAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .setAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .addChild(
        newBuilder("aapt:attr")
          .setNamespace(AAPT_URI)
          .setAttribute(null, null, "name", "android:src")
          .addChild(
            newBuilder("vector")
          )
      )
      .build();
    TagSnapshot root = TagSnapshot.createTagSnapshot(image, null);
    String expectedId = Long.toString(AaptAttrAttributeSnapshot.ourUniqueId.get() - 1);
    assertEquals("TagSnapshot{ImageView, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}, AttributeSnapshot{src=\"@aapt:_aapt/" + expectedId + "\"}], children=\n" +
                 "[]\n" +
                 "}",
                 root.toString());
  }
}
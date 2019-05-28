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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.intellij.lang.annotations.Language;
import org.junit.Rule;
import org.junit.Test;

public class TagSnapshotTest {
  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();

  @Test
  public void testSnapshotCreatingIf() {
    @Language("XML") final String layoutString = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                 "  android:orientation=\"vertical\">\"\n" +
                                                 "  <Button\n" +
                                                 "    android:layout_width=\"wrap_content\"\n" +
                                                 "    android:layout_height=\"wrap_content\" />\n" +
                                                 "  <TextView\n" +
                                                 "    android:layout_width=\"wrap_content\"\n" +
                                                 "    android:layout_height=\"wrap_content\" />\n" +
                                                 "</LinearLayout>";

    AtomicInteger callCount = new AtomicInteger(0);

    TagSnapshot snapshot = ApplicationManager.getApplication().runReadAction((Computable<TagSnapshot>)() -> {
      XmlTag linearLayout = XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText(layoutString);
      return TagSnapshot.createTagSnapshot(linearLayout, (tag) -> {
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
    });

    // Once per element
    assertEquals(3, callCount.get());
    TagSnapshot synthetic = ApplicationManager.getApplication().runReadAction((Computable<TagSnapshot>)() -> {
      XmlTag tag = XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText("<transformed />");
      return TagSnapshot.createSyntheticTag(tag, "synthetic", null, null,
                                            Collections.emptyList(), Collections.emptyList(), null);
    });
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

  @Test
  public void testAaptAttr() {
    @Language("XML") final String imageString = "<ImageView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                "  android:layout_width=\"wrap_content\"\n" +
                                                "  android:layout_height=\"wrap_content\">\"\n" +
                                                "  <aapt:attr\n" +
                                                "    xmlns:aapt=\"http://schemas.android.com/aapt\"\n" +
                                                "    name=\"android:src\">\n" +
                                                "    <vector />" +
                                                "  </aapt:attr>\n" +
                                                "</ImageView>";

    TagSnapshot root = ApplicationManager.getApplication().runReadAction((Computable<TagSnapshot>)() -> {
      XmlTag image = XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText(imageString);
      return TagSnapshot.createTagSnapshot(image, null);
    });
    String expectedId = Long.toString(AaptAttrAttributeSnapshot.ourUniqueId.get() - 1);
    assertEquals(
      "TagSnapshot{ImageView, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}, AttributeSnapshot{src=\"@aapt:_aapt/aapt" +
      expectedId +
      "\"}], children=\n" +
      "[]\n" +
      "}",
      root.toString());
  }
}
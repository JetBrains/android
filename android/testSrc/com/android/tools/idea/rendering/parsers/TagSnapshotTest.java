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
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.XmlElementFactory;
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
      RenderXmlTag linearLayout = new PsiXmlTag(XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText(layoutString));
      return TagSnapshot.createTagSnapshot(linearLayout, (tag) -> {
        callCount.incrementAndGet();

        if ("LinearLayout".equals(tag.tagName)) {
          assertThat(tag.getAttribute(ATTR_ORIENTATION, ANDROID_URI)).isEqualTo(VALUE_VERTICAL);
          assertThat(tag.children.size()).isEqualTo(2);
        }
        else {
          assertThat(tag.getAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI)).isEqualTo(VALUE_WRAP_CONTENT);
          assertThat(tag.getAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI)).isEqualTo(VALUE_WRAP_CONTENT);
          assertThat(tag.children.size()).isEqualTo(0);
        }
      });
    });

    // Once per element
    assertThat(callCount.get()).isEqualTo(3);
    TagSnapshot synthetic = ApplicationManager.getApplication().runReadAction((Computable<TagSnapshot>)() -> {
      RenderXmlTag tag = new PsiXmlTag(XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText("<transformed />"));
      return TagSnapshot.createSyntheticTag(tag, "synthetic", null, null,
                                            Collections.emptyList(), Collections.emptyList(), null);
    });
    synthetic.children = Collections.singletonList(snapshot);
    assertThat(synthetic.toString())
      .isEqualTo("TagSnapshot{synthetic, attributes=[], children=\n" +
                 "[TagSnapshot{LinearLayout, attributes=[AttributeSnapshot{orientation=\"vertical\"}], children=\n" +
                 "[TagSnapshot{Button, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}], children=\n" +
                 "[]\n" +
                 "}, TagSnapshot{TextView, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}], children=\n" +
                 "[]\n" +
                 "}]\n" +
                 "}]\n" +
                 "}");
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
      RenderXmlTag image = new PsiXmlTag(XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText(imageString));
      return TagSnapshot.createTagSnapshot(image, null);
    });
    String expectedId = Long.toString(AaptAttrAttributeSnapshot.ourUniqueId.get() - 1);
    assertThat(root.toString()).isEqualTo(
      "TagSnapshot{ImageView, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}, AttributeSnapshot{src=\"@aapt:_aapt/aapt" +
      expectedId +
      "\"}], children=\n" +
      "[]\n" +
      "}");
  }


  @Test
  public void testTagReplace() {
    @Language("XML") final String imageString = "<ImageView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                "  xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                                                "  android:layout_width=\"wrap_content\"\n" +
                                                "  android:layout_height=\"wrap_content\"\n" +
                                                "  tools:useTag=\"Button\" />";

    TagSnapshot button = ApplicationManager.getApplication().runReadAction((Computable<TagSnapshot>)() -> {
      RenderXmlTag image = new PsiXmlTag(XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText(imageString));
      return TagSnapshot.createTagSnapshot(image, null);
    });
    assertThat(button.toString()).isEqualTo(
      "TagSnapshot{Button, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}, AttributeSnapshot{useTag=\"Button\"}], children=\n" +
      "[]\n" +
      "}");
  }

  @Test
  public void testComposeViewReplacement() {
    @Language("XML") final String imageString = "<androidx.compose.ui.platform.ComposeView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                "  android:layout_width=\"wrap_content\"\n" +
                                                "  android:layout_height=\"wrap_content\" />";

    TagSnapshot button = ApplicationManager.getApplication().runReadAction((Computable<TagSnapshot>)() -> {
      RenderXmlTag image = new PsiXmlTag(XmlElementFactory.getInstance(myProjectRule.getProject()).createTagFromText(imageString));
      return TagSnapshot.createTagSnapshot(image, null);
    });
    assertThat(button.toString()).isEqualTo(
      "TagSnapshot{androidx.compose.ui.tooling.ComposeViewAdapter, attributes=[AttributeSnapshot{layout_width=\"wrap_content\"}, AttributeSnapshot{layout_height=\"wrap_content\"}], children=\n" +
      "[]\n" +
      "}");
  }
}
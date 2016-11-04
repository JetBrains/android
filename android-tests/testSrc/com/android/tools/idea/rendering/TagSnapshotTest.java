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

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import junit.framework.TestCase;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TagSnapshotTest extends TestCase {
  public void test() {
    XmlTag button = setAttributes(createTag("Button"), androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT),
                                    androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT));
    XmlTag textView = setAttributes(createTag("TextView"), androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT),
                                    androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT));
    XmlTag linearLayout = createTag("LinearLayout", button, textView);
    setAttributes(linearLayout, androidAttribute(ATTR_ORIENTATION, VALUE_VERTICAL));

    TagSnapshot snapshot = TagSnapshot.createTagSnapshot(linearLayout);
    TagSnapshot synthetic = TagSnapshot.createSyntheticTag(createTag("transformed"), "synthetic", null, null,
                                                           Collections.<AttributeSnapshot>emptyList(),
                                                           Collections.<TagSnapshot>emptyList());
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

  private static XmlAttribute androidAttribute(String localName, String value) {
    return createAttribute(ANDROID_URI, PREFIX_ANDROID, localName, value);
  }

  private static XmlAttribute createAttribute(String namespace, String prefix, String localName, String value) {
    XmlAttribute attribute = mock(XmlAttribute.class);
    when(attribute.getLocalName()).thenReturn(localName);
    when(attribute.getNamespace()).thenReturn(namespace);
    when(attribute.getNamespacePrefix()).thenReturn(prefix);
    when(attribute.getValue()).thenReturn(value);
    return attribute;
  }

  private static XmlTag createTag(String tagName, XmlTag... subtags) {
    XmlTag tag = mock(XmlTag.class);
    when(tag.getName()).thenReturn(tagName);
    when(tag.getSubTags()).thenReturn(subtags);
    return tag;
  }

  private static XmlTag setAttributes(XmlTag tag, XmlAttribute... attributes) {
    when(tag.getAttributes()).thenReturn(attributes);
    return tag;
  }
}
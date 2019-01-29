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

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XmlTagMockBuilder {
  private final String tagName;
  private String namespace = null;
  private List<XmlTag> subtags = new LinkedList<>();
  private List<XmlAttribute> attributes = new LinkedList<>();
  private XmlTagMockBuilder parent = null;
  private XmlTag builtTag = null;

  private XmlTagMockBuilder(String name) {
    tagName = name;
  }

  public XmlTagMockBuilder setNamespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  private void setParent(XmlTagMockBuilder builder) {
    parent = builder;
  }

  @NotNull
  XmlTagMockBuilder addChild(XmlTagMockBuilder tag) {
    tag.setParent(this);
    subtags.add(tag.build());
    return this;
  }

  @NotNull
  XmlTagMockBuilder setAttribute(String namespace, String prefix, String localName, String value) {
    XmlAttribute attribute = mock(XmlAttribute.class);
    when(attribute.getName()).thenReturn(prefix != null ? prefix + ":" + localName : localName);
    when(attribute.getLocalName()).thenReturn(localName);
    when(attribute.getNamespace()).thenReturn(namespace);
    when(attribute.getNamespacePrefix()).thenReturn(prefix);
    when(attribute.getValue()).thenReturn(value);

    attributes.add(attribute);

    return this;
  }

  @NotNull
  XmlTagMockBuilder setAttribute(String localName, String value) {
    return setAttribute(ANDROID_URI, PREFIX_ANDROID, localName, value);
  }

  XmlTag build() {
    String prefix = XmlUtil.findPrefixByQualifiedName(tagName);
    if (!prefix.isEmpty() && namespace == null) {
      Assert.fail("Prefix provided but namespace is missing");
    }

    XmlTag tag = mock(XmlTag.class);
    when(tag.getName()).thenReturn(XmlUtil.findLocalNameByQualifiedName(tagName));
    when(tag.getNamespace()).thenReturn(namespace);
    when(tag.getNamespacePrefix()).thenReturn(prefix);
    when(tag.getSubTags()).thenReturn(subtags.toArray(XmlTag.EMPTY));
    when(tag.getLocalName()).thenReturn(tagName.contains(":") ? tagName.split("\\:")[1] : tagName);
    when(tag.getAttributes()).thenReturn(attributes.toArray(XmlAttribute.EMPTY_ARRAY));
    when(tag.getParentTag()).then(params -> parent.builtTag);
    when(tag.getParent()).then(params -> parent.builtTag);
    when(tag.getAttribute(anyString())).thenAnswer(params -> attributes.stream()
      .filter(attr -> params.getArguments()[0].equals(attr.getLocalName()))
      .findFirst()
      .get());
    when(tag.getAttributeValue(anyString())).thenAnswer(params -> attributes.stream()
      .filter(attr -> params.getArguments()[0].equals(attr.getLocalName()))
      .map(XmlAttribute::getValue)
      .findFirst()
      .get());

    builtTag = tag;

    return tag;
  }

  public static XmlTagMockBuilder newBuilder(String name) {
    return new XmlTagMockBuilder(name);
  }
}

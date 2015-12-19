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

import com.android.tools.idea.databinding.DataBindingUtil;
import com.google.common.collect.Lists;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.PREFIX_BINDING_EXPR;

/**
 * A snapshot of an attribute value pulled from PSI. Used in conjunction with {@link TagSnapshot}.
 */
public class AttributeSnapshot {
  @Nullable public String namespace;
  @Nullable public String prefix;
  @NotNull public String name;
  @Nullable public String value;

  AttributeSnapshot(@Nullable String namespace, @Nullable String prefix, @NotNull String name, @Nullable String value) {
    this.namespace = namespace;
    this.prefix = prefix == null || prefix.isEmpty() ? null : prefix;
    this.name = name;
    this.value = value;
  }

  /**
   * Creates a snapshot of the given attribute.
   * <p>
   * NOTE: Returns null if the attribute should not be passed to LayoutLib.
   */
  @Nullable
  public static AttributeSnapshot createAttributeSnapshot(@NotNull XmlAttribute psiAttribute) {
    String localName = psiAttribute.getLocalName();
    String namespace = psiAttribute.getNamespace();
    String prefix = psiAttribute.getNamespacePrefix();
    String value = psiAttribute.getValue();
    if (value != null && value.startsWith(PREFIX_BINDING_EXPR)) {
      // if this is a binding expression, get the default value.
      value = DataBindingUtil.getBindingExprDefault(psiAttribute);
      if (value == null) {
        // If no default value, strip the attribute completely.
        return null;
      }
    }
    return new AttributeSnapshot(namespace, prefix, localName, value);
  }

  /** Creates a list of attribute snapshots corresponding to the attributes of the given tag */
  @NotNull
  public static List<AttributeSnapshot> createAttributesForTag(@NotNull XmlTag tag) {
    // Attributes
    XmlAttribute[] psiAttributes = tag.getAttributes();
    List<AttributeSnapshot> attributes = Lists.newArrayListWithExpectedSize(psiAttributes.length);
    for (XmlAttribute psiAttribute : psiAttributes) {
      AttributeSnapshot attribute = createAttributeSnapshot(psiAttribute);
      if (attribute != null) {
        attributes.add(attribute);
      }
    }

    return attributes;
  }

  @Override
  public String toString() {
    return "AttributeSnapshot{" + name + "=\"" + value + "\"}";
  }
}

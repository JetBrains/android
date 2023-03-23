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

import com.android.tools.idea.databinding.util.DataBindingUtil;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
  public static AttributeSnapshot createAttributeSnapshot(@NotNull RenderXmlAttribute attribute) {
    String localName = attribute.getLocalName();
    String namespace = attribute.getNamespace();
    String prefix = attribute.getNamespacePrefix();
    String value = attribute.getValue();
    if (value != null && DataBindingUtil.isBindingExpression(value)) {
      // if this is a binding expression, get the default value.
      value = attribute.getBindingExprDefault();
      if (value == null) {
        // If no default value, strip the attribute completely.
        return null;
      }
    }
    return new AttributeSnapshot(namespace, prefix, localName, value);
  }

  /** Creates a list of attribute snapshots corresponding to the attributes of the given tag */
  @NotNull
  public static List<AttributeSnapshot> createAttributesForTag(@NotNull RenderXmlTag tag) {
    // Attributes
    List<RenderXmlAttribute> renderAttributes = tag.getAttributes();
    List<AttributeSnapshot> attributes = Lists.newArrayListWithExpectedSize(renderAttributes.size());
    for (RenderXmlAttribute renderAttribute : renderAttributes) {
      if (renderAttribute.isNamespaceDeclaration()) {
        // Do not snapshot namespace declaration
        continue;
      }

      AttributeSnapshot attribute = createAttributeSnapshot(renderAttribute);
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

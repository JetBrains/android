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

import com.google.common.collect.Lists;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A snapshot of the state of an {@link XmlTag}.
 * <p>
 * Used by the rendering architecture to be able to hold a consistent view of
 * the layout files across a long rendering operation without holding read locks,
 * as well as to for example let the property sheet evaluate and paint the values
 * of properties as they were at the time of rendering, not as they are at the current
 * instant.
 */
public class TagSnapshot {
  @Nullable public final String namespace;
  @NotNull public final String tagName;
  @Nullable public final XmlTag tag;
  @Nullable public final String prefix;

  @Nullable private TagSnapshot myNext;
  @Nullable public List<TagSnapshot> children;
  @Nullable public List<AttributeSnapshot> attributes;

  private TagSnapshot(@Nullable XmlTag tag, @Nullable String tagName, @Nullable String prefix, @Nullable String namespace) {
    this.tagName = tagName != null ? tagName : "?";
    this.prefix = prefix == null || prefix.isEmpty() ? null : prefix;
    this.namespace = namespace;
    this.tag = tag;
  }

  private TagSnapshot(@NotNull XmlTag tag) {
    this(tag, tag.getName(), tag.getNamespacePrefix(), tag.getNamespace());
  }

  public static TagSnapshot createSyntheticTag(@Nullable XmlTag tag, @Nullable String tagName, @Nullable String prefix,
                                               @Nullable String namespace) {
    return new TagSnapshot(tag, tagName, prefix, namespace);
  }

  @NotNull
  public static TagSnapshot createTagSnapshot(@NotNull XmlTag tag) {
    TagSnapshot element = new TagSnapshot(tag);

    // Attributes
    element.attributes = AttributeSnapshot.createAttributesForTag(tag);

    // Children
    XmlTag[] subTags = tag.getSubTags();
    if (subTags.length > 0) {
      TagSnapshot last = null;
      List<TagSnapshot> children = Lists.newArrayListWithExpectedSize(subTags.length);
      element.children = children;
      for (XmlTag subTag : subTags) {
        TagSnapshot child = createTagSnapshot(subTag);
        children.add(child);
        if (last != null) {
          last.myNext = child;
        }
        last = child;
      }
    } else {
      element.children = Collections.emptyList();
    }

    return element;
  }

  @Nullable
  public String getAttribute(@NotNull String name) {
    return getAttribute(name, null);
  }

  @Nullable
  public String getAttribute(@NotNull String name, @Nullable String namespace) {
    if (attributes == null) {
      return null;
    }

    // We just use a list rather than a map since in layouts the number of attributes is
    // typically very small so map overhead isn't worthwhile
    for (AttributeSnapshot attribute : attributes) {
      if (name.equals(attribute.name) && (namespace == null || namespace.equals(attribute.namespace))) {
        return attribute.value;
      }
    }

    return null;
  }

  /**
   * Sets the given attribute in the snapshot; this should <b>only</b> be done during snapshot hierarchy
   * construction, not later
   */
  public void setAttribute(@NotNull String name, @Nullable String namespace, @Nullable String prefix, @Nullable String value) {
    if (attributes == null) {
      attributes = Lists.newArrayList();
    } else {
      for (AttributeSnapshot attribute : attributes) {
        if (name.equals(attribute.name) && (namespace == null || namespace.equals(attribute.namespace))) {
          attributes.remove(attribute);
          break;
        }
      }
    }
    if (value != null) {
      attributes.add(new AttributeSnapshot(namespace, prefix, name, value));
    }
  }

  @Nullable
  public TagSnapshot getNextSibling() {
    return myNext;
  }
}

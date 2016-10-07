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
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.google.common.base.Charsets.UTF_8;

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
  @NotNull  public final String tagName;
  @Nullable public final XmlTag tag;
  @Nullable public final String prefix;

  @Nullable private TagSnapshot myNext;
  @NotNull public List<TagSnapshot> children;
  @NotNull public List<AttributeSnapshot> attributes;

  private TagSnapshot(@Nullable XmlTag tag, @Nullable String tagName, @Nullable String prefix, @Nullable String namespace,
                      @NotNull List<AttributeSnapshot> attributes, @NotNull List<TagSnapshot> children) {
    this.tagName = tagName != null ? tagName : "?";
    this.prefix = prefix == null || prefix.isEmpty() ? null : prefix;
    this.namespace = namespace;
    this.tag = tag;
    this.attributes = attributes;
    this.children = children;
  }

  public static TagSnapshot createSyntheticTag(@Nullable XmlTag tag, @Nullable String tagName, @Nullable String prefix,
                                               @Nullable String namespace, @NotNull List<AttributeSnapshot> attributes,
                                               @NotNull List<TagSnapshot> children) {
    return new TagSnapshot(tag, tagName, prefix, namespace, attributes, children);
  }

  @NotNull
  public static TagSnapshot createTagSnapshot(@NotNull XmlTag tag) {
    // Attributes
    List<AttributeSnapshot> attributes = AttributeSnapshot.createAttributesForTag(tag);

    // Children
    List<TagSnapshot> children;
    XmlTag[] subTags = tag.getSubTags();
    if (subTags.length > 0) {
      TagSnapshot last = null;
      children = Lists.newArrayListWithCapacity(subTags.length);
      for (XmlTag subTag : subTags) {
        TagSnapshot child = createTagSnapshot(subTag);
        children.add(child);
        if (last != null) {
          last.myNext = child;
        }
        last = child;
      }
    } else {
      children = Collections.emptyList();
    }

    return new TagSnapshot(tag, tag.getName(), tag.getNamespacePrefix(), tag.getNamespace(), attributes, children);
  }

  @NotNull
  public static TagSnapshot createTagSnapshotWithoutChildren(@NotNull XmlTag tag) {
    List<AttributeSnapshot> attributes = AttributeSnapshot.createAttributesForTag(tag);
    List<TagSnapshot> children = Collections.emptyList();
    return new TagSnapshot(tag, tag.getName(), tag.getNamespacePrefix(), tag.getNamespace(), attributes, children);
  }

  @Nullable
  public String getAttribute(@NotNull String name) {
    return getAttribute(name, null);
  }

  @Nullable
  public String getAttribute(@NotNull String name, @Nullable String namespace) {
    // We just use a list rather than a map since in layouts the number of attributes is
    // typically very small so map overhead isn't worthwhile

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = attributes.size(); i < n; i++) {
      AttributeSnapshot attribute = attributes.get(i);
      if (name.equals(attribute.name) && (namespace == null || namespace.equals(attribute.namespace))) {
        return attribute.value;
      }
    }

    return null;
  }

  /**
   * Sets the given attribute in the snapshot; this should <b>only</b> be done during snapshot hierarchy
   * construction, not later, with the sole exception of the property sheet: In the case of the property sheet,
   * there is a short time between the user editing a value to when the rendering is complete during which the
   * snapshot value will be out of date unless it is updated via this API.
   */
  public void setAttribute(@NotNull String name, @Nullable String namespace, @Nullable String prefix, @Nullable String value) {
    for (int i = 0, n = attributes.size(); i < n; i++) {
      AttributeSnapshot attribute = attributes.get(i);
      if (name.equals(attribute.name) && (namespace == null || namespace.equals(attribute.namespace))) {
        attributes.remove(i);
        break;
      }
    }
    if (value != null) {
      if (attributes.isEmpty()) {
        // attributes may point to Collections.emptyList() when empty, which isn't mutable
        attributes = Lists.newArrayList();
      }
      attributes.add(new AttributeSnapshot(namespace, prefix, name, value));
    }
  }

  @Nullable
  public TagSnapshot getNextSibling() {
    return myNext;
  }

  @Override
  public String toString() {
    return "TagSnapshot{" + tagName + ", attributes=" + attributes + ", children=\n" + children + "\n}";
  }

  /** Creates a signature/fingerprint of this tag snapshot (which encapsulates the tag name and attributes */
  public long getSignature() {
    HashFunction hashFunction = Hashing.goodFastHash(64);
    Hasher hasher = hashFunction.newHasher();
    hasher.putString(tagName, UTF_8);
    for (AttributeSnapshot attribute : attributes) {
      if (attribute.prefix != null) {
        hasher.putString(attribute.prefix, UTF_8);
      }
      hasher.putString(attribute.name, UTF_8);
      if (attribute.value != null) {
        hasher.putString(attribute.value, UTF_8);
      }
      // Note that we're not bothering with namespaces here; the prefix will identify it uniquely
    }
    return hasher.hash().asLong();
  }
}

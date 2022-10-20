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

import static com.android.SdkConstants.AAPT_URI;
import static com.android.SdkConstants.ATTR_ATTR;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_USE_TAG;
import static com.android.SdkConstants.CLASS_COMPOSE_VIEW;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.compose.ComposeLibraryNamespaceKt.COMPOSE_VIEW_ADAPTER_FQN;
import static com.google.common.base.Charsets.UTF_8;

import com.google.common.collect.Lists;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @NotNull public Map<String, String> namespaceDeclarations;
  /** Whether this element or any of its children has any aapt:attr definitions */
  public boolean hasDeclaredAaptAttrs = false;

  private TagSnapshot(@Nullable XmlTag tag, @Nullable String tagName, @Nullable String prefix, @Nullable String namespace,
                      @NotNull List<AttributeSnapshot> attributes, @NotNull List<TagSnapshot> children, boolean hasDeclaredAaptAttrs) {
    this.tagName = tagName != null ? tagName : "?";
    this.prefix = prefix == null || prefix.isEmpty() ? null : prefix;
    this.namespace = namespace;
    this.tag = tag;
    this.attributes = attributes;
    this.children = children;
    this.hasDeclaredAaptAttrs = hasDeclaredAaptAttrs;
    this.namespaceDeclarations = tag != null ? tag.getLocalNamespaceDeclarations() : Collections.emptyMap();
  }

  /**
   * Creates a new tag snapshot with all the properties passed as arguments
   * @see #TagSnapshot(XmlTag, String, String, String, List, List, boolean)
   *
   * @param afterCreate If not null, it will be applied to the {@link TagSnapshot} created in a post processing step
   */
  static TagSnapshot createSyntheticTag(@Nullable XmlTag tag, @Nullable String tagName, @Nullable String prefix,
                                               @Nullable String namespace, @NotNull List<AttributeSnapshot> attributes,
                                               @NotNull List<TagSnapshot> children, @Nullable Consumer<TagSnapshot> afterCreate) {
    TagSnapshot newSnapshot = new TagSnapshot(tag, tagName, prefix, namespace, attributes, children, false);
    if (afterCreate != null) {
      afterCreate.accept(newSnapshot);
    }
    return newSnapshot;
  }

  @Nullable
  private static String getTagNameForSnapshot(@NotNull XmlTag tag) {
    XmlAttribute useTagAttribute = tag.getAttribute(ATTR_USE_TAG, TOOLS_URI);
    String tagName = useTagAttribute == null ? tag.getName() : useTagAttribute.getValue();

    // ComposeView gets replaced with ComposeViewAdapter so it can be rendered correctly within the Layout Editor.
    // The ComposeView requires a ViewTreeLifecycleOwner but, since the Layout Editor does not run within an activity, there is not one.
    // ComposeViewAdapter provides that logic allowing for transparently replace it.
    return CLASS_COMPOSE_VIEW.equals(tagName) ? COMPOSE_VIEW_ADAPTER_FQN : tagName;
  }

  /**
   * Creates a new tag snapshot starting at the given tag
   * @param tag The root tag to create the snapshot from
   * @param afterCreate If not null, this will be called for every new {@link TagSnapshot} created by this call
   */
  @NotNull
  public static TagSnapshot createTagSnapshot(@NotNull XmlTag tag, @Nullable Consumer<TagSnapshot> afterCreate) {
    // Attributes
    List<AttributeSnapshot> attributes = AttributeSnapshot.createAttributesForTag(tag);

    // Children
    List<TagSnapshot> children;
    XmlTag[] subTags = tag.getSubTags();
    boolean hasDeclaredAaptAttrs = false;
    if (subTags.length > 0) {
      TagSnapshot last = null;
      children = Lists.newArrayListWithCapacity(subTags.length);
      for (XmlTag subTag : subTags) {
        if (AAPT_URI.equals(subTag.getNamespace())) {
          if (ATTR_ATTR.equals(subTag.getLocalName()) && subTag.getAttribute(ATTR_NAME) != null) {
            AaptAttrAttributeSnapshot aaptAttribute = AaptAttrAttributeSnapshot.createAttributeSnapshot(subTag);
            if (aaptAttribute != null) {
              attributes.add(aaptAttribute);
              hasDeclaredAaptAttrs = true;
            }
          }
          // Since we save the aapt:attr tags as an attribute, we do not save them as a child element. Skip.
          continue;
        }

        TagSnapshot child = createTagSnapshot(subTag, afterCreate);
        hasDeclaredAaptAttrs |= child.hasDeclaredAaptAttrs;
        children.add(child);
        if (last != null) {
          last.myNext = child;
        }
        last = child;
      }
    } else {
      children = Collections.emptyList();
    }

    TagSnapshot newSnapshot =
      new TagSnapshot(tag, getTagNameForSnapshot(tag), tag.getNamespacePrefix(),
                      tag.getNamespace(), attributes, children, hasDeclaredAaptAttrs);
    if (afterCreate != null) {
      afterCreate.accept(newSnapshot);
    }

    return newSnapshot;
  }

  @NotNull
  public static TagSnapshot createTagSnapshotWithoutChildren(@NotNull XmlTag tag) {
    List<AttributeSnapshot> attributes = AttributeSnapshot.createAttributesForTag(tag);

    boolean hasDeclaredAaptAttrs = false;
    for (XmlTag subTag : tag.getSubTags()) {
      if (AAPT_URI.equals(subTag.getNamespace())) {
        if (ATTR_ATTR.equals(subTag.getLocalName()) && subTag.getAttribute(ATTR_NAME) != null) {
          AaptAttrAttributeSnapshot aaptAttribute = AaptAttrAttributeSnapshot.createAttributeSnapshot(subTag);
          if (aaptAttribute != null) {
            attributes.add(aaptAttribute);
            hasDeclaredAaptAttrs = true;
          }
        }
      }
    }

    return new TagSnapshot(
      tag,
      getTagNameForSnapshot(tag), tag.getNamespacePrefix(), tag.getNamespace(),
      attributes,
      Collections.emptyList(),
      hasDeclaredAaptAttrs);
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
  public void setAttribute(@NotNull String name,
                           @Nullable String namespace,
                           @Nullable String prefix,
                           @Nullable String value,
                           boolean overrideIfExists) {
    for (int i = 0, n = attributes.size(); i < n; i++) {
      AttributeSnapshot attribute = attributes.get(i);
      if (name.equals(attribute.name) && (namespace == null || namespace.equals(attribute.namespace))) {
        if (overrideIfExists) {
          attributes.remove(i);
        }
        else {
          return;
        }
        break;
      }
    }
    if (value != null) {
      if (attributes.isEmpty()) {
        // attributes may point to Collections.emptyList() when empty, which isn't mutable
        attributes = new ArrayList<>();
      }
      attributes.add(new AttributeSnapshot(namespace, prefix, name, value));
    }
  }

  /**
   * Sets the given attribute in the snapshot; this should <b>only</b> be done during snapshot hierarchy
   * construction, not later, with the sole exception of the property sheet: In the case of the property sheet,
   * there is a short time between the user editing a value to when the rendering is complete during which the
   * snapshot value will be out of date unless it is updated via this API.
   */
  public void setAttribute(@NotNull String name, @Nullable String namespace, @Nullable String prefix, @Nullable String value) {
    setAttribute(name, namespace, prefix, value, true);
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

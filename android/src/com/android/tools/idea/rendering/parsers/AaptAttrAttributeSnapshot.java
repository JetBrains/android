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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

import static com.android.SdkConstants.AAPT_ATTR_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;

/**
 * Aapt attributes are attributes that instead of containing a reference, contain the inlined value of the reference. This snapshot will
 * generate a dynamic reference that will be used by the resource resolution to be able to retrieve the inlined value.
 */
class AaptAttrAttributeSnapshot extends AttributeSnapshot {
  /**
   * Each attribute must keep a dynamic unique ID so it can be referenced by the parent. We simply generate a sequential number.
   * This counter is thread safe in order to be able to use {@link AaptAttrAttributeSnapshot} in parallel streams.
   */
  @VisibleForTesting
  static final AtomicLong ourUniqueId = new AtomicLong(0L);

  private final String myId;
  private final TagSnapshot myBundledTag;

  AaptAttrAttributeSnapshot(@Nullable String namespace, @Nullable String prefix, @NotNull String name, @NotNull String id, @NotNull TagSnapshot bundledTag) {
    super(namespace, prefix, name, AAPT_ATTR_PREFIX + id);

    myId = id;
    myBundledTag = bundledTag;
  }

  /**
   * Creates an attribute snapshot for an appt:attr item.
   */
  @Nullable
  public static AaptAttrAttributeSnapshot createAttributeSnapshot(@NotNull XmlTag tag) {
    XmlTag parent = tag.getParentTag();
    String name = tag.getAttributeValue(ATTR_NAME);
    if (parent == null || name == null) {
      return null;
    }

    XmlTag[] subTags = tag.getSubTags();
    if (subTags.length == 0) {
      return null;
    }

    XmlTag bundledTag = tag.getSubTags()[0];

    String prefix = XmlUtil.findPrefixByQualifiedName(name);

    // Generate a dynamic reference back to the child TagSnapshot
    return new AaptAttrAttributeSnapshot(
      tag.getNamespaceByPrefix(prefix),
      prefix,
      XmlUtil.findLocalNameByQualifiedName(name), Long.toString(ourUniqueId.getAndIncrement()),
      TagSnapshot.createTagSnapshot(bundledTag, null));
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public TagSnapshot getBundledTag() {
    return myBundledTag;
  }
}

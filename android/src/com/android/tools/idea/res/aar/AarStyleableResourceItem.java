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
package com.android.tools.idea.res.aar;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.res.aar.Base128InputStream.StreamFormatException;
import com.google.common.collect.ImmutableList;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a styleable resource.
 */
final class AarStyleableResourceItem extends AbstractAarValueResourceItem implements StyleableResourceValue {
  @NotNull private final List<AttrResourceValue> myAttrs;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param attrs the attributes of the styleable
   */
  public AarStyleableResourceItem(@NotNull String name,
                                  @NotNull AarSourceFile sourceFile,
                                  @NotNull ResourceVisibility visibility,
                                  @NotNull List<AttrResourceValue> attrs) {
    super(ResourceType.STYLEABLE, name, sourceFile, visibility);
    myAttrs = ImmutableList.copyOf(attrs);
  }

  @Override
  @NotNull
  public List<AttrResourceValue> getAllAttributes() {
    return myAttrs;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarStyleableResourceItem other = (AarStyleableResourceItem) obj;
    return myAttrs.equals(other.myAttrs);
  }

  @Override
  void serialize(@NotNull Base128OutputStream stream,
                 @NotNull ObjectIntHashMap<String> configIndexes,
                 @NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                 @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeInt(myAttrs.size());
    for (AttrResourceValue attr : myAttrs) {
      ((AbstractAarValueResourceItem)attr).serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    }
  }

  /**
   * Creates an AarStyleableResourceItem by reading its contents of the given stream.
   */
  @NotNull
  static AarStyleableResourceItem deserialize(@NotNull Base128InputStream stream,
                                              @NotNull String name,
                                              @NotNull ResourceVisibility visibility,
                                              @NotNull AarSourceFile sourceFile,
                                              @NotNull ResourceNamespace.Resolver resolver,
                                              @NotNull List<AarConfiguration> configurations,
                                              @NotNull List<AarSourceFile> sourceFiles,
                                              @NotNull List<ResourceNamespace.Resolver> namespaceResolvers) throws IOException {
    AbstractAarResourceRepository repository = sourceFile.getConfiguration().getRepository();
    int n = stream.readInt();
    List<AttrResourceValue> attrs = n == 0 ? Collections.emptyList() : new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      AbstractAarResourceItem attrItem = deserialize(stream, configurations, sourceFiles, namespaceResolvers);
      if (!(attrItem instanceof AarAttrResourceItem)) {
        throw StreamFormatException.invalidFormat();
      }
      AttrResourceValue attr = getCanonicalAttr((AarAttrResourceItem)attrItem, repository);
      attrs.add(attr);
    }
    AarStyleableResourceItem item = new AarStyleableResourceItem(name, sourceFile, visibility, attrs);
    item.setNamespaceResolver(resolver);
    return item;
  }

  @NotNull
  private static AttrResourceValue getCanonicalAttr(@NotNull AarAttrResourceItem attr, @NotNull AbstractAarResourceRepository repository) {
    List<ResourceItem> items = repository.getResources(attr.getNamespace(), ResourceType.ATTR, attr.getName());
    for (ResourceItem item : items) {
      if (item.equals(attr)) {
        return (AarAttrResourceItem)item;
      }
    }
    return attr;
  }
}

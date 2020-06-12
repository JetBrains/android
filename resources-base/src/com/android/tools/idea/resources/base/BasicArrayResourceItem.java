/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.resources.base;

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing an array resource.
 */
public final class BasicArrayResourceItem extends BasicValueResourceItemBase implements ArrayResourceValue {
  @NotNull private final List<String> myElements;
  private final int myDefaultIndex;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param elements the elements  or the array
   * @param defaultIndex the default index for the {@link #getValue()} method
   */
  public BasicArrayResourceItem(@NotNull String name,
                                @NotNull ResourceSourceFile sourceFile,
                                @NotNull ResourceVisibility visibility,
                                @NotNull List<String> elements,
                                int defaultIndex) {
    super(ResourceType.ARRAY, name, sourceFile, visibility);
    myElements = elements;
    assert elements.isEmpty() || defaultIndex < elements.size();
    myDefaultIndex = defaultIndex;
  }

  @Override
  public int getElementCount() {
    return myElements.size();
  }

  @Override
  @NotNull
  public String getElement(int index) {
    return myElements.get(index);
  }

  @Override
  public Iterator<String> iterator() {
    return myElements.iterator();
  }

  @Override
  @Nullable
  public String getValue() {
    return myElements.isEmpty() ? null : myElements.get(myDefaultIndex);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicArrayResourceItem other = (BasicArrayResourceItem) obj;
    return myElements.equals(other.myElements);
  }

  @Override
  public void serialize(@NotNull Base128OutputStream stream,
                        @NotNull Object2IntMap<String> configIndexes,
                        @NotNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NotNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeInt(myElements.size());
    for (String element : myElements) {
      stream.writeString(element);
    }
    stream.writeInt(myDefaultIndex);
  }

  /**
   * Creates a BasicArrayResourceItem by reading its contents from the given stream.
   */
  @NotNull
  static BasicArrayResourceItem deserialize(@NotNull Base128InputStream stream,
                                            @NotNull String name,
                                            @NotNull ResourceVisibility visibility,
                                            @NotNull ResourceSourceFile sourceFile,
                                            @NotNull ResourceNamespace.Resolver resolver) throws IOException {
    int n = stream.readInt();
    List<String> elements = n == 0 ? Collections.emptyList() : new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      elements.add(stream.readString());
    }
    int defaultIndex = stream.readInt();
    if (!elements.isEmpty() && defaultIndex >= elements.size()) {
      throw Base128InputStream.StreamFormatException.invalidFormat();
    }
    BasicArrayResourceItem item = new BasicArrayResourceItem(name, sourceFile, visibility, elements, defaultIndex);
    item.setNamespaceResolver(resolver);
    return item;
  }
}

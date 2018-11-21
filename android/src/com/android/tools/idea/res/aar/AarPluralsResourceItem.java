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

import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a plurals resource.
 */
final class AarPluralsResourceItem extends AbstractAarValueResourceItem implements PluralsResourceValue {
  @NotNull private final List<String> myQuantities;
  @NotNull private final List<String> myValues;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param quantities the quantities, e.g. "one", "two", "few"
   * @param values the values corresponding to the quantities
   */
  public AarPluralsResourceItem(@NotNull String name,
                                @NotNull AarSourceFile sourceFile,
                                @NotNull ResourceVisibility visibility,
                                @NotNull List<String> quantities,
                                @NotNull List<String> values) {
    super(ResourceType.PLURALS, name, sourceFile, visibility);
    assert quantities.size() == values.size();
    myQuantities = quantities;
    myValues = values;
  }

  @Override
  public int getPluralsCount() {
    return myQuantities.size();
  }

  @Override
  @NotNull
  public String getQuantity(int index) {
    return myQuantities.get(index);
  }

  @Override
  @NotNull
  public String getValue(int index) {
    return myValues.get(index);
  }

  @Override
  @Nullable
  public String getValue(@NotNull String quantity) {
    for (int i = 0, n = myQuantities.size(); i < n; i++) {
      if (quantity.equals(myQuantities.get(i))) {
        return myValues.get(i);
      }
    }

    return null;
  }

  @Override
  @Nullable
  public String getValue() {
    return myValues.isEmpty() ? null : myValues.get(0);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarPluralsResourceItem other = (AarPluralsResourceItem) obj;
    return myQuantities.equals(other.myQuantities) && myValues.equals(other.myValues);
  }

  @Override
  void serialize(@NotNull Base128OutputStream stream,
                 @NotNull ObjectIntHashMap<String> configIndexes,
                 @NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                 @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    int n = myQuantities.size();
    stream.writeInt(n);
    for (int i = 0; i < n; i++) {
      stream.writeString(myQuantities.get(i));
      stream.writeString(myValues.get(i));
    }
  }

  /**
   * Creates an AarPluralsResourceItem by reading its contents of the given stream.
   */
  @NotNull
  static AarPluralsResourceItem deserialize(@NotNull Base128InputStream stream,
                                            @NotNull String name,
                                            @NotNull ResourceVisibility visibility,
                                            @NotNull AarSourceFile sourceFile,
                                            @NotNull ResourceNamespace.Resolver resolver) throws IOException {
    int n = stream.readInt();
    List<String> quantities = n == 0 ? Collections.emptyList() : new ArrayList<>(n);
    List<String> values = n == 0 ? Collections.emptyList() : new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      quantities.add(stream.readString());
      values.add(stream.readString());
    }
    AarPluralsResourceItem item = new AarPluralsResourceItem(name, sourceFile, visibility, quantities, values);
    item.setNamespaceResolver(resolver);
    return item;
  }
}

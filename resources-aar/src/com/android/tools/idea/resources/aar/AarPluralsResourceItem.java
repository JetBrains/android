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
package com.android.tools.idea.resources.aar;

import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.Arity;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a plurals resource.
 */
final class AarPluralsResourceItem extends AbstractAarValueResourceItem implements PluralsResourceValue {
  @NotNull private final Arity[] myArities;
  @NotNull private final String[] myValues;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param quantityValues the values corresponding to quantities
   */
  public AarPluralsResourceItem(@NotNull String name,
                                @NotNull AarSourceFile sourceFile,
                                @NotNull ResourceVisibility visibility,
                                @NotNull Map<Arity, String> quantityValues) {
    this(name, sourceFile, visibility,
         quantityValues.keySet().toArray(Arity.EMPTY_ARRAY), quantityValues.values().toArray(ArrayUtil.EMPTY_STRING_ARRAY));
  }

  private AarPluralsResourceItem(@NotNull String name,
                                 @NotNull AarSourceFile sourceFile,
                                 @NotNull ResourceVisibility visibility,
                                 @NotNull Arity[] arities,
                                 @NotNull String[] values) {
    super(ResourceType.PLURALS, name, sourceFile, visibility);
    myArities = arities;
    myValues = values;
  }

  @Override
  public int getPluralsCount() {
    return myArities.length;
  }

  @Override
  @NotNull
  public String getQuantity(int index) {
    return myArities[index].getName();
  }

  @Override
  @NotNull
  public String getValue(int index) {
    return myValues[index];
  }

  @Override
  @Nullable
  public String getValue(@NotNull String quantity) {
    for (int i = 0, n = myArities.length; i < n; i++) {
      if (quantity.equals(myArities[i])) {
        return myValues[i];
      }
    }

    return null;
  }

  @Override
  @Nullable
  public String getValue() {
    return myValues.length == 0 ? null : myValues[0];
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarPluralsResourceItem other = (AarPluralsResourceItem) obj;
    return Arrays.equals(myArities, other.myArities) && Arrays.equals(myValues, other.myValues);
  }

  @Override
  void serialize(@NotNull Base128OutputStream stream,
                 @NotNull ObjectIntHashMap<String> configIndexes,
                 @NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                 @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    int n = myArities.length;
    stream.writeInt(n);
    for (int i = 0; i < n; i++) {
      stream.writeInt(myArities[i].ordinal());
      stream.writeString(myValues[i]);
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
    Arity[] arities = n == 0 ? Arity.EMPTY_ARRAY : new Arity[n];
    String[] values = n == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : new String[n];
    for (int i = 0; i < n; i++) {
      arities[i] = Arity.values()[stream.readInt()];
      values[i] = stream.readString();
    }
    AarPluralsResourceItem item = new AarPluralsResourceItem(name, sourceFile, visibility, arities, values);
    item.setNamespaceResolver(resolver);
    return item;
  }
}

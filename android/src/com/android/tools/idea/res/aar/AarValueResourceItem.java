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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.IOException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a value resource, e.g. a string or a color.
 */
class AarValueResourceItem extends AbstractAarValueResourceItem {
  @Nullable private final String myValue;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param value the value associated with the resource
   */
  public AarValueResourceItem(@NotNull ResourceType type,
                              @NotNull String name,
                              @NotNull AarSourceFile sourceFile,
                              @NotNull ResourceVisibility visibility,
                              @Nullable String value) {
    super(type, name, sourceFile, visibility);
    myValue = value;
  }

  @Override
  @Nullable
  public String getValue() {
    return myValue;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarValueResourceItem other = (AarValueResourceItem) obj;
    return Objects.equals(myValue, other.myValue);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), Objects.hashCode(myValue));
  }

  @Override
  void serialize(@NotNull Base128OutputStream stream,
                 @NotNull ObjectIntHashMap<String> configIndexes,
                 @NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                 @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeString(myValue);
    String rawXmlValue = getRawXmlValue();
    stream.writeString(Objects.equals(rawXmlValue, myValue) ? null : rawXmlValue);
  }

  /**
   * Creates an AarValueResourceItem by reading its contents of the given stream.
   */
  @NotNull
  static AarValueResourceItem deserialize(@NotNull Base128InputStream stream,
                                          @NotNull ResourceType resourceType,
                                          @NotNull String name,
                                          @NotNull ResourceVisibility visibility,
                                          @NotNull AarSourceFile sourceFile,
                                          @NotNull ResourceNamespace.Resolver resolver) throws IOException {
    String value = stream.readString();
    String rawXmlValue = stream.readString();
    AarValueResourceItem item = rawXmlValue == null ?
        new AarValueResourceItem(resourceType, name, sourceFile, visibility, value) :
        new AarTextValueResourceItem(resourceType, name, sourceFile, visibility, value, rawXmlValue);
    item.setNamespaceResolver(resolver);
    return item;
  }
}

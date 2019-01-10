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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for AAR value resource items.
 */
abstract class AbstractAarValueResourceItem extends AbstractAarResourceItem {
  @NotNull private final AarSourceFile mySourceFile;
  @NotNull private ResourceNamespace.Resolver myNamespaceResolver = ResourceNamespace.Resolver.EMPTY_RESOLVER;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   */
  public AbstractAarValueResourceItem(@NotNull ResourceType type,
                                      @NotNull String name,
                                      @NotNull AarSourceFile sourceFile,
                                      @NotNull ResourceVisibility visibility) {
    super(type, name, visibility);
    mySourceFile = sourceFile;
  }

  @Override
  @Nullable
  public String getValue() {
    return null;
  }

  @Override
  public final boolean isFileBased() {
    return false;
  }

  @Override
  @NotNull
  public final FolderConfiguration getConfiguration() {
    return mySourceFile.getConfiguration().getFolderConfiguration();
  }

  @Override
  @NotNull
  protected final AbstractAarResourceRepository getRepository() {
    return mySourceFile.getConfiguration().getRepository();
  }

  @Override
  @NotNull
  public final ResourceNamespace.Resolver getNamespaceResolver() {
    return myNamespaceResolver;
  }

  public final void setNamespaceResolver(@NotNull ResourceNamespace.Resolver resolver) {
    myNamespaceResolver = resolver;
  }

  @Override
  @Nullable
  public final PathString getSource() {
    return getOriginalSource();
  }

  @Override
  @Nullable
  public final PathString getOriginalSource() {
    String sourcePath = mySourceFile.getRelativePath();
    return sourcePath == null ? null : getRepository().getOriginalSourceFile(sourcePath, false);
  }

  @NotNull
  final AarSourceFile getSourceFile() {
    return mySourceFile;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AbstractAarValueResourceItem other = (AbstractAarValueResourceItem)obj;
    return Objects.equals(mySourceFile, other.mySourceFile);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), Objects.hashCode(mySourceFile));
  }

  @Override
  void serialize(@NotNull Base128OutputStream stream,
                 @NotNull ObjectIntHashMap<String> configIndexes,
                 @NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                 @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    int index = sourceFileIndexes.get(mySourceFile);
    assert index >= 0;
    stream.writeInt(index);
    index = namespaceResolverIndexes.get(myNamespaceResolver);
    assert index >= 0;
    stream.writeInt(index);
  }

  /**
   * Creates a resource item by reading its contents of the given stream.
   */
  @NotNull
  static AbstractAarValueResourceItem deserialize(@NotNull Base128InputStream stream,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull String name,
                                                  @NotNull ResourceVisibility visibility,
                                                  @NotNull List<AarConfiguration> configurations,
                                                  @NotNull List<AarSourceFile> sourceFiles,
                                                  @NotNull List<ResourceNamespace.Resolver> namespaceResolvers) throws IOException {
    AarSourceFile sourceFile = sourceFiles.get(stream.readInt());
    ResourceNamespace.Resolver resolver = namespaceResolvers.get(stream.readInt());

    switch (resourceType) {
      case ARRAY:
        return AarArrayResourceItem.deserialize(stream, name, visibility, sourceFile, resolver);

      case ATTR:
        return AarAttrResourceItem.deserialize(stream, name, visibility, sourceFile, resolver);

      case PLURALS:
        return AarPluralsResourceItem.deserialize(stream, name, visibility, sourceFile, resolver);

      case STYLE:
        return AarStyleResourceItem.deserialize(stream, name, visibility, sourceFile, resolver, namespaceResolvers);

      case STYLEABLE:
        return AarStyleableResourceItem.deserialize(
            stream, name, visibility, sourceFile, resolver, configurations, sourceFiles, namespaceResolvers);

      default:
        return AarValueResourceItem.deserialize(stream, resourceType, name, visibility, sourceFile, resolver);
    }
  }
}

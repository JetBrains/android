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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.resources.base.Base128OutputStream;
import com.android.resources.base.BasicFileResourceItem;
import com.android.resources.base.RepositoryConfiguration;
import com.android.resources.base.ResourceSourceFile;
import com.intellij.openapi.vfs.VirtualFile;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link BasicFileResourceItem} plus the virtual file it is associated with.
 */
public class VfsFileResourceItem extends BasicFileResourceItem {
  @Nullable private final VirtualFile myVirtualFile;

  /**
   * Initializes the resource.
   *
   * @param type          the type of the resource
   * @param name          the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility    the visibility of the resource
   * @param relativePath  defines location of the resource. Exact semantics of the path may vary depending on the resource repository
   */
  public VfsFileResourceItem(@NotNull ResourceType type,
                             @NotNull String name,
                             @NotNull RepositoryConfiguration configuration,
                             @NotNull ResourceVisibility visibility,
                             @NotNull String relativePath) {
    this(type, name, configuration, visibility, relativePath,
         ((ResourceFolderRepository)configuration.getRepository()).getResourceDir().findFileByRelativePath(relativePath));
  }
  /**
   * Initializes the resource.
   *
   * @param type          the type of the resource
   * @param name          the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility    the visibility of the resource
   * @param relativePath  defines location of the resource. Exact semantics of the path may vary depending on the resource repository
   * @param virtualFile   the virtual file associated with the resource, or null of the resource is out of date
   */
  public VfsFileResourceItem(@NotNull ResourceType type,
                             @NotNull String name,
                             @NotNull RepositoryConfiguration configuration,
                             @NotNull ResourceVisibility visibility,
                             @NotNull String relativePath,
                             @Nullable VirtualFile virtualFile) {
    super(type, name, configuration, visibility, relativePath);
    myVirtualFile = virtualFile;
  }

  @Override
  public void serialize(@NotNull Base128OutputStream stream,
                        @NotNull Object2IntMap<String> configIndexes,
                        @NotNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NotNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.write(FileTimeStampLengthHasher.hash(myVirtualFile));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    VfsFileResourceItem other = (VfsFileResourceItem)obj;
    return isValid() == other.isValid();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public boolean isValid() {
    return myVirtualFile != null;
  }
}

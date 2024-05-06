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

import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.resources.base.RepositoryConfiguration;
import com.android.utils.HashCodes;
import com.google.common.base.MoreObjects;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link VfsFileResourceItem} for a density based file resource.
 */
public final class VfsDensityBasedFileResourceItem extends VfsFileResourceItem implements DensityBasedResourceValue {
  @NotNull private final Density myDensity;

  /**
   * Initializes the resource.
   *
   * @param type          the type of the resource
   * @param name          the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility    the visibility of the resource
   * @param relativePath  defines location of the resource. Exact semantics of the path may vary depending on the resource repository
   * @param density       the screen density this resource is associated with
   */
  public VfsDensityBasedFileResourceItem(@NotNull ResourceType type,
                                         @NotNull String name,
                                         @NotNull RepositoryConfiguration configuration,
                                         @NotNull ResourceVisibility visibility,
                                         @NotNull String relativePath,
                                         @NotNull Density density) {
    super(type, name, configuration, visibility, relativePath);
    myDensity = density;
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
   * @param density       the screen density this resource is associated with
   */
  public VfsDensityBasedFileResourceItem(@NotNull ResourceType type,
                                         @NotNull String name,
                                         @NotNull RepositoryConfiguration configuration,
                                         @NotNull ResourceVisibility visibility,
                                         @NotNull String relativePath,
                                         @Nullable VirtualFile virtualFile,
                                         @NotNull Density density) {
    super(type, name, configuration, visibility, relativePath, virtualFile);
    myDensity = density;
  }

  @Override
  @NotNull
  public Density getResourceDensity() {
    return myDensity;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    VfsDensityBasedFileResourceItem other = (VfsDensityBasedFileResourceItem)obj;
    return myDensity == other.myDensity;
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), myDensity.hashCode());
  }

  @Override
  protected int getEncodedDensityForSerialization() {
    return myDensity.getDpiValue();
  }

  @Override
  @NotNull
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("name", getName())
                      .add("namespace", getNamespace())
                      .add("type", getResourceType())
                      .add("source", getSource())
                      .add("density", getResourceDensity())
                      .toString();
  }
}

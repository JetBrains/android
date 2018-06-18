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

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a file resource inside an AAR, e.g. a drawable or a layout.
 */
class AarFileResourceItem extends ResourceValueImpl implements AarResourceItem {
  @NotNull private final AarConfiguration myConfiguration;
  @NotNull private final ResourceVisibility myVisibility;

  /**
   * Initializes a file resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param zipEntryPath the path of the resource zip entry inside res.apk
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   */
  public AarFileResourceItem(@NotNull ResourceType type,
                             @NotNull String name,
                             @NotNull String zipEntryPath,
                             @NotNull AarConfiguration configuration,
                             @NotNull ResourceVisibility visibility) {
    super(configuration.getRepository().getNamespace(), type, name, zipEntryPath, configuration.getRepository().getLibraryName());
    myConfiguration = configuration;
    myVisibility = visibility;
  }

  @Override
  @NotNull
  public ResourceType getType() {
    return getResourceType();
  }

  @Override
  @NotNull
  public FolderConfiguration getConfiguration() {
    return myConfiguration.getFolderConfiguration();
  }

  @Override
  @NotNull
  public ResourceReference getReferenceToSelf() {
    return asReference();
  }

  @Override
  @Nullable
  public ResourceValue getResourceValue() {
    return this;
  }

  @Override
  public boolean isFileBased() {
    return true;
  }

  @Override
  @Nullable
  public String getValue() {
    return myConfiguration.getRepository().getResourceUrl(super.getValue());
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned PathString points either to a file on disk, or to a ZIP entry inside a res.apk file.
   * In the latter case the filesystem URI part points to res.apk itself, e.g. {@code "zip:///foo/bar/res.apk"}.
   * The path part is the path of the ZIP entry containing the resource.
   */
  @Override
  @Nullable
  public PathString getSource() {
    return myConfiguration.getRepository().getPathString(super.getValue());
  }

  @Override
  @NotNull
  public ResourceVisibility getVisibility() {
    return myVisibility;
  }

  @Override
  @NotNull
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("name", getName())
                      .add("namespace", getNamespace())
                      .add("type", getType())
                      .add("source", getSource())
                      .toString();
  }
}

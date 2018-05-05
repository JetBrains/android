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
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a value resource, e.g. a string or a color.
 */
class AarValueResourceItem implements AarResourceItem {
  @NotNull private final ResourceValue myValue;
  @NotNull private final AarConfiguration myConfiguration;
  @NotNull private final ResourceVisibility myVisibility;

  public AarValueResourceItem(@NotNull ResourceValue resourceValue, @NotNull AarConfiguration configuration,
                              @NotNull ResourceVisibility visibility) {
    myValue = resourceValue;
    myConfiguration = configuration;
    myVisibility = visibility;
  }

  @Override
  @NotNull
  public final String getName() {
    return myValue.getName();
  }

  @Override
  @NotNull
  public final ResourceType getType() {
    return myValue.getResourceType();
  }

  @Override
  @NotNull
  public final ResourceNamespace getNamespace() {
    return myConfiguration.getRepository().getNamespace();
  }

  @Override
  @Nullable
  public final String getLibraryName() {
    return myConfiguration.getRepository().getLibraryName();
  }

  @Override
  @NotNull
  public final FolderConfiguration getConfiguration() {
    return myConfiguration.getFolderConfiguration();
  }

  @Override
  @NotNull
  public ResourceReference getReferenceToSelf() {
    return myValue.asReference();
  }

  @Override
  @NotNull
  public ResourceValue getResourceValue() {
    return myValue;
  }

  @Override
  public boolean isFileBased() {
    return false;
  }

  @Override
  @Nullable
  public PathString getSource() {
    // TODO: Implement
    return null;
  }

  @NotNull
  public ResourceVisibility getVisibility() {
    return myVisibility;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("name", getName())
                      .add("namespace", getNamespace())
                      .add("type", getType())
                      .toString();
  }
}

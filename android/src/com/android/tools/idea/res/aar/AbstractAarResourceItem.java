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
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Base class for AAR resource items. */
abstract class AbstractAarResourceItem implements AarResourceItem, ResourceValue {
  @NotNull private final String myName;
  @NotNull private final AarConfiguration myConfiguration;
  @NotNull private final ResourceVisibility myVisibility;

  public AbstractAarResourceItem(@NotNull String name,
                                 @NotNull AarConfiguration configuration,
                                 @NotNull ResourceVisibility visibility) {
    myName = name;
    myConfiguration = configuration;
    myVisibility = visibility;
  }

  @Override
  @NotNull
  public final ResourceType getType() {
    return getResourceType();
  }

  @Override
  @NotNull
  public final ResourceNamespace getNamespace() {
    return getRepository().getNamespace();
  }

  @Override
  @NotNull
  public final String getName() {
    return myName;
  }

  @Override
  @NotNull
  public final FolderConfiguration getConfiguration() {
    return myConfiguration.getFolderConfiguration();
  }

  @Override
  @Nullable
  public final String getLibraryName() {
    return getRepository().getLibraryName();
  }

  @Override
  @NotNull
  public final ResourceVisibility getVisibility() {
    return myVisibility;
  }

  @Override
  @NotNull
  public final ResourceReference getReferenceToSelf() {
    return asReference();
  }

  @Override
  @NotNull
  public final ResourceValue getResourceValue() {
    return this;
  }

  /** Returns true if the resource is user defined. */
  @Override
  public final boolean isUserDefined() {
    return false;
  }

  @Override
  public final boolean isFramework() {
    return getNamespace().equals(ResourceNamespace.ANDROID);
  }

  @Override
  @NotNull
  public final ResourceReference asReference() {
    return new ResourceReference(getNamespace(), getResourceType(), myName);
  }

  @NotNull
  protected final AarProtoResourceRepository getRepository() {
    return myConfiguration.getRepository();
  }

  @Override
  @NotNull
  public final String getKey() {
    String qualifiers = getConfiguration().getQualifierString();
    if (!qualifiers.isEmpty()) {
      return getType().getName() + '-' + qualifiers + '/' + getName();
    }

    return getType().getName() + '/' + getName();
  }

  /**
   * Sets the value of the resource.
   *
   * @param value the new value
   */
  @Override
  public final void setValue(@Nullable String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public final ResourceNamespace.Resolver getNamespaceResolver() {
    return ResourceNamespace.Resolver.EMPTY_RESOLVER;
  }

  /**
   * Specifies logic used to resolve namespace aliases for values that come from XML files.
   *
   * <p>This method is meant to be called by the XML parser that created this {@link
   * ResourceValue}.
   */
  @Override
  public final void setNamespaceResolver(@NotNull ResourceNamespace.Resolver resolver) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AbstractAarResourceItem other = (AbstractAarResourceItem) obj;
    return getResourceType() == other.getResourceType()
            && myName.equals(other.myName)
            && myConfiguration.equals(other.myConfiguration)
            && myVisibility.equals(other.myVisibility);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(getResourceType().hashCode(), myName.hashCode(), myConfiguration.hashCode());
  }

  @Override
  @NotNull
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("namespace", getNamespace())
                      .add("type", getResourceType())
                      .add("name", getName())
                      .add("value", getValue())
                      .toString();
  }
}

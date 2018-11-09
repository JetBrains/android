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
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Base class for AAR resource items. */
abstract class AbstractAarResourceItem implements AarResourceItem, ResourceValue {
  @NotNull private final String myName;
  // Minimize memory usage by storing enums as their ordinals in byte form.
  private final byte myTypeOrdinal;
  private final byte myVisibilityOrdinal;

  AbstractAarResourceItem(@NotNull ResourceType type, @NotNull String name, @NotNull ResourceVisibility visibility) {
    myName = name;
    myTypeOrdinal = (byte)type.ordinal();
    myVisibilityOrdinal = (byte)visibility.ordinal();
  }

  @Override
  @NotNull
  public final ResourceType getType() {
    return getResourceType();
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return getRepository().getNamespace();
  }

  @Override
  @NotNull
  public final String getName() {
    return myName;
  }

  @Override
  @NotNull
  public final String getLibraryName() {
    return getRepository().getLibraryName();
  }

  @Override
  @NotNull
  public final ResourceType getResourceType() {
    return ResourceType.values()[myTypeOrdinal];
  }

  @Override
  @NotNull
  public final ResourceVisibility getVisibility() {
    return ResourceVisibility.values()[myVisibilityOrdinal];
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

  /**
   * Returns the repository this resource belongs to.
   */
  @NotNull
  protected abstract AbstractAarResourceRepository getRepository();

  @Override
  @NotNull
  public final String getKey() {
    String qualifiers = getConfiguration().getQualifierString();
    if (!qualifiers.isEmpty()) {
      return getType().getName() + '-' + qualifiers + '/' + getName();
    }

    return getType().getName() + '/' + getName();
  }

  @Override
  public final void setValue(@Nullable String value) {
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
    return myTypeOrdinal == other.myTypeOrdinal
        && myName.equals(other.myName)
        && myVisibilityOrdinal == other.myVisibilityOrdinal;
  }

  @Override
  public int hashCode() {
    // The myVisibilityOrdinal field is intentionally not included in hash code because having two resource items
    // differing only by visibility in the same hash table is extremely unlikely.
    return HashCodes.mix(myTypeOrdinal, myName.hashCode());
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

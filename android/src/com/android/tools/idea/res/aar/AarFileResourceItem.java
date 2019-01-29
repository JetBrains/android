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
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a file resource inside an AAR, e.g. a drawable or a layout.
 */
class AarFileResourceItem extends AbstractAarResourceItem {
  @NotNull private final ResourceType myResourceType;
  @NotNull private final String myRelativePath;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param relativePath the path of the resource relative to the res folder, or path of a zip entry inside res.apk
   */
  public AarFileResourceItem(@NotNull ResourceType type,
                             @NotNull String name,
                             @NotNull AarConfiguration configuration,
                             @NotNull ResourceVisibility visibility,
                             @NotNull String relativePath) {
    super(name, configuration, visibility);
    myResourceType = type;
    myRelativePath = relativePath;
  }

  @Override
  @NotNull
  public final ResourceType getResourceType() {
    return myResourceType;
  }

  @Override
  public final boolean isFileBased() {
    return true;
  }

  @Override
  @Nullable
  public String getValue() {
    return getRepository().getResourceUrl(myRelativePath);
  }

  @Override
  @Nullable
  public final ResourceReference getReference() {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned PathString points either to a file on disk, or to a ZIP entry inside a res.apk file.
   * In the latter case the filesystem URI part points to res.apk itself, e.g. {@code "zip:///foo/bar/res.apk"}.
   * The path part is the path of the ZIP entry containing the resource.
   */
  @Override
  @NotNull
  public final PathString getSource() {
    return getRepository().getPathString(myRelativePath);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarFileResourceItem other = (AarFileResourceItem) obj;
    return myRelativePath.equals(other.myRelativePath);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), myRelativePath.hashCode());
  }
}

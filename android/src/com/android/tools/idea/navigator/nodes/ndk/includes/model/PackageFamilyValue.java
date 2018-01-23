/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk.includes.model;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValue.SortOrderKey.NDK_PACKAGING_FAMILY;
import static com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValue.SortOrderKey.OTHER_PACKAGING_FAMILY;

/**
 * A folder that represents a particular packaging family like NDK or CDep.
 * Contains a collection of packages. So for example,
 *
 * NDK <- this class
 * STL <- an NDK module
 * Native App Glue <- another module
 */
public class PackageFamilyValue extends ClassifiedIncludeValue {

  @NotNull
  public final PackageFamilyKey myKey;

  @NotNull
  public final ImmutableList<ClassifiedIncludeValue> myIncludes;

  public PackageFamilyValue(@NotNull PackageFamilyKey key, @NotNull Collection<ClassifiedIncludeValue> includes) {
    myKey = key;
    myIncludes = ImmutableList.copyOf(includes);
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", getPackageType().myDescription, getPackageFamilyBaseFolder());
  }

  @NotNull
  @Override
  public String getSortKey() {
    // NDK goes above other package families in the view.
    if (getPackageType().equals(PackageType.NdkComponent)) {
      return NDK_PACKAGING_FAMILY.myKey + toString();
    }
    return OTHER_PACKAGING_FAMILY.myKey + toString();
  }

  @NotNull
  @Override
  public PackageType getPackageType() {
    return myKey.myPackageType;
  }

  @NotNull
  @Override
  public File getPackageFamilyBaseFolder() {
    return myKey.myPackagingFamilyBaseFolder;
  }
}

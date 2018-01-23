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

import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValue.SortOrderKey.SIMPLE_INCLUDE;

/**
 * Information about a single include path.
 */
public class SimpleIncludeValue extends ClassifiedIncludeValue {

  @NotNull
  private final PackageType myPackageType;

  @NotNull
  public final String mySimplePackageName;

  @NotNull
  public final String myRelativeIncludeSubFolder;

  @NotNull
  public final File myIncludeFolder;

  @NotNull
  private final File myPackagingFamilyBaseFolder;

  public SimpleIncludeValue(@NotNull PackageType packageType,
                            @NotNull String simplePackageName,
                            @NotNull String relativeIncludeSubFolder,
                            @NotNull File includeFolder,
                            @NotNull File packagingFamilyBaseFolder) {
    myPackageType = packageType;
    mySimplePackageName = simplePackageName;
    myRelativeIncludeSubFolder = relativeIncludeSubFolder;
    myIncludeFolder = includeFolder;
    myPackagingFamilyBaseFolder = packagingFamilyBaseFolder;
  }

  @Override
  public String toString() {
    return String
      .format("%s (%s, %s, %s)", mySimplePackageName, myPackageType.myDescription, myPackagingFamilyBaseFolder, myRelativeIncludeSubFolder);
  }

  @NotNull
  @Override
  public String getSortKey() {
    return SIMPLE_INCLUDE.myKey + toString();
  }

  @NotNull
  @Override
  public PackageType getPackageType() {
    return myPackageType;
  }

  @NotNull
  @Override
  public File getPackageFamilyBaseFolder() {
    return myPackagingFamilyBaseFolder;
  }
}

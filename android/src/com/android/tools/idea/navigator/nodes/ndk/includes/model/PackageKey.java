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

import com.google.common.base.Objects;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Package grouping key. This key groups packages by package type, package name, and base folder for the package family.
 */
public class PackageKey {
  // The packaging kind. For example, NDK component.
  @NotNull
  public final PackageType myPackageType;

  // The name of the package
  @NotNull
  public final String mySimplePackageName;

  // The root folder of the packaging. For example Android NDK root folder
  @NotNull
  public final File myPackagingFamilyBaseFolder;

  public PackageKey(@NotNull PackageType packageType,
                    @NotNull String simplePackageName,
                    @NotNull File packagingFamilyBaseFolder) {
    myPackageType = packageType;
    mySimplePackageName = simplePackageName;
    myPackagingFamilyBaseFolder = packagingFamilyBaseFolder;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (obj.getClass() != getClass()) {
      return false;
    }
    PackageKey rhs = (PackageKey)obj;
    if (!FileUtil.filesEqual(rhs.myPackagingFamilyBaseFolder, myPackagingFamilyBaseFolder)) {
      return false;
    }
    if (!Objects.equal(mySimplePackageName, rhs.mySimplePackageName)) {
      return false;
    }
    if (!Objects.equal(myPackageType, rhs.myPackageType)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return (mySimplePackageName.hashCode() * 37 + FileUtil.fileHashCode(myPackagingFamilyBaseFolder)) * 37 + myPackageType.hashCode();
  }
}

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

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;

/**
 * An include path or collection of include paths categorized into a PackageType.
 */
abstract public class ClassifiedIncludeValue extends IncludeValue {

  @NotNull
  public abstract PackageType getPackageType();

  @NotNull
  public abstract String getPackageDescription();

  /**
   * The home folder for the packaging system that owns this include expression.
   * For example, this is the NDK root folder for NDK components.
   */
  @NotNull
  public abstract File getPackageFamilyBaseFolder();

  /**
   * <pre>
   * This function returns a path that is primarily meant for presentation of the folder relative
   * to the family base folder. Overall, in the project view the tree will look like this:
   *
   *   [-] app
   *       [-] cpp
   *           [-] includes (C:\Sdk\ndk-bundle)
   *               [-] NDK Components (C:\Sdk\ndk-bundle)
   *                  [+] llvm-libc++ (sources\cxx\llvm-libc++\include)
   *                  ...
   *
   * This function is what produces the value 'sources\cxx\llvm-libc++\include'. Note that it
   * converts the internal slashes which are always linux-style to Windows back-slashes.
   *
   * </pre>
   */
  @NotNull
  public String getPackagingFamilyBaseFolderNameRelativeToHome() {
    String folderRelativeToUserHome = getLocationRelativeToUserHome(getPackageFamilyBaseFolder().getPath());
    return FilenameUtils.separatorsToSystem(folderRelativeToUserHome);
  }
}

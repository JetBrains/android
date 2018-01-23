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

import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValue.SortOrderKey.PACKAGING;

/**
 * A collection of includes that represents a single logical package that has multiple include folders.
 *
 * Visually, it will be a folder structure like this in the Android Project View:
 *
 * <pre>
 *   [-] MyGame
 *       [-] Includes
 *           [-] CDep [A node where PackageType is CDepPackage]
 *               [-] protobuf [A node represented by PackageValue]
 *                   protobuf.h
 * </pre>
 */
public class PackageValue extends ClassifiedIncludeValue {

  @NotNull
  public final PackageKey myKey;

  // The list of includes that are covered.
  @NotNull
  public final ImmutableList<SimpleIncludeValue> myIncludes;

  // Relative path that is common to the complete set of includes.
  @NotNull
  private final String myCommonRelativeFolder;


  public PackageValue(@NotNull PackageKey key, @NotNull Collection<SimpleIncludeValue> simpleIncludeValues) {
    myKey = key;
    myIncludes = ImmutableList.copyOf(simpleIncludeValues);
    myCommonRelativeFolder = LexicalIncludePaths.findCommonParentFolder(
      simpleIncludeValues.stream().map(expression -> expression.myRelativeIncludeSubFolder).collect(Collectors.toList()));
  }


  @Override
  public String toString() {
    return String.format("%s (%s, %s)", myKey.mySimplePackageName, myKey.myPackageType.myDescription, getDescriptiveText());
  }

  @NotNull
  public String getDescriptiveText() {
    String commonRelativeFolderWithNoSlashes = LexicalIncludePaths.trimPathSeparators(myCommonRelativeFolder);
    if (commonRelativeFolderWithNoSlashes.isEmpty()) {
      return String.format("%s include paths", myIncludes.size());
    }
    return String.format("%s", commonRelativeFolderWithNoSlashes);
  }

  @NotNull
  public String getSimplePackageName() {
    return myKey.mySimplePackageName;
  }

  @NotNull
  @Override
  public String getSortKey() {
    return PACKAGING.myKey + toString();
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

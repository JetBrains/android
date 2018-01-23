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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Methods for rearranging IncludeExpressions into logical groupings for presentation to the user.
 */
public class IncludeValues {

  @NotNull
  private static final Comparator<IncludeValue> COMPARE_NATIVE_DEPENDENCY = Comparator.comparing(IncludeValue::getSortKey);

  /**
   * Organize a flat list of simple includes into a hierarchy of include values for presentation to the user in a tree.
   */
  @NotNull
  public static List<IncludeValue> organize(@NotNull List<SimpleIncludeValue> simpleIncludes) {

    // The first phase groups the includes into packages like "NDK CPU Features". Not every include can be categorized into packages
    // so the result is a mix of packages alongside standalone include folders. At the end, the list is sorted into the order that
    // will be presented to the user.
    List<ClassifiedIncludeValue> simplifiedPackages =
      simplifyPackageWithSingleIncludeFolder(convertGroupsToPackage(groupByPackageKey(simpleIncludes)));
    Collections.sort(simplifiedPackages, COMPARE_NATIVE_DEPENDENCY);

    // The second phase groups again. This time, packages are grouped into package families with packages nested underneath.
    // So for example, "NDK Components" is a package family and "CPU Features" is one of the packages nested beneath.
    // Again, not all include folders can be classified into packages. These folders are left at the top level for now.
    List<ClassifiedIncludeValue> simplifiedFamilies =
      simplifyPackageFamilyWithSinglePackage(convertGroupsToPackageFamily(groupByPackageFamilyKey(simplifiedPackages)));
    Collections.sort(simplifiedFamilies, COMPARE_NATIVE_DEPENDENCY);

    // Lastly, all the include folders that weren't captured in packages are now grouped into a single shadowing folder. Shadowing just
    // means that the include folders are kept in order and the earlier includes may hide later includes.
    List<IncludeValue> result = groupTopLevelIncludesIntoShadowExpression(simplifiedFamilies);
    Collections.sort(result, COMPARE_NATIVE_DEPENDENCY);
    return result;
  }

  /**
   * Group the incoming list by PackageKey.
   */
  @NotNull
  private static Map<PackageKey, List<SimpleIncludeValue>> groupByPackageKey(@NotNull List<SimpleIncludeValue> includes) {
    return includes.stream().collect(Collectors.groupingBy(IncludeValues::toPackageKey, Collectors.toList()));
  }

  /**
   * Construct a PackageValue for each group of PackageKey.
   */
  @NotNull
  private static List<PackageValue> convertGroupsToPackage(
    @NotNull Map<PackageKey, List<SimpleIncludeValue>> groupsByPackingExpressionKey) {
    return groupsByPackingExpressionKey.entrySet().stream().map(entry -> new PackageValue(entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());
  }

  /**
   * If any of the PackageValues contain just one include folder then there is no need to hold these in a logical subfolder.
   * Instead, these are simplified so they are held at the parent level instead.
   */
  @NotNull
  private static List<ClassifiedIncludeValue> simplifyPackageWithSingleIncludeFolder(List<PackageValue> includes) {
    return includes.stream().map(expression -> expression.myIncludes.size() == 1
                                               ? expression.myIncludes.get(0)
                                               : expression).collect(Collectors.toList());
  }

  /**
   * Group the incoming list by PackageFamilyKey.
   */
  @NotNull
  private static Map<PackageFamilyKey, List<ClassifiedIncludeValue>> groupByPackageFamilyKey(
    @NotNull List<ClassifiedIncludeValue> includes) {
    return includes.stream().collect(Collectors.groupingBy(IncludeValues::toPackageFamilyKey, Collectors.toList()));
  }

  /**
   * Construct a PackageFamilyValue for each group of PackageFamilyKey.
   */
  @NotNull
  private static List<PackageFamilyValue> convertGroupsToPackageFamily(
    @NotNull Map<PackageFamilyKey, List<ClassifiedIncludeValue>> groups) {
    return groups.entrySet().stream().map(entry -> new PackageFamilyValue(entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());
  }

  /**
   * If any of the PackageFamilyValues contain just one package folder then there is no need to hold these in a logical subfolder.
   * Instead, these are simplified so they are held at the parent level instead.
   */
  private static List<ClassifiedIncludeValue> simplifyPackageFamilyWithSinglePackage(List<PackageFamilyValue> families) {
    return families.stream().map(family -> family.myIncludes.size() == 1
                                           ? family.myIncludes.get(0)
                                           : family).collect(Collectors.toList());
  }

  /**
   * Get a PackageKey from a SimpleIncludeValue.
   */
  private static PackageKey toPackageKey(@NotNull SimpleIncludeValue include) {
    return new PackageKey(include.getPackageType(), include.mySimplePackageName, include.getPackageFamilyBaseFolder());
  }

  /**
   * Get a PackageFamilyKey from an AbstractClassifiedIncludeValue.
   */
  private static PackageFamilyKey toPackageFamilyKey(@NotNull ClassifiedIncludeValue include) {
    return new PackageFamilyKey(include.getPackageType(), include.getPackageFamilyBaseFolder());
  }

  /**
   * When there is an include folder that references a known kind of package, for example NDK STL, we
   * don't want to show these as plain include files since they are already showing in their individual
   * package location.
   *
   * For this reason, we compute a list of folders (in order) along with a set of folders that should
   * be excluded since they are already present elsewhere.
   */
  @NotNull
  public static List<IncludeValue> groupTopLevelIncludesIntoShadowExpression(
    @NotNull List<ClassifiedIncludeValue> includes) {
    List<IncludeValue> result = new ArrayList<>();
    List<SimpleIncludeValue> simpleIncludeValues = new ArrayList<>();
    Set<String> excludes = new HashSet<>();
    for (ClassifiedIncludeValue child : includes) {
      if (child instanceof SimpleIncludeValue) {
        simpleIncludeValues.add((SimpleIncludeValue)child);
      }
      else {
        // Add folders to the list of folders to exclude from the simple path group
        excludes.add(child.getPackageFamilyBaseFolder().getPath());
        result.add(child);
      }
    }

    // The simple folders are collapsed into a single list with shadowing.
    if (!simpleIncludeValues.isEmpty()) {
      result.add(new ShadowingIncludeValue(simpleIncludeValues, excludes));
    }
    return result;
  }
}

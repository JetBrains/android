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
package com.android.tools.idea.navigator.nodes.ndk.includes.model

import java.util.*

/**
 * Methods for rearranging IncludeExpressions into logical groupings for presentation to the user.
 */
object IncludeValues {

  private val COMPARE_NATIVE_DEPENDENCY = Comparator.comparing<IncludeValue, String> { it.sortKey }

  /**
   * Organize a flat list of simple includes into a hierarchy of include values for presentation to the user in a tree.
   */
  fun organize(simpleIncludes: List<SimpleIncludeValue>): List<IncludeValue> {

    // The first phase groups the includes into packages like "NDK CPU Features". Not every include can be categorized into packages
    // so the result is a mix of packages alongside standalone include folders. At the end, the list is sorted into the order that
    // will be presented to the user.
    val groupedByPackageKey = simpleIncludes.groupBy { PackageKey(it.packageType, it.simplePackageName, it.packageFamilyBaseFolder) }
    val convertedToPackage = groupedByPackageKey.map { (key, value) ->
      PackageValue(key, value.first().packageDescription, value)
    }
    val simplifiedPackages = convertedToPackage.map { expression ->
        if (expression.includes.size == 1)
          expression.includes[0]
        else
          expression
      }
    Collections.sort(simplifiedPackages, COMPARE_NATIVE_DEPENDENCY)

    // The second phase groups again. This time, packages are grouped into package families with packages nested underneath.
    // So for example, "NDK Components" is a package family and "CPU Features" is one of the packages nested beneath.
    // Again, not all include folders can be classified into packages. These folders are left at the top level for now.
    val groupByPackageFamilyKey = simplifiedPackages.groupBy { PackageFamilyKey(it.packageType, it.packageDescription, it.packageFamilyBaseFolder) }
    val convertGroupsToPackageFamily = groupByPackageFamilyKey.map { (key, value) -> PackageFamilyValue(key, value) }
    val simplifiedFamilies = convertGroupsToPackageFamily.map { family ->
      if (family.myIncludes.size == 1 && family.myKey.packageType.myIsCollapsibleFamily)
        family.myIncludes[0]
      else
        family
    }
    Collections.sort(simplifiedFamilies, COMPARE_NATIVE_DEPENDENCY)

    // Lastly, all the include folders that weren't captured in packages are now grouped into a single shadowing folder. Shadowing just
    // means that the include folders are kept in order and the earlier includes may hide later includes.
    val result = groupTopLevelIncludesIntoShadowExpression(simplifiedFamilies)
    Collections.sort(result, COMPARE_NATIVE_DEPENDENCY)
    return result
  }

  /**
   * When there is an include folder that references a known kind of package, for example NDK STL, we
   * don't want to show these as plain include files since they are already showing in their individual
   * package location.
   *
   * For this reason, we compute a list of folders (in order) along with a set of folders that should
   * be excluded since they are already present elsewhere.
   */
  private fun groupTopLevelIncludesIntoShadowExpression(
    includes: List<ClassifiedIncludeValue>): List<IncludeValue> {
    val result = ArrayList<IncludeValue>()
    val simpleIncludeValues = ArrayList<SimpleIncludeValue>()
    val excludes = HashSet<String>()
    for (child in includes) {
      if (child is SimpleIncludeValue) {
        simpleIncludeValues.add(child)
      }
      else {
        // Add folders to the list of folders to exclude from the simple path group
        excludes.add(child.packageFamilyBaseFolder.path)
        result.add(child)
      }
    }

    // The simple folders are collapsed into a single list with shadowing.
    if (simpleIncludeValues.isNotEmpty()) {
      result.add(ShadowingIncludeValue(simpleIncludeValues, excludes))
    }
    return result
  }
}

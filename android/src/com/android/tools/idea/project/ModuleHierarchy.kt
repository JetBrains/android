/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.navigator

import com.android.tools.idea.apk.ApkFacet
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager

fun getSubmodules(project: Project, parent: Module?): Collection<Module> {
  val moduleManager = ModuleManager.getInstance(project)
  val modules = moduleManager.modules
  val grouper = moduleManager.getModuleGrouper(null)

  fun Module.groupPath() = grouper.getModuleAsGroupPath(this) ?: grouper.getGroupPath(this)

  fun Module.groupPathIfMatchesParent(): List<String>? {
    val parentGroupPath = parent?.groupPath() ?: emptyList()
    val groupPath = groupPath()
    return groupPath.takeIf { (parent == null || groupPath.size > parentGroupPath.size) && groupPath.startsWith(parentGroupPath) }
  }

  val seenParents = mutableSetOf<List<String>>()
  var rootModuleCount = 0
  val submodules = mutableListOf<Module>()
  // 1. Create the submodules nodes
  modules.mapNotNull { module ->
    // If there is only one root project node, don't display it.
    if (module.isIgnoredRootModule()) rootModuleCount++
    module.groupPathIfMatchesParent()?.let { groupPath -> module to groupPath }
  }.sortedBy {
    // Ensures parents go before their children.
    it.second.size
  }.forEach moduleForEach@{ (module, groupPath) ->
    // Remove the root module if it is the only one. This hides the root module node when only one
    // project is present.
    if (rootModuleCount == 1 && module.isIgnoredRootModule()) return@moduleForEach
    for (size in 0 until groupPath.size) {
      if (groupPath.isNotEmpty() && seenParents.contains(groupPath.subList(0, size))) return@moduleForEach
    }
    seenParents.add(groupPath)
    submodules.add(module)
  }
  return submodules
}

private fun Module.isIgnoredRootModule() =
  ModuleRootManager.getInstance(this).sourceRoots.isEmpty() && ApkFacet.getInstance(this) == null

private fun List<String>.startsWith(prefix: List<String>): Boolean {
  if (size < prefix.size) return false
  return (0 until prefix.size).all { index -> this[index] == prefix[index] }
}

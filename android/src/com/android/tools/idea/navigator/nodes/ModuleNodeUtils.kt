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
@file:JvmName("ModuleNodeUtils")

package com.android.tools.idea.navigator.nodes

import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.util.GradleUtil.isRootModuleWithNoSources
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.android.tools.idea.navigator.nodes.android.AndroidModuleNode
import com.android.tools.idea.navigator.nodes.apk.ApkModuleNode
import com.android.tools.idea.navigator.nodes.ndk.NdkModuleNode
import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.util.ArrayList

/**
 * Creates Android project view nodes for a given [project].
 */
fun createChildModuleNodes(
  project: Project,
  parent: Module?,
  projectViewPane: AndroidProjectViewPane,
  settings: ViewSettings
): MutableList<AbstractTreeNode<*>> {
  val moduleManager = ModuleManager.getInstance(project)
  val modules = moduleManager.modules
  val children = ArrayList<AbstractTreeNode<*>>(modules.size)
  val grouper = moduleManager.getModuleGrouper(null)

  fun Module.groupPath() = grouper.getModuleAsGroupPath(this) ?: grouper.getGroupPath(this)

  fun Module.groupPathIfMatchesParent(): List<String>? {
    val parentGroupPath = parent?.groupPath() ?: emptyList()
    val groupPath = groupPath()
    return groupPath.takeIf { (parent == null || groupPath.size > parentGroupPath.size) && groupPath.startsWith(parentGroupPath) }
  }

  val modulesWithGroupPaths = modules
    .filter { !it.isIgnoredRootModule() }
    .mapNotNull { module -> module.groupPathIfMatchesParent()?.let { groupPath -> module to groupPath } }
    .sortedBy { it.second.size }  // Ensures parents go before their children.

  val seenParents = mutableSetOf<List<String>>()

  modulesWithGroupPaths.forEach moduleForEach@{ (module, groupPath) ->
    for (size in 0 until groupPath.size) {
      if (groupPath.isNotEmpty() && seenParents.contains(groupPath.subList(0, size))) return@moduleForEach
    }
    seenParents.add(groupPath)

    val apkFacet = ApkFacet.getInstance(module)
    val androidFacet = AndroidFacet.getInstance(module)
    val ndkFacet = NdkFacet.getInstance(module)
    when {
      androidFacet != null && AndroidModel.get(androidFacet) != null ->
        children.add(AndroidModuleNode(project, module, projectViewPane, settings))
      androidFacet != null && apkFacet != null -> {
        children.add(ApkModuleNode(project, module, androidFacet, apkFacet, settings))
        children.add(ExternalLibrariesNode(project, settings))
      }
      ndkFacet != null && ndkFacet.ndkModuleModel != null ->
        children.add(NdkModuleNode(project, module, projectViewPane, settings))
      else ->
        children.add(NonAndroidModuleNode(project, module, projectViewPane, settings))
    }
  }

  return children
}

private fun Module.isIgnoredRootModule() = isRootModuleWithNoSources(this) && ApkFacet.getInstance(this) == null

private fun List<String>.startsWith(prefix: List<String>): Boolean {
  if (size < prefix.size) return false
  return (0 until prefix.size).all { index -> this[index] == prefix[index] }
}

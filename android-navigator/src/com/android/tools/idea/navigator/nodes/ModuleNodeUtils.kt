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

import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Creates Android project view nodes for a given [project].
 */
fun createChildModuleNodes(
  project: Project,
  submodules: Collection<Module>,
  settings: ViewSettings
): MutableList<AbstractTreeNode<*>> {
  val providers = AndroidViewNodeProvider.getProviders()
  val children = ArrayList<AbstractTreeNode<*>>(submodules.size)
  submodules.forEach { module ->
    val nodeGroups = providers.mapNotNull { provider -> provider.getModuleNodes(module, settings) }
    children.addAll(
      if (nodeGroups.isNotEmpty()) nodeGroups.flatten()
      else listOf(NonAndroidModuleNode(project, module, settings))
    )
  }
  return children
}



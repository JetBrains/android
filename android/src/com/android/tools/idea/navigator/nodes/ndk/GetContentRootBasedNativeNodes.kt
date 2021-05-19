/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk

import com.android.tools.idea.gradle.project.facet.ndk.NativeSourceRootType
import com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludesViewNodeV2
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiManager

/** Gets nodes for project view and Android view populated by native content roots. */
fun getContentRootBasedNativeNodes(module: Module,
                                   settings: ViewSettings): Collection<AbstractTreeNode<*>> {
  val project = module.project
  val sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(NativeSourceRootType)
  val psiManager = PsiManager.getInstance(project)
  val sourceRootPsiDirs = sourceRoots.mapNotNull { sourceRoot -> psiManager.findDirectory(sourceRoot) }

  var sourceRootNodes: Collection<AbstractTreeNode<*>> = sourceRootPsiDirs.map { PsiDirectoryNode(project, it, settings) }
  if (sourceRootNodes.size == 1) {
    sourceRootNodes = sourceRootNodes.first().children
  }
  return sourceRootNodes + listOfNotNull(IncludesViewNodeV2.create(module, settings))
}


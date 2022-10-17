/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.application

/**
 * A provider of nodes representing an IDE module.
 */
interface AndroidViewNodeProvider {
  companion object {
    val EP_NAME: ExtensionPointName<AndroidViewNodeProvider> =
      ExtensionPointName.create("com.android.tools.idea.navigator.androidViewNodeProvider")

    @JvmStatic
    fun getProviders(): Collection<AndroidViewNodeProvider> {
      val extensionArea = application.extensionArea
      return if (extensionArea.hasExtensionPoint(EP_NAME)) extensionArea.getExtensionPoint(EP_NAME).extensionList else emptyList()
    }
  }

  /**
   * For a given [module], returns a collection of nodes that represent it in the Android project view or `null` if the [module]
   * is not recognised by the provider.
   */
  fun getModuleNodes(module: Module, settings: ViewSettings): List<AbstractTreeNode<*>>? = null

  /**
   * If the provider may return nodes representing files not included in the IDE project structure such files should be recognised by this
   * method.
   *
   * This is needed to support refreshing the tree and locating nodes in the tree.
   */
  fun projectContainsExternalFile(project: Project, file: VirtualFile): Boolean = false
}
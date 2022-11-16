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
package com.android.tools.idea.navigator.nodes.ndk

import com.android.tools.idea.apk.debugging.LibraryFolder
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
import com.android.tools.idea.navigator.nodes.apk.ndk.LibFolderNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

class AndroidViewNodeNdkProvider : AndroidViewNodeProvider {
  override fun projectContainsExternalFile(project: Project, file: VirtualFile): Boolean {
    // Include files may be out-of-project so check for them.
    for (module in ModuleManager.getInstance(project).modules) {
      val ndkFacet = NdkFacet.getInstance(module!!)
      val ndkModuleModel = ndkFacet?.ndkModuleModel
      if (ndkModuleModel != null) {
        return containedByNativeNodes(project, ndkModuleModel, file)
      }
    }
    return false
  }

  override fun getModuleChildren(module: Module, settings: ViewSettings): List<AbstractTreeNode<*>>? {
    val ndkModuleModel = NdkModuleModel.Companion.get(module) ?: return null
    return listOf(
      AndroidJniFolderNode(
        module.project,
        ndkModuleModel,
        settings
      )
    )
  }

  override fun moduleContainsExternalFile(module: Module, file: VirtualFile): Boolean {
    val facet = AndroidFacet.getInstance(module) ?: return false
    val ndkModuleModel = NdkModuleModel.Companion.get(facet.module)
    return ndkModuleModel?.let { containedByNativeNodes(module.project, it, file) } ?: false
  }

  override fun getApkModuleChildren(module: Module, settings: ViewSettings): List<AbstractTreeNode<*>>? {
    val found = LibraryFolder.findIn(module.project) ?: return null
    return listOf(LibFolderNode(module.project, found, settings))
  }
}
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
package com.android.tools.idea.navigator.nodes.ndk

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths
import com.android.tools.idea.ndk.ModuleVariantAbi
import com.android.tools.idea.ndk.NativeWorkspaceService
import com.android.tools.idea.ndk.NativeWorkspaceService.Companion.getInstance
import com.android.tools.idea.util.toIoFile
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

fun getNativeSourceNodes(project: Project,
                         ndkModuleModel: NdkModuleModel,
                         settings: ViewSettings): Collection<AbstractTreeNode<*>> {
  val module = ModuleManager.getInstance(project).findModuleByName(ndkModuleModel.moduleName)
               ?: return emptyList() // the module could be missing in case user changes project structure and sync
  val ndkModel = ndkModuleModel.ndkModel
  return getContentRootBasedNativeNodes(module, settings)
}

fun containedByNativeNodes(project: Project, ndkModuleModel: NdkModuleModel, file: VirtualFile): Boolean {
  // Check if the file is an additional native files that is manually added.
  val module = ModuleManager.getInstance(project).findModuleByName(ndkModuleModel.moduleName) ?: return false
  if (getInstance(project).getAdditionalNativeFiles(module).contains(file)) {
    return true
  }

  // Check if the file is a header file that's under the synthesized "Includes" node.
  if (!LexicalIncludePaths.hasHeaderExtension(file.name)) {
    // Skip directly if the file does not look like a header.
    return false
  }
  val ndkFacet = NdkFacet.getInstance(module)!!
  val selectedVariantAbi = ndkFacet.selectedVariantAbi ?: return false
  val variant = selectedVariantAbi.variant
  val abi = selectedVariantAbi.abi
  val nativeWorkspaceService = NativeWorkspaceService.getInstance(project)
  val nativeHeaderDirs = nativeWorkspaceService.getNativeHeaderDirs(ModuleVariantAbi(ndkModuleModel.moduleName, variant, abi))
  return nativeHeaderDirs.any { VfsUtil.isAncestor(it.dir, file.toIoFile(), false) }
}

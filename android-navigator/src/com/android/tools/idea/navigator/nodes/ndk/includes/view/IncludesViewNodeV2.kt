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
package com.android.tools.idea.navigator.nodes.ndk.includes.view

import com.android.tools.analytics.UsageTracker.log
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.gradle.project.facet.ndk.NativeSourceRootType
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.navigator.nodes.FolderGroupNode
import com.android.tools.idea.navigator.nodes.ndk.includes.model.ClassifiedIncludeValue
import com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValues.organize
import com.android.tools.idea.navigator.nodes.ndk.includes.model.ShadowingIncludeValue
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue
import com.android.tools.idea.navigator.nodes.ndk.includes.resolver.IncludeResolver
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.VirtualFiles
import com.android.tools.idea.ndk.ModuleVariantAbi
import com.android.tools.idea.ndk.NativeHeaderDir
import com.android.tools.idea.ndk.NativeWorkspaceService
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.toIoFile
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.CppHeadersViewEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES

/**
 * The includes node that contains all non-project native headers in a module. This is the top node that is directly under the `cpp` node.
 *
 * This node class, compared with its predecessor (now removed):
 * - is populated with data from [NativeWorkspaceService] instead of [NdkModuleModel], which gets the information from AGP
 * - is per module rather than per artifact
 */
class IncludesViewNodeV2(
  project: Project,
  nativeHeaderDirs: List<NativeHeaderDir>,
  viewSettings: ViewSettings) : ProjectViewNode<List<NativeHeaderDir>>(project, nativeHeaderDirs, viewSettings), FolderGroupNode {

  companion object {
    fun create(module: Module, settings: ViewSettings): IncludesViewNodeV2? {
      val ndkFacet = NdkFacet.getInstance(module) ?: return null
      val sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(NativeSourceRootType)
      val project = module.project
      val projectFileIndex = ProjectFileIndex.getInstance(project)
      val selectedVariantAbi = ndkFacet.selectedVariantAbi ?: return null
      val variant = selectedVariantAbi.variant
      val abi = selectedVariantAbi.abi
      val nativeWorkspaceService = NativeWorkspaceService.getInstance(project)
      val nativeHeaderDirs = nativeWorkspaceService.getNativeHeaderDirs(
        ModuleVariantAbi(module.name, variant, abi)).filter { nativeHeaderDir ->
        val nativeHeaderDirVirtualFile = VfsUtil.findFileByIoFile(nativeHeaderDir.dir, false) ?: return@filter true

        // Only show this include directory if it's not under any source folders, or if it is under a source folder but it's also excluded.
        sourceRoots.none { sourceRoot -> VfsUtil.isAncestor(sourceRoot.toIoFile(), nativeHeaderDir.dir, false) } ||
        projectFileIndex.isExcluded(nativeHeaderDirVirtualFile)
      }
      return if (nativeHeaderDirs.isEmpty()) {
        null
      }
      else {
        IncludesViewNodeV2(project, nativeHeaderDirs, settings)
      }
    }
  }

  override fun contains(file: VirtualFile): Boolean {
    if (!LexicalIncludePaths.hasHeaderExtension(file.name)) {
      // Skip directly if the file does not look like a header.
      return false
    }
    return value.any { VfsUtil.isAncestor(it.dir, file.toIoFile(), false) }
  }

  override fun update(presentation: PresentationData) {
    presentation.addText("includes", REGULAR_ATTRIBUTES)
    presentation.setIcon(AllIcons.Nodes.WebFolder)
  }

  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    val startTime = System.currentTimeMillis()
    return try {
      getChildrenImpl()
    }
    finally {
      log(AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.CPP_HEADERS_VIEW_EVENT)
            .setCppHeadersViewEvent(CppHeadersViewEvent.newBuilder()
                                      .setEventDurationMs(System.currentTimeMillis() - startTime)
                                      .setType(CppHeadersViewEvent.CppHeadersViewEventType.OPEN_TOP_INCLUDES_NODE)).withProjectId(
          project))
    }
  }

  private fun getChildrenImpl(): Collection<AbstractTreeNode<*>> {
    val result = mutableListOf<AbstractTreeNode<*>>()
    val simpleIncludes = mutableListOf<SimpleIncludeValue>()

    val includeDirs = value.map { it.dir }
    for (includeFolder in includeDirs) {
      simpleIncludes.add(IncludeResolver.getGlobalResolver(IdeSdks.getInstance().androidNdkPath).resolve(includeFolder) ?: continue)
    }

    val includes = organize(simpleIncludes)

    for (include in includes) {
      if (include is ShadowingIncludeValue) {
        val concrete = include
        result.addAll(IncludeViewNodes.getIncludeFolderNodesWithShadowing(
          concrete.includePathsInOrder, VirtualFiles.convertToVirtualFile(concrete.myExcludes), false, project!!, settings))
      }
      else if (include is SimpleIncludeValue) {
        result.add(SimpleIncludeViewNode(include, includeDirs, true, project, settings))
      }
      else if (include is ClassifiedIncludeValue) {
        // Add folders to the list of folders to exclude from the simple path group
        result.add(IncludeViewNode.createIncludeView(include, includeDirs, true, project, settings))
      }
    }
    return result
  }

  override val folders: List<PsiDirectory> get() = emptyList()

  override fun getTypeSortWeight(sortByType: Boolean): Int = -100
}
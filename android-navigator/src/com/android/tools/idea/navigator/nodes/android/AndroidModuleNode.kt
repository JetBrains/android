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
package com.android.tools.idea.navigator.nodes.android

import com.android.ide.common.util.PathString
import com.android.tools.idea.fileTypes.AndroidIconProvider
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.navigator.nodes.AndroidViewModuleNode
import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.util.toVirtualFile
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.android.facet.AndroidFacet

/**
 * [com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode] does not classify source types, and just assumes that all source
 * roots contain Java packages. This class overrides that behavior to provide a per source type node ([AndroidSourceTypeNode]) inside
 * a module.
 */
class AndroidModuleNode(
  project: Project,
  module: Module,
  settings: ViewSettings
) : AndroidViewModuleNode(project, module, settings) {
  override fun getModuleChildren(): Collection<AbstractTreeNode<*>> {
    val module = value?.takeUnless { it.isDisposed } ?: return emptyList()
    val facet = AndroidFacet.getInstance(module)
    return if (facet == null || AndroidModel.get(facet) == null) {
      platformGetChildren()
    }
    else {
      getChildren(facet, settings)
    }
  }

  override fun contains(file: VirtualFile): Boolean {
    if (super.contains(file)) {
      return true
    }

    // If there is a native-containing module then check it for externally referenced header files
    val module = value?.takeUnless { it.isDisposed } ?: return false
    return AndroidViewNodeProvider.getProviders().any { it.moduleContainsExternalFile(module, file) }
  }

  override fun getSortKey(): Comparable<*>? = value?.takeUnless { it.isDisposed }?.name
  override fun getTypeSortKey(): Comparable<*>? = sortKey

  override fun toTestString(printInfo: Queryable.PrintInfo?): String {
    val module = value
    return if (module == null || module.isDisposed) {
      if (module == null) "(null)" else "(Disposed)"
    }
    else String.format("%1\$s (Android)", super.toTestString(printInfo))
  }

  override fun update(presentation: PresentationData) {
    super.update(presentation)
    val module = value
    if (module == null || module.isDisposed) {
      return
    }
    // Use Android Studio Icons if module is available. If module was disposed, super.update will set the value of this node to null.
    // This can happen when a module was just deleted, see b/67838273.
    presentation.setIcon(AndroidIconProvider.getModuleIcon(module))
  }

  /**
   * This node represents:
   * - module represented by this node
   * - all virtual files that belong to this module or its linked modules that are not contained by its children
   *
   * TODO (http://b/249099672): This should be expanded to handle more cases.
   */
  override fun canRepresent(element: Any?): Boolean {
    if (super.canRepresent(element)) return true

    val file = when (element) {
      is VirtualFile -> element
      is PsiElement -> PsiUtilCore.getVirtualFile(element)
      else -> null
    } ?: return false

    val project = project.takeUnless { it == null || it.isDisposed } ?: return false
    val moduleForFile = ProjectFileIndex.getInstance(project).getModuleForFile(file, false)
    if (value != moduleForFile?.getHolderModule()) return false

    val childrenContainFile = moduleChildren.any {
      it !is ProjectViewNode || it.contains(file)
    }

    return !childrenContainFile
  }

  companion object {
    fun getChildren(
      facet: AndroidFacet,
      settings: ViewSettings
    ): Collection<AbstractTreeNode<*>> {
      val result: MutableList<AbstractTreeNode<*>> = ArrayList()
      result.addAll(AndroidViewNodeProvider.getProviders().mapNotNull { it.getModuleChildren(facet.holderModule, settings) }.flatten())
      return result
    }

    fun getPsiDirectory(project: Project, path: PathString?): PsiDirectory? {
      val virtualFile = path.toVirtualFile() ?: return null
      return PsiManager.getInstance(project).findDirectory(virtualFile)
    }
  }
}
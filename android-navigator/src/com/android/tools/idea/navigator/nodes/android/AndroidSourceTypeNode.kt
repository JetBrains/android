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

import com.android.tools.idea.navigator.AndroidViewNodes
import com.android.tools.idea.navigator.nodes.FolderGroupNode
import com.android.tools.idea.navigator.nodes.GroupNodes
import com.android.tools.idea.projectsystem.findSourceRoot
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidSourceType
import java.util.Objects

/**
 * [AndroidSourceTypeNode] is a virtual node in the package view of an Android module under which all sources
 * corresponding to a particular [AndroidSourceType] are grouped together.
 */
open class AndroidSourceTypeNode internal constructor(
  project: Project,
  androidFacet: AndroidFacet,
  settings: ViewSettings,
  private val sourceType: AndroidSourceType,
  private val sourceRoots: Set<VirtualFile>
) : ProjectViewNode<AndroidFacet?>(project, androidFacet, settings), FolderGroupNode {
  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    val projectViewDirectoryHelper = ProjectViewDirectoryHelper.getInstance(myProject)
    return sourceFolders.flatMap { directory ->
      annotateWithSourceProvider(projectViewDirectoryHelper.getDirectoryChildren(directory, settings, true))
    }
  }

  private fun annotateWithSourceProvider(folderChildren: Collection<AbstractTreeNode<*>>): Collection<AbstractTreeNode<*>> {
    return folderChildren.map { child ->
      when (child) {
        is PsiDirectoryNode -> {
          val folder = child.value!!
          val (first, file) = findSourceProvider(folder.virtualFile)
          val psiDir = if (file == null) null else PsiManager.getInstance(myProject).findDirectory(file)
          AndroidPsiDirectoryNode(myProject, folder, settings, first, psiDir)
        }

        is PsiFileNode -> {
          val file = child.value!!
          val virtualFile = file.virtualFile
          val (first) = findSourceProvider(virtualFile)
          AndroidPsiFileNode(myProject, file, settings, first)
        }

        else -> child
      }
    }
  }

  protected fun findSourceProvider(virtualFile: VirtualFile): Pair<String?, VirtualFile?> {
    val androidFacet = value!!
    return AndroidViewNodes.getSourceProviders(androidFacet)
      .firstNotNullOfOrNull { provider ->
        val root = provider.findSourceRoot(virtualFile) ?: return@firstNotNullOfOrNull null
        provider.name to root
      }
      ?: (null to null)
  }

  protected val sourceFolders: List<PsiDirectory>
    get() {
      val psiManager = PsiManager.getInstance(myProject)
      return sourceRoots.asSequence().filter { it.isValid }.mapNotNull { psiManager.findDirectory(it) }.toList()
    }

  override fun update(presentation: PresentationData) {
    presentation.addText(sourceType.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    if (sourceType.isGenerated) {
      presentation.addText(GENERATED_SUFFIX, SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
    val icon = sourceType.icon
    if (icon != null) {
      presentation.setIcon(icon)
    }
    presentation.presentableText = toTestString(null)
  }

  override fun toTestString(printInfo: Queryable.PrintInfo?): String? {
    return if (sourceType.isGenerated) sourceType.name + GENERATED_SUFFIX else sourceType.name
  }

  override fun contains(file: VirtualFile): Boolean {
    //TODO: first check if the file is of my source type
    return sourceRoots.any {root -> VfsUtilCore.isAncestor(root, file, false)}
  }

  override fun canRepresent(element: Any): Boolean {
    return GroupNodes.canRepresent(this, element)
  }

  override fun getSortKey(): Comparable<*>? = sourceType

  override fun getTypeSortKey(): Comparable<*>? = sourceType

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    if (!super.equals(other)) {
      return false
    }
    val that = other as AndroidSourceTypeNode
    return (sourceType === that.sourceType) && (sourceRoots == that.sourceRoots)
  }

  override fun hashCode(): Int {
    return Objects.hash(super.hashCode(), sourceType, sourceRoots)
  }

  override val folders: List<PsiDirectory>
    get() = sourceFolders

  companion object {
    private const val GENERATED_SUFFIX = " (generated)"
  }
}
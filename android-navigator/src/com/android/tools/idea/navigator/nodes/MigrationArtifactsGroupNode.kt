/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/** A group node in the Android Project View that aggregates migration-related artifacts. */
class MigrationArtifactsGroupNode(project: Project, settings: ViewSettings, private val migrationDir: VirtualFile) :
  ProjectViewNode<Project>(project, project, settings) {

  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    val psiManager = PsiManager.getInstance(myProject)
    val rootPsiDir = psiManager.findDirectory(migrationDir) ?: return emptyList()
    var currentChildren = PsiDirectoryNode(myProject, rootPsiDir, settings).children

    // If there is only one children directory, let's not display it.
    // This simplifies the case where for example we only have an import migration running.
    while (currentChildren.size == 1) {
      val onlyChild = currentChildren.first()
      if (onlyChild is PsiDirectoryNode) {
        currentChildren = onlyChild.children
      } else {
        break
      }
    }
    return currentChildren
  }

  override fun update(presentation: PresentationData) {
    presentation.presentableText = "Migration Artifacts"
    presentation.setIcon(AllIcons.Nodes.Folder)
  }

  override fun contains(file: VirtualFile): Boolean {
    return VfsUtilCore.isAncestor(migrationDir, file, false)
  }

  override fun getWeight(): Int = 110

  companion object {
    @JvmStatic
    fun createIfAvailable(project: Project, settings: ViewSettings): MigrationArtifactsGroupNode? {
      if (!StudioFlags.IMPORT_PROJECT_ENABLED.get()) return null
      val migrationDir = project.guessProjectDir()?.findChild(".migration") ?: return null
      return MigrationArtifactsGroupNode(project, settings, migrationDir)
    }
  }
}

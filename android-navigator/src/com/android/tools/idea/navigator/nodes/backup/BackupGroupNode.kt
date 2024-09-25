/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.backup

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.collections.immutable.toImmutableList
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * A group node in the Android Project View that contains all the backup files in the project
 */
internal class BackupGroupNode(project: Project, settings: ViewSettings)
  : ProjectViewNode<String>(project, "/", settings), DirectoryNode {
  private val children = mutableListOf<AbstractTreeNode<*>>()

  override fun addChild(node: AbstractTreeNode<*>) {
    children.add(node)
  }

  override fun update(presentation: PresentationData) {
    presentation.presentableText = "Backup Files"
    presentation.setIcon(AllIcons.Actions.Upload)
  }

  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    val dirs = mutableMapOf<String, DirectoryNode>()
    dirs.clear()
    children.clear()
    ProjectFileIndex.getInstance(project).iterateContent(
      {
        val parent = it.parent.path.removePrefix(project.basePath ?: "")
        dirs.getOrCreateDirectoryNode(parent).addChild(BackupFileNode(project, it, settings))
        true
      }, { it.extension == "backup" })

    return children.toImmutableList()
  }

  override fun contains(file: VirtualFile): Boolean {
    return true
  }

  override fun canRepresent(element: Any): Boolean {
    val file = element as? VirtualFile ?: return false
    if (!file.isDirectory) {
      return false
    }
    val index = ProjectFileIndex.getInstance(project)
    return when {
      !index.isInProjectOrExcluded(file) -> false
      !index.isInContent(file) -> false
      index.isInSource(file) -> false
      index.isInLibrary(file) -> false
      else -> true
    }
  }

  private fun MutableMap<String, DirectoryNode>.getOrCreateDirectoryNode(pathString: String): DirectoryNode {
    if (pathString.isEmpty() || pathString == File.separator) {
      return this@BackupGroupNode
    }
    return getOrPut(pathString) {
      val path = Path.of(pathString)
      val dir = BackupDirectoryNode(project, path.name, settings)
      getOrCreateDirectoryNode(path.parent.pathString).addChild(dir)
      dir
    }
  }
}

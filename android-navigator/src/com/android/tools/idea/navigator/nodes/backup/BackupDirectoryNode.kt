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
import com.intellij.ide.projectView.NodeSortOrder
import com.intellij.ide.projectView.NodeSortSettings
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.collections.immutable.toImmutableList

/**
 * A group node in the Android Project View for a directory that contains back files
 */
internal open class BackupDirectoryNode internal constructor(
  project: Project,
  private val dirName: String,
  settings: ViewSettings
) : ProjectViewNode<String>(project, dirName, settings), DirectoryNode {

  private val children = mutableListOf<AbstractTreeNode<*>>()

  override fun addChild(node: AbstractTreeNode<*>) {
    children.add(node)
  }

  override fun contains(file: VirtualFile): Boolean {
    return true
  }

  @Suppress("UnstableApiUsage")
  override fun getSortOrder(settings: NodeSortSettings) = NodeSortOrder.FOLDER

  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    return children.toImmutableList()
  }

  override fun canRepresent(element: Any) = false

  override fun update(presentation: PresentationData) {
    presentation.presentableText = dirName
    presentation.setIcon(AllIcons.Nodes.Folder)
  }
}
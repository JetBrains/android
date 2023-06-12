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

import com.android.resources.ResourceFolderType
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidSourceType

class AndroidResFolderNode internal constructor(
  project: Project,
  androidFacet: AndroidFacet,
  sourceType: AndroidSourceType,
  settings: ViewSettings,
  sourceRoots: Set<VirtualFile>
) : AndroidSourceTypeNode(project, androidFacet, settings, sourceType, sourceRoots) {
  /**
   * Returns the children of the res folder. Rather than showing the existing directory hierarchy, this merges together all the folders by
   * their [ResourceFolderType].
   */
  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    val children = mutableListOf<AbstractTreeNode<*>>()

    val foldersByResourceType = sourceFolders.asSequence()
      .flatMap { it.subdirectories.asSequence() }    // collect all res folders from all source providers
      .mapNotNull {
        (ResourceFolderType.getFolderType(it.name) ?: return@mapNotNull null) to it
      }
      .groupBy({ it.first }, { it.second })

    foldersByResourceType.entries
      .map {(type, folders) ->
        AndroidResFolderTypeNode(
          myProject, androidFacet, ArrayList(folders), settings,
          type
        )
      }.forEach(children::add)

    val resourcesPropertiesFile = sourceFolders.find { it.parentDirectory?.name == "main" }?.files?.find { it.name == "resources.properties"}
    if (resourcesPropertiesFile != null) {
      val (first, _) = findSourceProvider(resourcesPropertiesFile.virtualFile)
      children.add(AndroidPsiFileNode(myProject, resourcesPropertiesFile, settings, first))
    }

    return children
  }

  private val androidFacet: AndroidFacet
    get() = value!!
}
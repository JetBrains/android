/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

fun getLibraryAdditionalArtifactPaths(project: Project, pathType: LibraryPathType): List<String> {
  val projectDataManager = ProjectDataManager.getInstance()
  val externalProjectsData = projectDataManager.getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
  val result = mutableListOf<String>()

  for (externalProjectData in externalProjectsData) {
    externalProjectData.externalProjectStructure?.let { externalProjectStructure ->
      val libraryDataNodes = findLibraryDataNodes(externalProjectStructure)
      libraryDataNodes.forEach { libraryDataNode ->
        libraryDataNode.data.getPaths(pathType).let { sourcePaths ->
          result.addAll(sourcePaths)
        }
      }
    }
  }
  return result
}

private fun findLibraryDataNodes(dataNode: DataNode<*>): List<DataNode<LibraryData>> {
  val result = mutableListOf<DataNode<LibraryData>>()
  findLibraryDataNodesRecursive(dataNode, result)
  return result
}

private fun findLibraryDataNodesRecursive(dataNode: DataNode<*>, result: MutableList<DataNode<LibraryData>>) {
  if (dataNode.key == ProjectKeys.LIBRARY && dataNode.data is LibraryData) {
    @Suppress("UNCHECKED_CAST")
    result.add(dataNode as DataNode<LibraryData>)
  }
  dataNode.children.forEach { findLibraryDataNodesRecursive(it, result) }
}
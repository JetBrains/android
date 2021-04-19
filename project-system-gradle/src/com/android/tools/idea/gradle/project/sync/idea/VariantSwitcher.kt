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
@file:JvmName("VariantSwitcher")
package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAllRecursively
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * This is a temp method that will be used by the current [BuildVariantUpdater] if at all possible
 * please use the other overload.
 */
fun switchVariant(project: Project, facets: List<AndroidFacet>) : Boolean {
  if (facets.isEmpty()) return true

  val linkedProjectPath = ExternalSystemModulePropertyManager.getInstance(facets.first().module).getRootProjectPath() ?: return false
  val models = facets.mapNotNull { facet -> AndroidModuleModel.get(facet) }

  val projectData = ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, linkedProjectPath)
                    ?: return false
  val projectDataNode = projectData.externalProjectStructure as? DataNode<ProjectData> ?: return false

  return switchVariant(project, models, projectDataNode)
}

/**
 * Sets up the IDE to containing the dependency information in the selected variants for modules of all provided
 * [AndroidModuleModel].
 * This method assumes that the new variant name has been already set on the [AndroidModuleModel]s.
 *
 * If we have the variant information this method re-configures the project to use this new information by adjusting the
 * [DataNode] tree to use the new variant information. If we don't we just trigger a new refresh project syncing the new
 * variant.
 *
 */
fun switchVariant(
  project: Project,
  androidModuleModels: List<AndroidModuleModel>,
  projectDataNode: DataNode<ProjectData>
): Boolean {
  if (androidModuleModels.isEmpty()) return true
  clearCurrentVariantDataNodes(androidModuleModels, projectDataNode)
  setupDataNodesForSelectedVariant(project, androidModuleModels, projectDataNode)
  ProjectDataManager.getInstance().importData(projectDataNode, project, false)
  return true
}

private fun clearCurrentVariantDataNodes(
  androidModuleModels: List<AndroidModuleModel>,
  projectDataNode: DataNode<ProjectData>
) {
  val moduleNodes = findAll(projectDataNode, ProjectKeys.MODULE)

  // Clear all project level libraries, these will be reset again during import.
  findAllRecursively(projectDataNode, ProjectKeys.LIBRARY).forEach { node ->
    node.clear(true)
  }

  // First we need to check if the variant is already present in the AndroidModuleModel.
  // Note: The AndroidModuleModel is the same object that is attached to the DataNode tree.
  androidModuleModels.forEach { androidModuleModel ->
    val moduleNode = moduleNodes.firstOrNull { node ->
      node.data.internalName == androidModuleModel.moduleName
    } ?: return@forEach

    // If the Variant does exist then we need to get it and remove all nodes from the data tree that were setup using the old variant.
    findAllRecursively(moduleNode, ProjectKeys.LIBRARY_DEPENDENCY).forEach { node ->
      node.clear(true)
    }
    findAllRecursively(moduleNode, ProjectKeys.LIBRARY).forEach { node ->
      node.clear(true)
    }
    findAllRecursively(moduleNode, ProjectKeys.MODULE_DEPENDENCY).forEach { node ->
      node.clear(true)
    }
    findAllRecursively(moduleNode, ProjectKeys.CONTENT_ROOT).forEach { node ->
      node.clear(true)
    }
  }
}

/**
 * Set up data nodes that are normally created by the project resolver when processing [AndroidModuleModel]s.
 */
fun setupDataNodesForSelectedVariant(
  project: Project,
  androidModuleModels: List<AndroidModuleModel>,
  projectDataNode: DataNode<ProjectData>
) {
  val moduleNodes = findAll(projectDataNode, ProjectKeys.MODULE)
  val moduleIdToDataMap = createModuleIdToModuleDataMap(moduleNodes)
  androidModuleModels.forEach { androidModuleModel ->
    val newVariant = androidModuleModel.selectedVariant

    val moduleNode = moduleNodes.firstOrNull { node ->
      node.data.internalName == androidModuleModel.moduleName
    } ?: return@forEach

    // Now we need to recreate these nodes using the information from the new variant.
    moduleNode.setupCompilerOutputPaths(newVariant)
    // Then patch in any Kapt generated sources that we need
    val kaptModel = moduleNode.getUserData(AndroidGradleProjectResolver.KAPT_GRADLE_MODEL_KEY)
    AndroidGradleProjectResolver.patchMissingKaptInformationOntoModelAndDataNode(androidModuleModel, moduleNode, kaptModel)
    val libraryFilePaths = LibraryFilePaths.getInstance(project)
    moduleNode.setupAndroidDependenciesForModule({ id: String -> moduleIdToDataMap[id] }, { id, path ->
      AdditionalArtifactsPaths(
        libraryFilePaths.findSourceJarPath(id, path),
        libraryFilePaths.findJavadocJarPath(id, path),
        libraryFilePaths.findSampleSourcesJarPath(id, path)
      )
    }, newVariant)
    moduleNode.setupAndroidContentEntries(newVariant)
  }
}

private fun createModuleIdToModuleDataMap(moduleNodes: Collection<DataNode<ModuleData>>): Map<String, ModuleData> {
  return moduleNodes.map { moduleDataNode -> moduleDataNode.data }.associateBy { moduleData ->
    moduleData.id
  }
}

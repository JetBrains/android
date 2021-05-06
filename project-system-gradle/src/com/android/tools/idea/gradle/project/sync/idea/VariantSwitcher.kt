/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.ApplyAbiSelectionMode.OVERRIDE_ONLY
import com.android.tools.idea.gradle.project.sync.VariantDetails
import com.android.tools.idea.gradle.project.sync.VariantSelectionChange
import com.android.tools.idea.gradle.project.sync.applyChange
import com.android.tools.idea.gradle.project.sync.getSelectedVariantDetails
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData
import java.util.ArrayDeque

class VariantProjectDataNodes {
  var data: MutableList<DataNode<ProjectData>> = mutableListOf()

  companion object {
    /**
     * Collects [ProjectData] nodes describing the currently selected and nay previously cached build variants.
     */
    fun collectCurrentAndPreviouslyCachedVariants(projectDataNode: DataNode<ProjectData>): VariantProjectDataNodes {
      return VariantProjectDataNodes().apply {
        val projectDataNodeCopy = DataNode.nodeCopy(projectDataNode)
        for (child in projectDataNode.children) {
          if (child.key == AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS) continue
          projectDataNodeCopy.addChild(child)
        }
        data.add(projectDataNodeCopy)
        val cachedVariants = ExternalSystemApiUtil.find(projectDataNode,
                                                        AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS)
        if (cachedVariants != null) {
          // When resolving conflicts in large projects many variant combinations may get accumulated. Prevent accumulating more than 10.
          data.addAll(cachedVariants.data.data.take(9))
        }
      }
    }
  }
}

fun computeExpectedVariantsAfterSwitch(
  module: Module,
  variantNameAndAbi: VariantAndAbi,
  data: ExternalProjectInfo?
): Map<String, VariantAndAbi>? {
  val projectDataDataNode = data?.externalProjectStructure ?: return null
  val cachedVariants =
    ExternalSystemApiUtil.find(projectDataDataNode, AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS)?.data
    ?: return null

  val variantChange: VariantSelectionChange = createVariantSelectionChange(module, variantNameAndAbi, cachedVariants) ?: return null

  val androidModules = projectDataDataNode.getAndroidModules()

  val affectedModules = androidModules.getAffectedModuleIds(ExternalSystemApiUtil.getExternalProjectId(module) ?: return null)

  val sourceVariants =
    androidModules
      .modulesById
      .mapNotNull { nameAndVariantDetails(it.value.module) }
      .toMap()

  // Both `affectedModules` and `expectedVariants` in some edge cases may differ from variants resolved by Gradle/AGP. If it happens,
  // switching will unfortunately fall back to Gradle sync as it won't find the expected set of variants cached.
  return sourceVariants
    .mapValues { (moduleId, value) ->
      if (affectedModules.contains(moduleId)) value.applyChange(variantChange, applyAbiMode = OVERRIDE_ONLY)
      else value
    }
    .mapValues { (_, value) -> VariantAndAbi(value.name, value.abi) }
}

fun Project.getSelectedVariantAndAbis(): Map<String, VariantAndAbi> {
  return ProjectFacetManager.getInstance(this)
    .getFacets(AndroidFacet.ID)
    .mapNotNull { androidFacet ->
      val module = androidFacet.module
      val ndkFacet = NdkFacet.getInstance(module)
      (ExternalSystemApiUtil.getExternalProjectId(module) ?: return@mapNotNull null) to
        VariantAndAbi(
          androidFacet.properties.SELECTED_BUILD_VARIANT,
          // NOTE: Do not use `ndkFacet?.selectedVariantAbi` whis is too smart and assumes NdkModuleModel is already attached.
          ndkFacet?.configuration?.selectedVariantAbi?.abi
        )
    }
    .toMap()
}

fun ExternalProjectInfo.findAndSetupSelectedCachedVariantData(variants: Map<String, VariantAndAbi>): DataNode<ProjectData>? {
  val projectDataDataNode = externalProjectStructure ?: return null
  if (projectDataDataNode.getSelectedVariants() == variants) return projectDataDataNode

  val cachedVariants = VariantProjectDataNodes.collectCurrentAndPreviouslyCachedVariants(projectDataDataNode)

  if (!projectDataDataNode.repopulateProjectDataWith(cachedVariants, variants)) return null
  return projectDataDataNode
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
  projectDataNode: DataNode<ProjectData>
): Boolean {
  ProjectDataManager.getInstance().importData(projectDataNode, project, true)
  return true
}

private fun DataNode<ProjectData>.repopulateProjectDataWith(
  from: VariantProjectDataNodes,
  variants: Map<String, VariantAndAbi>
): Boolean {
  val selectedVariantIndex = from.data.indexOfFirst { it.getSelectedVariants() == variants }
  if (selectedVariantIndex == -1) return false
  val selectedVariant = from.data[selectedVariantIndex]
  from.data.removeAt(selectedVariantIndex)

  clear(true)
  for (dataNode in selectedVariant.children) {
    addChild(dataNode)
  }
  createChild(AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, from)
  return true
}

private fun nameAndVariantDetails(moduleDataNode: DataNode<ModuleData>): Pair<String, VariantDetails>? {
  val androidModuleModel = ExternalSystemApiUtil.find(moduleDataNode, AndroidProjectKeys.ANDROID_MODEL)?.data
                           ?: return null
  val ndkModuleModel = ExternalSystemApiUtil.find(moduleDataNode, AndroidProjectKeys.NDK_MODEL)?.data
  return moduleDataNode.data.id to (getSelectedVariantDetails(androidModuleModel, ndkModuleModel) ?: return null)
}

private fun variantAndAbi(moduleDataNode: DataNode<ModuleData>): Pair<String, VariantAndAbi>? {
  val androidModuleModel = ExternalSystemApiUtil.find(moduleDataNode, AndroidProjectKeys.ANDROID_MODEL)?.data
                           ?: return null
  val ndkModuleModel = ExternalSystemApiUtil.find(moduleDataNode, AndroidProjectKeys.NDK_MODEL)?.data
  return moduleDataNode.data.id to (VariantAndAbi(androidModuleModel.selectedVariantName, ndkModuleModel?.selectedAbi))
}

private class AndroidModule(
  val moduleId: String,
  val module: DataNode<ModuleData>,
  val androidModel: AndroidModuleModel
)

private class AndroidModules(
  val modulesById: Map<String, AndroidModule>,
  val projectData: ProjectData,
  val compositeData: CompositeBuildData?
)

/**
 * Returns the set of modules (moduleId's) which are assumed to be affected by build variant selection change when applied to the module
 * identified by a `moduleId`. It includes any modules the target module depends directly or indirectly on and also any of their feature
 * modules.
 */
private fun AndroidModules.getAffectedModuleIds(moduleId: String): Set<String> {
  return sequence {
    val queue = ArrayDeque(listOfNotNull(modulesById[moduleId]))
    val seen = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
      val head = queue.pop()
      if (seen.add(head.moduleId)) {
        yield(head.moduleId)
        queue.addAll(
          head.androidModel.selectedVariant
            .let {
              it.mainArtifact.level2Dependencies.moduleDependencies +
              it.androidTestArtifact?.level2Dependencies?.moduleDependencies.orEmpty()
            }
            .mapNotNull { dependency -> modulesById[computeModuleIdForLibraryTarget(dependency, projectData, compositeData)] }
        )
        queue.addAll(
          head.androidModel.androidProject.dynamicFeatures
            // TODO: Fix support for dynamic features in included builds.
            .mapNotNull { dynamicFeatureId -> modulesById[dynamicFeatureId] }
        )
      }
    }
  }
    .toSet()
}

private fun DataNode<ProjectData>.getAndroidModules(): AndroidModules {
  return AndroidModules(
    findAll(this, ProjectKeys.MODULE)
      .mapNotNull {
        val androidModel = ExternalSystemApiUtil.find(it, AndroidProjectKeys.ANDROID_MODEL)?.data ?: return@mapNotNull null
        AndroidModule(it.data.id, it, androidModel)
      }
      .associateBy { it.moduleId },
    data,
    ExternalSystemApiUtil.find(this, CompositeBuildData.KEY)?.data
  )
}

private fun VariantProjectDataNodes.findCachedVariantData(moduleVariants: Map<String, VariantAndAbi>): DataNode<ProjectData>? {
  for (projectDataNode in data) {
    val targetVariants = projectDataNode.getSelectedVariants()

    if (moduleVariants == targetVariants) {
      return projectDataNode
    }
  }
  return null
}

@VisibleForTesting
fun DataNode<ProjectData>.getSelectedVariants(): Map<String, VariantAndAbi> {
  return findAll(this, ProjectKeys.MODULE)
    .mapNotNull(::variantAndAbi)
    .toMap()
}

private fun createVariantSelectionChange(
  updatedModule: Module,
  targetVariant: VariantAndAbi,
  cachedVariants: VariantProjectDataNodes,
): VariantSelectionChange? {
  val sourceAndroidModuleModel = AndroidModuleModel.get(updatedModule) ?: return null
  val sourceNdkModuleModel = NdkModuleModel.get(updatedModule)
  // Find any cached variant with the [updatedModule] configured for [targetVariantName] and build the diff if found. We only need it to
  // deconstruct the target variant name without guessing.
  for (projectDataNode in cachedVariants.data) {
    val moduleDataDataNode = findAll(projectDataNode, ProjectKeys.MODULE)
                               .firstOrNull { it: DataNode<ModuleData> -> it.data.internalName == updatedModule.name }
                             ?: continue
    val androidModelDataNode = ExternalSystemApiUtil.find(moduleDataDataNode, AndroidProjectKeys.ANDROID_MODEL)
                               ?: continue
    val ndkModelDataNode = ExternalSystemApiUtil.find(moduleDataDataNode, AndroidProjectKeys.NDK_MODEL)
    val targetAndroidModuleModel = androidModelDataNode.data
    val targetNdkModuleModel = ndkModelDataNode?.data
    if (targetAndroidModuleModel.selectedVariantName == targetVariant.variant && targetNdkModuleModel?.selectedAbi == targetVariant.abi) {
      val fromVariant = getSelectedVariantDetails(sourceAndroidModuleModel, sourceNdkModuleModel) ?: return null
      return VariantSelectionChange.extractVariantSelectionChange(
        getSelectedVariantDetails(targetAndroidModuleModel, targetNdkModuleModel) ?: return null,
        fromVariant
      )
    }
  }
  return null
}

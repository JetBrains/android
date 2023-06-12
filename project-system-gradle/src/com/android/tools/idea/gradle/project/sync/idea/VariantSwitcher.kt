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

import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.model.GradleAndroidModelDataImpl
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.SwitchVariantRequest
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.projectsystem.gradle.sync.createLibraryResolverFor
import com.android.tools.idea.projectsystem.gradle.toHolder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAllRecursively
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting

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
        val cachedVariants = ExternalSystemApiUtil.find(
          projectDataNode,
          AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS
        )
        if (cachedVariants != null) {
          // When resolving conflicts in large projects many variant combinations may get accumulated. Prevent accumulating more than 10.
          data.addAll(cachedVariants.data.data.take(9))
        }
      }
    }
  }
}

fun findVariantProjectData(
  module: Module,
  variantNameAndAbi: SwitchVariantRequest,
  data: ExternalProjectInfo?
): DataNode<ProjectData>? {
  val projectDataDataNode = data?.externalProjectStructure ?: return null
  val cachedVariants =
    ExternalSystemApiUtil.find(projectDataDataNode, AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS)?.data
      ?: return null

  return cachedVariants.findVariantProjectData(module, variantNameAndAbi)
}

fun Project.getSelectedVariantAndAbis(): Map<GradleProjectPath, VariantAndAbi> {
  return getAndroidFacets()
    .mapNotNull { androidFacet ->
      val module = androidFacet.module
      val gradleProjectPath = module.getGradleProjectPath() ?: return@mapNotNull null
      gradleProjectPath to androidFacet.getVariantAndAbi()
    }.toMap()
}

fun AndroidFacet.getVariantAndAbi(): VariantAndAbi {
  val ndkFacet = NdkFacet.getInstance(holderModule)
  val variantAndAbi = VariantAndAbi(
    properties.SELECTED_BUILD_VARIANT,
    // NOTE: Do not use `ndkFacet?.selectedVariantAbi` which assumes NdkModuleModel is already attached.
    ndkFacet?.configuration?.selectedVariantAbi?.abi
  )
  return variantAndAbi
}

fun ExternalProjectInfo.findAndSetupSelectedCachedVariantData(variants: Map<GradleProjectPath, VariantAndAbi>): DataNode<ProjectData>? {
  val projectDataDataNode = externalProjectStructure ?: return null
  if (projectDataDataNode.getSelectedVariants() == variants) return projectDataDataNode

  val cachedVariants = VariantProjectDataNodes.collectCurrentAndPreviouslyCachedVariants(projectDataDataNode)

  val variantData = cachedVariants.data.firstOrNull { it.getSelectedVariants() == variants } ?: return null
  if (!projectDataDataNode.repopulateProjectDataWith(cachedVariants, variantData)) return null
  return projectDataDataNode
}

fun ExternalProjectInfo.findAndSetupSelectedCachedVariantData(variantData: DataNode<ProjectData>): DataNode<ProjectData>? {
  val projectDataDataNode = externalProjectStructure ?: return null
  if (projectDataDataNode === variantData) return projectDataDataNode

  val cachedVariants = VariantProjectDataNodes.collectCurrentAndPreviouslyCachedVariants(projectDataDataNode)

  if (!projectDataDataNode.repopulateProjectDataWith(cachedVariants, variantData)) return null
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
  ProjectDataManager.getInstance().importData(projectDataNode, project)
  return true
}

private fun DataNode<ProjectData>.repopulateProjectDataWith(
  from: VariantProjectDataNodes,
  variantData: DataNode<ProjectData>
): Boolean {
  val selectedVariantIndex = from.data.indexOfFirst { it === variantData }
  if (selectedVariantIndex == -1) error("Index of variant project data not found")
  val selectedVariant = from.data[selectedVariantIndex]
  from.data.removeAt(selectedVariantIndex)

  clear(true)
  for (dataNode in selectedVariant.children) {
    addChild(dataNode)
  }
  createChild(AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, from)
  return true
}

private fun variantAndAbi(moduleDataNode: DataNode<out ModuleData>): VariantAndAbi? {
  val androidModuleModel = GradleAndroidModelDataImpl.findFromModuleDataNode(moduleDataNode) ?: return null
  val ndkModuleModel = ExternalSystemApiUtil.find(moduleDataNode, AndroidProjectKeys.NDK_MODEL)?.data
  return VariantAndAbi(androidModuleModel.selectedVariantName, ndkModuleModel?.selectedAbi)
}

private class AndroidModule(
  val gradleProjectPath: GradleHolderProjectPath,
  val module: DataNode<out ModuleData>,
  val androidModel: GradleAndroidModelData
)

private class AndroidModules(
  val modulesByGradleProjectPath: Map<GradleHolderProjectPath, AndroidModule>,
  val projectData: DataNode<ProjectData>
)

private fun DataNode<ProjectData>.getAndroidModules(): AndroidModules {
  val holderModuleNodes = findAllRecursively(this, ProjectKeys.MODULE)
  val roots = holderModuleNodes.filter { !it.data.id.contains(':') }.associateBy { it.data.id }

  return AndroidModules(
    holderModuleNodes.mapNotNull { node ->
      val androidModel = GradleAndroidModelDataImpl.findFromModuleDataNode(node) ?: return@mapNotNull null
      val moduleId = node.data.id
      // Note: The root project name extracted below does not necessarily match the name of any Gradle projects or included builds.
      // However, it is expected to be always the same for all modules derived from one `IdeaProject` model instance.
      val rootProjectName = moduleId.substringBefore(':', moduleId)
      val projectPath = ":" + moduleId.substringAfter(':', "")
      AndroidModule(
        gradleProjectPath = GradleHolderProjectPath(
          (if (rootProjectName == "") this.data.linkedExternalProjectPath
          else roots[rootProjectName]?.data?.linkedExternalProjectPath) ?: error("Cannot find root module data: $rootProjectName"),
          projectPath
        ),
        module = node,
        androidModel = androidModel
      )
    }.associateBy { it.gradleProjectPath },
    this
  )
}

@VisibleForTesting
fun DataNode<ProjectData>.getSelectedVariants(): Map<GradleProjectPath, VariantAndAbi> {
  return getAndroidModules()
    .modulesByGradleProjectPath
    .mapNotNull { (key, value) -> variantAndAbi(value.module)?.let { key to it } }
    .toMap()
}

/**
 * Finds a cached [ProjectData] with the [updatedModule] synced to [request] applied to the currently selected variant.
 *
 * If [request] is partial the application process preserves the current selection of the not specified component if it is possible.
 * If it is not and the specified component is `request.abi` a different variant matching the abi may be returned and if the specified
 * component is `request.variantName` and the target variant does not contain the currently selected abi a different abi may be returned.
 *
 */
private fun VariantProjectDataNodes.findVariantProjectData(
  updatedModule: Module,
  request: SwitchVariantRequest,
): DataNode<ProjectData>? {
  val sourceAndroidModuleModel = GradleAndroidModel.get(updatedModule) ?: return null
  val sourceNdkModuleModel = NdkModuleModel.get(updatedModule)
  val updatedModuleGradlePath = updatedModule.getGradleProjectPath()?.toHolder() ?: return null

  fun NdkModuleModel.variantAbis(variantName: String): Collection<String> {
    return allVariantAbis.filter { it.variant == variantName }.map { it.abi }
  }

  class Models(val node: DataNode<ProjectData>, val androidModel: GradleAndroidModelData, val ndkModel: NdkModuleModel?) {
    val variantName: String get() = androidModel.selectedVariantName
    val abi: String? get() = ndkModel?.selectedAbi

    fun isValidAbi(abi: String): Boolean {
      return ndkModel != null && ndkModel.variantAbis(variantName).contains(abi)
    }

    /**
     * Returns whether the cached variant contains a consistent variant selection under the given modules and whether the selection in
     * other modules matches the current selection.
     *
     * Because of variant selection conflicts there might be multiple cached data node trees having the same variant selection at the
     * requested module.
     */
    fun hasNoConflictsUnderUpdatedModule(): Boolean {
      return node.getAndroidModules().validateVariants(
        updatedModuleGradlePath,
        selectedVariant = { path -> path.resolveIn(updatedModule.project)?.let { GradleAndroidModel.get(it)?.selectedVariantName } }
      )
    }
  }

  fun DataNode<ProjectData>.getModels(): Models? {
    return findAll(this, ProjectKeys.MODULE)
      .firstOrNull { it.data.internalName == updatedModule.name }
      ?.let { moduleDataDataNode ->
        val androidModelDataNode = ExternalSystemApiUtil.find(moduleDataDataNode, AndroidProjectKeys.ANDROID_MODEL) ?: return null
        val ndkModelDataNode = ExternalSystemApiUtil.find(moduleDataDataNode, AndroidProjectKeys.NDK_MODEL)
        Models(this, androidModelDataNode.data, ndkModelDataNode?.data)
      }
  }

  fun getCachedVariants(): Sequence<Models> = data.asSequence().mapNotNull { it.getModels() }

  val currentVariant = sourceAndroidModuleModel.selectedVariantName
  val currentAbi = sourceNdkModuleModel?.selectedAbi
  val fullTarget =
    request.copy(
      variantName = request.variantName ?: currentVariant,
      abi = request.abi ?: currentAbi
    )

  getCachedVariants()
    .find { models ->
      models.variantName == fullTarget.variantName && models.abi == fullTarget.abi && models.hasNoConflictsUnderUpdatedModule()
    }
    ?.let { return it.node }

  val result = if (request.variantName == null && request.abi != null) {
    if (sourceNdkModuleModel?.variantAbis(currentVariant)?.contains(request.abi) == true) {
      // The requested abi is contained in the current variant and thus can be fetched by syncing.
      return null
    }
    getCachedVariants()
      .find { models -> models.abi == request.abi && models.hasNoConflictsUnderUpdatedModule() }
  } else {
    getCachedVariants()
      .find { models -> models.variantName == request.variantName && models.hasNoConflictsUnderUpdatedModule() }
      ?.takeIf { models ->
        //  A full match was not found but the newly selected variant contains the abi. We need to sync.
        currentAbi == null || !models.isValidAbi(currentAbi)
      }
  }

  return result?.node
}

/**
 * Returns the set of modules (moduleId's) which are assumed to be affected by build variant selection change when applied to the module
 * identified by a `moduleId`. It includes any modules the target module depends directly or indirectly on and also any of their feature
 * modules.
 */
private fun AndroidModules.validateVariants(moduleId: GradleProjectPath, selectedVariant: (GradleProjectPath) -> String?): Boolean {
  val libraryResolver = createLibraryResolverFor(projectData)

  class WorkItem(val module: AndroidModule, val expectedVariant: String?)

  val queue = ArrayDeque(listOf(WorkItem(modulesByGradleProjectPath[moduleId] ?: return false, null)))
  val seen = mutableSetOf<GradleProjectPath>()
  while (queue.isNotEmpty()) {
    val head = queue.removeFirst()
    if (head.expectedVariant != null) {
      if (head.module.androidModel.selectedVariantName != head.expectedVariant) {
        return false
      }
    }
    if (seen.add(head.module.gradleProjectPath)) {
      queue.addAll(
        head.module.androidModel.selectedVariant(libraryResolver)
          .let {
            it.mainArtifact.compileClasspath.libraries +
              it.unitTestArtifact?.compileClasspath?.libraries.orEmpty() +
              it.androidTestArtifact?.compileClasspath?.libraries.orEmpty() +
              it.testFixturesArtifact?.compileClasspath?.libraries.orEmpty()
          }.filterIsInstance<IdeModuleLibrary>()
          .mapNotNull { library ->
            WorkItem(
              module = modulesByGradleProjectPath[computeModuleIdForLibrary(library).toHolder()] ?: return@mapNotNull null,
              expectedVariant = library.variant
            )
          }
      )
      queue.addAll(
        head.module.androidModel.androidProject.dynamicFeatures
          // TODO: Fix support for dynamic features in included builds.
          .mapNotNull { dynamicFeatureId ->
            WorkItem(
              module = modulesByGradleProjectPath[GradleHolderProjectPath(
                head.module.gradleProjectPath.buildRoot,
                dynamicFeatureId
              )] ?: return@mapNotNull null,
              expectedVariant = null
            )
          }
      )
    }
  }

  return modulesByGradleProjectPath.all { (path, androidModule) ->
    seen.contains(path) || selectedVariant(path) == androidModule.androidModel.selectedVariantName
  }
}


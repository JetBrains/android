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
package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.dependencies.DeclaredDependencies
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeBasicVariantName
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantNameImpl
import kotlin.collections.mutableMapOf
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

/**
 * Minimal interface for topological sorting, hiding the full Sync models. path: Path of the consumer Project moduleId: ModuleId of the
 * consumer Project projectType: Project Type of the consumer Project. Needed to special case handle TEST_Project projects
 * outgoingDependencies: List of outgoing dependencies. This should not contains dependencies to self (coming from MPSS defined
 * dependencies)
 */
data class ProjectNode(
  val path: String,
  val moduleId: String,
  val projectType: IdeAndroidProjectType,
  val outgoingDependencies: List<String>,
)

class AndroidProjectData(
  val versions: Versions,
  val modelVersions: ModelVersions,
  val basicAndroidProject: BasicAndroidProject,
  val androidProject: AndroidProject,
  val androidDsl: AndroidDsl,
  val declaredDependencies: DeclaredDependencies,
  val gradlePluginModel: GradlePluginModel,
  val gradleTaskModel: GradleTaskModel,
  val ideAndroidProject: IdeAndroidProjectImpl,
  var selectedVariantName: String,
  val shouldSkipRuntimeClassPathForLibraries: Boolean,
) {
  var isSeen = false
}

/**
 * Entry point for variant selection / matching and model consumption for all the Android projects.
 *
 * This handles topological resolution of the projects variants:
 * 1. sort projects by priority.
 * 2. Then, processes projects in priority batches to resolve final variants for each project, and propagate the selection across
 *    dependencies.
 * 4. Finally, we consume the resulting [IdeBasicVariantName] for each project and updates the [cachedModels].
 *
 * @param results The list of projects and their associated [AndroidProjectData].
 * @param syncOptions that contains the variant switching request (if it exists).
 * @param modelConsumer to report the resolved variant models back to the IDE.
 * @param cachedModels A cache for storing resolved data to be used by other model builders across different sync phases.
 */
fun setupProjectsVariantsAndConsume(
  results: List<Pair<BasicGradleProject, AndroidProjectData>>,
  syncOptions: SyncActionOptions,
  modelConsumer: ProjectImportModelProvider.GradleModelConsumer,
  cachedModels: ModelProviderCachedData,
) {

  // Now sort the projects based on their priority criteria (projectType + number of incoming dependencies).
  val projectsWithPriority =
    sortProjectsByPriority(
      results,
      { (gradleProject, androidData) ->
        ProjectNode(
          path = gradleProject.path,
          moduleId = gradleProject.moduleId(),
          projectType = androidData.ideAndroidProject.projectType,
          outgoingDependencies = androidData.declaredDependencies.allOutgoingProjectDependencies.filter { it != gradleProject.path },
        )
      },
      syncOptions,
    )

  // Cache the mapping of gradleProject -> expected variant from dependant projects that got
  // resolved already.
  val projectsAndRequestedVariants = mutableMapOf<String, String>()

  // Resolve the variants at this stage handling each level of priority at a time, and considering the declared project dependencies.
  for (nextBatch in projectsWithPriority.values) {
    val modulesToVisit = nextBatch.filter { !it.second.isSeen }
    if (modulesToVisit.isEmpty()) continue
    modulesToVisit.forEach { (gradleProject, androidProjectContext) ->
      androidProjectContext.isSeen = true
      val selectedVariantNameModel =
        getSelectedVariantName(
          androidProjectContext.selectedVariantName,
          androidProjectContext.androidDsl,
          androidProjectContext.basicAndroidProject,
          androidProjectContext.declaredDependencies,
          gradleProject,
          projectsAndRequestedVariants,
        )
      // Update the selected variant for this project: this is important because this data is
      // passed through to other model builders.
      androidProjectContext.selectedVariantName = selectedVariantNameModel.name

      modelConsumer.consumeProjectModel(gradleProject, selectedVariantNameModel, IdeBasicVariantName::class.java)

      // Set the cachedData variant value.
      cachedModels.data[gradleProject] =
        CachedAndroidProjectData(
          androidProjectContext.modelVersions,
          androidProjectContext.selectedVariantName,
          androidProjectContext.ideAndroidProject,
          androidProjectContext.shouldSkipRuntimeClassPathForLibraries,
          androidProjectContext.declaredDependencies.allOutgoingProjectDependencies,
        )
    }
  }
}

/**
 * Returns the priority value for a given project.
 *
 * Priority 0: The project specifically requested for variant switching (highest intent). Priority 1: App modules (primary entry points for
 * the graph). Priority 2: All other modules.
 */
@VisibleForTesting
fun getPriorityValue(projectNode: ProjectNode, syncOptions: SyncActionOptions, projectType: IdeAndroidProjectType) =
  when {
    // The project for which we are changing the selected variant has the highest priority.
    projectNode.moduleId == (syncOptions as? SingleVariantSyncActionOptions)?.switchVariantRequest?.moduleId -> 0
    // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
    // The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
    // dependencies of others and will be visited sooner and the configurations created below will be discarded without being fetched.
    projectType == IdeAndroidProjectType.PROJECT_TYPE_APP -> 1
    // The rest of the projects are treated as similar priority.
    else -> 2
  }

/**
 * Groups projects into topological layers (batches) to ensure consumers resolve before producers.
 *
 * This ensures that when a module is resolved, all its consumers have already finished and contributed their resolution requirements
 * (strategies and fallbacks) to the global maps.
 */
fun <T> sortProjectsByPriority(projects: List<T>, nodeMapper: (T) -> ProjectNode, syncOptions: SyncActionOptions): Map<Int, List<T>> {
  val projectsWithNodes = projects.map { it to nodeMapper(it) }
  val nodeMap = projectsWithNodes.associate { it.second.path to it.second }

  // 1. Build the mapping of each project to the # of incoming dependencies.
  val inWeight = mutableMapOf<String, Int>()
  projectsWithNodes.forEach { (_, node) ->
    node.outgoingDependencies.forEach { dep ->
      // Only ignore edges targeting an APP module to prevent Graph Inversion.
      // This preserves dependencies on libraries (e.g. Test -> SharedLib) while ensuring Apps resolve first.
      if (nodeMap[dep]?.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP) return@forEach
      inWeight[dep] = (inWeight[dep] ?: 0) + 1
    }
  }

  // 2. Now we are going to classify these weighted projects into batches for layers processing.
  val headProjectsToBatch = mutableMapOf<String, Int>() // This will get fed Apps, and Libraries with no incoming dependencies.
  val queue = ArrayDeque<String>()

  // 2.1 Seed initial entry points (Priorities 0 and 1)
  projectsWithNodes.forEach { (_, projectNode) ->
    val priority = getPriorityValue(projectNode, syncOptions, projectNode.projectType)
    if (priority == 0) {
      headProjectsToBatch[projectNode.path] = 0
      queue.addFirst(projectNode.path)
    } else if (priority == 1 && !headProjectsToBatch.containsKey(projectNode.path)) {
      headProjectsToBatch[projectNode.path] = 1
      queue.addLast(projectNode.path)
    }
  }

  // 2.2  Handle libraries that do not have consumers.
  projectsWithNodes.forEach { (_, node) ->
    if (!inWeight.containsKey(node.path) && !headProjectsToBatch.containsKey(node.path)) {
      headProjectsToBatch[node.path] = 2
      queue.addLast(node.path)
    }
  }

  // 3. Propagation using transitive dependencies.
  val switchId = (syncOptions as? SingleVariantSyncActionOptions)?.switchVariantRequest?.moduleId
  while (queue.isNotEmpty()) {
    val consumerPath = queue.removeFirst()
    val consumerBatch = headProjectsToBatch[consumerPath]!!
    val node = nodeMap[consumerPath] ?: continue

    node.outgoingDependencies.forEach { producerPath ->
      // Skip edges to the switch target (already pinned) or to Apps (this is usually either dynamic features of TEST_ONLY) projects
      if (nodeMap[producerPath]?.moduleId == switchId) return@forEach
      if (nodeMap[producerPath]?.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP) return@forEach

      // Each time we go through a consumer project, we decrease the amount of incoming deps (handled) for this project.
      // Once all it's consumers have been handled (weight = 0), then we can handle this project (i.e. add it to the queue for processing)
      inWeight[producerPath]?.let { inWeight[producerPath] = it - 1 }

      val currentBatch = headProjectsToBatch[producerPath] ?: -1
      if (consumerBatch + 1 > currentBatch && inWeight[producerPath]!! <= 0) {
        headProjectsToBatch[producerPath] = consumerBatch + 1
        queue.addLast(producerPath)
      }
    }
  }

  return projectsWithNodes
    .groupBy(keySelector = { headProjectsToBatch[it.second.path] ?: 1000 }, valueTransform = { it.first })
    .toSortedMap()
}

@VisibleForTesting
fun getSelectedVariantName(
  variantFromModule: String,
  androidDsl: AndroidDsl,
  basicAndroidProject: BasicAndroidProject,
  declaredDependenciesModel: DeclaredDependencies,
  gradleProject: BasicGradleProject,
  projectsAndRequestedVariants: MutableMap<String, String>,
): IdeBasicVariantNameImpl {
  var updatedVariant = variantFromModule

  val expectedVariantFromDependencies = projectsAndRequestedVariants[gradleProject.path]

  // 1st case: we don't expect a specific variant: take the variant that we initially computed.
  if (expectedVariantFromDependencies == null) {
    val variantObject = basicAndroidProject.variants.firstOrNull { it.name == variantFromModule }
    // 1.1: the variant we want to sync exists.
    if (variantObject != null) {
      // We don't need to update the value of androidProjectContext.selectedVariantName
      // Now we set up the expected variant for all our project dependencies.
      setUpExpectedVariantForDependantProjects(
        variantObject.name,
        declaredDependenciesModel.allOutgoingProjectDependencies,
        projectsAndRequestedVariants,
      )
    }
    // 1.2: the variant we expected to sync doesn't exist, se we fallback to the default variant.
    else {
      // TODO: the error thrown below will fail sync, but we should just throw a warning.
      updatedVariant =
        getDefaultVariant(basicAndroidProject, androidDsl) ?: error("Unable to find a variant to Sync for ${gradleProject.path}")
    }
  }
  // 2nd case: we are expecting a specific variant to resolve based on this project's dependencies.
  else {
    val variantObject = basicAndroidProject.variants.firstOrNull { it.name == expectedVariantFromDependencies }
    // 2.1: The expected variant exists
    if (variantObject != null) {
      updatedVariant = variantObject.name
    }
    // 2.2: The variant we are expecting does not exist, and we need to go through the fallbacks.
    else {
      // Get the default variant.
      updatedVariant =
        getDefaultVariant(basicAndroidProject, androidDsl) ?: error("Unable to find a variant to Sync for ${gradleProject.path}")
    }
  }

  return IdeBasicVariantNameImpl(updatedVariant)
}

private fun getDefaultVariant(basicAndroidProject: BasicAndroidProject, androidDsl: AndroidDsl) =
  basicAndroidProject.variants.toList().getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors)

private fun setUpExpectedVariantForDependantProjects(
  variantToSync: String,
  outgoingProjectDependencies: List<String>,
  projectsAndRequestedVariants: MutableMap<String, String>,
) {
  outgoingProjectDependencies.forEach {
    // populate that map with the requested variant with its fallbacks IF THEY EXIST
    val requestedVariantsForDependency = projectsAndRequestedVariants[it]
    // there is nothing yet (variants / fallbacks) that we expect from this dependency module.
    if (requestedVariantsForDependency == null) projectsAndRequestedVariants[it] = variantToSync
  }
}

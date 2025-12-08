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

import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.LegacyAndroidGradlePluginProperties
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
  val legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
) {
  var isSeen = false
}

/** Encapsulates the state of variant resolution across all projects during a sync phase. */
class VariantResolutionContext {
  // Cache the mapping of gradleProject -> expected variant from dependant projects that got resolved already.
  val projectsAndRequestedVariants = mutableMapOf<String, String>()
  // Mapping of the expected variant to it's buildType, followed by all the matchingFallbacks ordered by priority.
  val variantToBuildTypeAndFallbacks = mutableMapOf<String, BuildTypeAndFallbacks>()
  // Mapping of variant to every (dimension, product flavor, fallbacks (if any)).
  // variantName -> (dim1 -> (flavor1.1, backup1, backup2), dim2 ->(flavor2.1, backup11, backup22)).
  val variantToProductFlavorsAndDimensions = mutableMapOf<String, Map<String, ProductFlavorsAndFallbacks>>()
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

  val variantResolutionContext = VariantResolutionContext()

  // Resolve the variants at this stage handling each level of priority at a time, and considering the declared project dependencies.
  for (nextBatch in projectsWithPriority.values) {
    val modulesToVisit = nextBatch.filter { !it.second.isSeen }
    if (modulesToVisit.isEmpty()) continue
    modulesToVisit.forEach { (gradleProject, androidProjectContext) ->
      androidProjectContext.isSeen = true
      val selectedVariantNameModel = getSelectedVariantName(gradleProject, androidProjectContext, variantResolutionContext)
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
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantResolutionContext: VariantResolutionContext,
): IdeBasicVariantNameImpl {
  val variantFromModule = androidProjectContext.selectedVariantName
  val androidDsl = androidProjectContext.androidDsl
  val basicAndroidProject = androidProjectContext.basicAndroidProject
  val declaredDependenciesModel = androidProjectContext.declaredDependencies
  val modelVersions = androidProjectContext.modelVersions
  val legacyAndroidGradlePluginProperties = androidProjectContext.legacyAndroidGradlePluginProperties

  var updatedVariant = variantFromModule

  val expectedVariantFromDependencies = variantResolutionContext.projectsAndRequestedVariants[gradleProject.path]

  // 1st case: we don't expect a specific variant: take the variant that we initially computed.
  if (expectedVariantFromDependencies == null) {
    val variantObject = basicAndroidProject.variants.firstOrNull { it.name == variantFromModule }
    // 1.1: the variant we want to sync exists.
    if (variantObject != null) {
      // We don't need to update the value of androidProjectContext.selectedVariantName
      // Get the build type, productFlavor(s) and their fallbacks in order of priority.
      setBuildTypeAndProductFlavorsWithFallbacksForVariant(
        variantObject,
        variantResolutionContext,
        androidDsl,
        modelVersions,
        legacyAndroidGradlePluginProperties,
      )

      // Now we set up the expected variant for all our project dependencies.
      setUpExpectedVariantForDependantProjects(
        // Here there is nothing we were expecting to Sync initially, so we propagate the variantName that we have asked for.
        variantObject.name,
        declaredDependenciesModel.allOutgoingProjectDependencies,
        variantResolutionContext.projectsAndRequestedVariants,
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
    val variantToSync =
      if (variantObject != null) {
        variantObject.name
      }
      // 2.2: The variant we are expecting does not exist, and we need to go through the fallbacks.
      else {
        val buildTypeAndFallbacks = variantResolutionContext.variantToBuildTypeAndFallbacks[expectedVariantFromDependencies]

        val productFlavorsAndFallbacks =
          if (androidDsl.productFlavors.isNotEmpty()) {
            variantResolutionContext.variantToProductFlavorsAndDimensions[expectedVariantFromDependencies]
          } else {
            mutableMapOf() // if this projects does not have any productFlavors, then there is no ambiguity to solve, and we don't need to
            // resolve productFlavors for it.
          }

        if (buildTypeAndFallbacks == null) error(" Failed to find a variant for ${gradleProject.path}. Falling back to the default one.")
        // Get the buildType if exists or fallback into the fallbacks in a priority descending order.
        val buildTypeOrFallback =
          androidDsl.buildTypes.firstOrNull { it.name == buildTypeAndFallbacks.buildType }
            ?:
            // This case means there isn't a buildType direct match , and need to check  the fallbacks.
            buildTypeAndFallbacks.matchingFallbacks.firstNotNullOfOrNull { fallback ->
              androidDsl.buildTypes.firstOrNull { it.name == fallback }
            }

        // If we have expected productFlavors, then we should use them:
        val resolvedProductFlavors =
          productFlavorsAndFallbacks?.map { (dimension, flavors) ->
            // We first check if we can match directly the productFlavors before falling to the fallbacks.
            androidDsl.productFlavors.firstOrNull { it.name == flavors.productFlavor && it.dimension == dimension }
              ?:
              // Otherwise, pick the first existing matchingFallback.
              flavors.matchingFallbacks.firstNotNullOfOrNull { fallback -> androidDsl.productFlavors.firstOrNull { it.name == fallback } }
          }
            ?:
            // Otherwise, this means that the request came from a project with
            // no dimensions, so we will still need to resolve the flavors in this project (if any).
            // Important: In this case, and because we have no guidelines from the dependencies that we have resolved so far, the only way
            // we can resolve this
            // variant for this project would be if there is no productFlavor ambiguity (i.e. each dimension has one flavor), otherwise we
            // won't be able to pick
            // a productFlavor accurately, and we should throw the error here).
            // TODO: this will become a warning in sync, and we will fallback to the default variant for this project.
            androidDsl.flavorDimensions.map { dim ->
              androidDsl.productFlavors.singleOrNull { flavor -> flavor.dimension == dim }
                ?: error(
                  "Cannot resolve variant ${expectedVariantFromDependencies}. Cannot resolve ambiguity of productFlavors for ${gradleProject.path}."
                )
            }

        // If we can't resolve the variant based on the requirements, then we fall back to the default one we had initially.
        if (buildTypeOrFallback == null || resolvedProductFlavors.contains(null))
          error("Variant resolution conflict: Cannot find a variant for ${gradleProject.path}.") // variantFromModule
        else {
          // need to now create a variant out of this build type and productFlavors.
          basicAndroidProject.variants
            .singleOrNull { variant ->
              buildTypeOrFallback.let { variant.buildType != null && variant.buildType == it.name } &&
                resolvedProductFlavors.all {
                  variant.productFlavors.contains(it!!.name)
                } // This is the case where we should extend to missingDimensionStrategy.
            }
            ?.name ?: basicAndroidProject.variants.toList().getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors)
        }
      } ?: error("Variant resolution conflict: Cannot find a variant for ${gradleProject.path}.")

    setBuildTypeAndProductFlavorsWithFallbacksForVariant(
      basicAndroidProject.variants.singleOrNull { it.name == variantToSync }
        ?: error("No existing variant that matches the name $variantToSync for project ${gradleProject.path}."),
      variantResolutionContext,
      androidDsl,
      modelVersions,
      legacyAndroidGradlePluginProperties,
    )
    // get the dependencies as well and set the expected variant for them.
    setUpExpectedVariantForDependantProjects(
      expectedVariantFromDependencies,
      declaredDependenciesModel.allOutgoingProjectDependencies,
      variantResolutionContext.projectsAndRequestedVariants,
    )

    updatedVariant = variantToSync
  }
  return IdeBasicVariantNameImpl(updatedVariant)
}

fun BasicGradleProject.moduleId() = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, path)

private fun setBuildTypeAndProductFlavorsWithFallbacksForVariant(
  variant: BasicVariant,
  variantResolutionContext: VariantResolutionContext,
  androidDsl: AndroidDsl,
  modelVersions: ModelVersions,
  legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
) {
  getBuildTypesAndFallbacksInPriorityOrder(
    variant,
    variantResolutionContext.variantToBuildTypeAndFallbacks,
    androidDsl,
    modelVersions,
    legacyAndroidGradlePluginProperties,
  )
  // Now do the productFlavors (if any) and their dimensions.
  if (androidDsl.productFlavors.isNotEmpty())
    getProductFlavorsAndFallbacksInOrder(
      variant,
      variantResolutionContext.variantToProductFlavorsAndDimensions,
      androidDsl,
      modelVersions,
      legacyAndroidGradlePluginProperties,
    )
}

private fun getBuildTypesAndFallbacksInPriorityOrder(
  variant: BasicVariant,
  variantToBuildTypeAndFallbacks: MutableMap<String, BuildTypeAndFallbacks>,
  androidDsl: AndroidDsl,
  modelVersion: ModelVersions,
  legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
) {
  val variantAndBuildTypes = variantToBuildTypeAndFallbacks[variant.name]
  val buildTypesAndFallbacks = getBuildTypeAndFallbacksForVariant(androidDsl, variant, modelVersion, legacyAndroidGradlePluginProperties)
  if (variantAndBuildTypes == null) {
    variantToBuildTypeAndFallbacks[variant.name] =
      BuildTypeAndFallbacks(buildTypesAndFallbacks.first, buildTypesAndFallbacks.second.toMutableSet())
  } else {
    variantToBuildTypeAndFallbacks[variant.name]!!.matchingFallbacks.addAll(buildTypesAndFallbacks.second)
  }
}

private fun getProductFlavorsAndFallbacksInOrder(
  variant: BasicVariant,
  variantToProductFlavorsAndFallbacks: MutableMap<String, Map<String, ProductFlavorsAndFallbacks>>,
  androidDsl: AndroidDsl,
  modelVersion: ModelVersions,
  legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
) {
  // Get the productFlavors and their matchingFallbacks for this variant and this project.
  val incomingFlavors = getProductFlavorsPerDimensionsForVariant(androidDsl, variant, modelVersion, legacyAndroidGradlePluginProperties)
  // Get the list of cached productFlavors and their fallbacks (if any) for this variant: these could have been specified by other projects.
  val existingVariant = variantToProductFlavorsAndFallbacks[variant.name]

  // We don't have any fallbacks yet for this variant.
  if (existingVariant == null) {
    variantToProductFlavorsAndFallbacks[variant.name] =
      incomingFlavors.mapValues { (_, v) -> ProductFlavorsAndFallbacks(v.first, v.second.toMutableSet()) }
  } else {
    // We already have fallbacks for this variant, so we append the ones from this project to the existing list.
    for ((dim, flavorsAndFallbacks) in incomingFlavors) {
      val existingDimEntry =
        existingVariant.get(dim)
          ?: error("Cannot resolve variant: $variant. There are no matching fallbacks specified for the '$dim' dimension.")
      existingDimEntry.matchingFallbacks.addAll(flavorsAndFallbacks.second)
    }
  }
}

private fun getBuildTypeAndFallbacksForVariant(
  androidDsl: AndroidDsl,
  variant: BasicVariant,
  modelVersions: ModelVersions,
  legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
): Pair<String, List<String>> {
  // if this variant buildType specifies fallbacks to use, then add them to the cache of fallbacks.
  val buildTypeForNewVariant =
    androidDsl.buildTypes.singleOrNull { variant.buildType == it.name }
      ?: error(" There is no BuildType associated with ${variant.name} variant.")
  val fallbacks =
    if (modelVersions[ModelFeature.HAS_MATCHING_FALLBACKS]) buildTypeForNewVariant.matchingFallbacks
    else legacyAndroidGradlePluginProperties?.productFlavorsMatchingFallbacks[buildTypeForNewVariant.name] ?: emptyList()
  return Pair(buildTypeForNewVariant.name, fallbacks)
}

/** Returns a map of: dimension -> Pair.of(productFlavor, listOf(fallbacks)) */
private fun getProductFlavorsPerDimensionsForVariant(
  androidDsl: AndroidDsl,
  variant: BasicVariant,
  modelVersions: ModelVersions,
  legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
): Map<String, Pair<String, List<String>>> {
  return androidDsl.productFlavors
    .filter { variant.productFlavors.contains(it.name) }
    .associate {
      val fallbacks =
        if (modelVersions[ModelFeature.HAS_MATCHING_FALLBACKS]) it.matchingFallbacks
        else legacyAndroidGradlePluginProperties?.productFlavorsMatchingFallbacks[it.name] ?: emptyList()

      it.dimension!! to Pair(it.name, fallbacks)
    }
}

private fun getDefaultVariant(basicAndroidProject: BasicAndroidProject, androidDsl: AndroidDsl) =
  basicAndroidProject.variants.toList().getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors)

private fun setUpExpectedVariantForDependantProjects(
  variantToSync: String, // If there is an expected variant then we use that, if not then we set using the variantToSync
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

data class BuildTypeAndFallbacks(val buildType: String, val matchingFallbacks: MutableSet<String>)

data class ProductFlavorsAndFallbacks(
  val productFlavor: String,
  val matchingFallbacks:
    MutableSet<String>, // This is in reality a LinkedHashSet, so orders of insertion will be respected (for priority of fallbacks).
)

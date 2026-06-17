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

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.ProjectType
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
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlinx.collections.immutable.toImmutableMap
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

  val productFlavorsByDimensionAndName: Map<Pair<String, String>, ProductFlavor> by
    lazy(LazyThreadSafetyMode.PUBLICATION) { androidDsl.productFlavors.associateBy { (it.dimension ?: "") to it.name } }

  val productFlavorsByName: Map<String, ProductFlavor> by
    lazy(LazyThreadSafetyMode.PUBLICATION) { androidDsl.productFlavors.associateBy { it.name } }

  val buildTypesByName: Map<String, BuildType> by lazy(LazyThreadSafetyMode.PUBLICATION) { androidDsl.buildTypes.associateBy { it.name } }
}

/** Encapsulates the state of variant resolution across all projects during a sync phase. */
class VariantResolutionContext {
  /** Map of project identity to the variant requirements pushed from its consumers. */
  val projectVariantRequirements = mutableMapOf<ProjectBuildInfo, VariantRequirement>()

  /** Map of project identity to the final variant selection (Registry of Outcomes). */
  val projectToSelectedVariants = mutableMapOf<ProjectBuildInfo, VariantAndPriority>()
}

/** The group of requirements defining a project's variant resolution. */
data class VariantRequirement(
  /** The expected variant */
  val variant: BasicVariant,
  /** The expected buildType details including the fallbacks. */
  val buildTypeRequirement: VariantBuildTypeAndFallbacks? = null,
  /** The expected productFlavors requirements and their fallbacks. */
  val flavorRequirements: Map<String, ProductFlavorsAndFallbacks> = emptyMap(),
  /** The expected missingDimensionStrategy information. */
  val missingDimensionStrategies: Map<String, MissingDimensionStrategies> = emptyMap(),
  /** The priority of this expected variant (used to compare consumers priority) */
  val priority: Int,
)

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
 * @param variantsResolutionIssues A map to collect any exceptions that occurred during variant resolution for each project.
 */
fun setupProjectsVariantsAndConsume(
  results: List<Pair<BasicGradleProject, AndroidProjectData>>,
  syncOptions: SyncActionOptions,
  modelConsumer: ProjectImportModelProvider.GradleModelConsumer,
  cachedModels: ModelProviderCachedData,
  variantsResolutionIssues: MutableMap<BasicGradleProject, Throwable>,
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
  projectsWithPriority.forEach { (priority, nextBatch) ->
    nextBatch.forEach { (gradleProject, androidProjectContext) ->
      if (!androidProjectContext.isSeen) {
        var selectedVariantNameModel: IdeBasicVariantNameImpl? = null
        try {
          androidProjectContext.isSeen = true
          selectedVariantNameModel = getSelectedVariantName(gradleProject, androidProjectContext, variantResolutionContext, priority)

          // Update the selected variant for this project: this is important because this data is
          // passed through to other model builders.
          androidProjectContext.selectedVariantName = selectedVariantNameModel.name
        } catch (e: Exception) {
          variantsResolutionIssues[gradleProject] = e
        } finally {
          // If there was an exception fetching the selected variant for the project, then we need to safely fall back to the default value.
          val finalVariantNameModel = selectedVariantNameModel ?: IdeBasicVariantNameImpl(androidProjectContext.selectedVariantName)
          modelConsumer.consumeProjectModel(gradleProject, finalVariantNameModel, IdeBasicVariantName::class.java)

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

  // 2.1 Seed initial entry points (Priorities 0 and 1) and handle libraries without consumers.
  projectsWithNodes.forEach { (_, projectNode) ->
    val priority = getPriorityValue(projectNode, syncOptions, projectNode.projectType)
    when {
      priority == 0 -> {
        headProjectsToBatch[projectNode.path] = 0
        queue.addFirst(projectNode.path)
      }
      priority == 1 && !headProjectsToBatch.containsKey(projectNode.path) -> {
        headProjectsToBatch[projectNode.path] = 1
        queue.addLast(projectNode.path)
      }
    }
  }

  // 2.2  Handle libraries that do not have consumers. This way we are sure these are always added last to the deque so they get processed
  // after Apps.
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
      val producerNode = nodeMap[producerPath] ?: return@forEach
      // Skip edges to the switch target (already pinned) or to Apps (this is usually either dynamic features of TEST_ONLY) projects
      if (producerNode.moduleId == switchId) return@forEach
      if (producerNode.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP) return@forEach

      // Each time we go through a consumer project, we decrease the amount of incoming deps (handled) for this project.
      // Once all it's consumers have been handled (weight = 0), then we can handle this project (i.e. add it to the queue for processing)
      inWeight[producerPath] = inWeight.getValue(producerPath) - 1

      val currentBatch = headProjectsToBatch[producerPath] ?: -1
      if (inWeight.getValue(producerPath) <= 0 && consumerBatch + 1 > currentBatch) {
        headProjectsToBatch[producerPath] = consumerBatch + 1
        queue.addLast(producerPath)
      }
    }
  }

  return projectsWithNodes
    .groupBy(keySelector = { headProjectsToBatch[it.second.path] ?: 1000 }, valueTransform = { it.first })
    .toSortedMap()
}

/**
 * Resolves the selected variant name for a module during Phased Sync.
 *
 * This function applies a hierarchical matching order (Direct Match -> Attributes -> Fallbacks -> Strategies) to ensure that the IDE
 * selects a stable and functional variant that satisfies all its consumers.
 */
@VisibleForTesting
fun getSelectedVariantName(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantResolutionContext: VariantResolutionContext,
  priority: Int,
): IdeBasicVariantNameImpl {

  val currentProjectId = ProjectBuildInfo(gradleProject.path, gradleProject.projectIdentifier.buildIdentifier.rootDir)
  val variantRequirement = variantResolutionContext.projectVariantRequirements[currentProjectId]
  val (variantToSync, effectivePriority) = resolveVariantByCase(gradleProject, androidProjectContext, variantResolutionContext, priority)

  setUpExpectedVariantForDependantProjects(
    gradleProject,
    androidProjectContext,
    variantToSync,
    variantRequirement,
    variantResolutionContext,
    effectivePriority,
  )

  // Cache the result of the final selected variants.
  variantResolutionContext.projectToSelectedVariants[currentProjectId] = VariantAndPriority(variantToSync, effectivePriority)

  return IdeBasicVariantNameImpl(variantToSync.name)
}

private fun resolveVariantByCase(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantResolutionContext: VariantResolutionContext,
  priority: Int,
): VariantAndPriority {
  val currentProjectId = ProjectBuildInfo(gradleProject.path, gradleProject.projectIdentifier.buildIdentifier.rootDir)
  val variantRequirement = variantResolutionContext.projectVariantRequirements[currentProjectId]

  return when (androidProjectContext.basicAndroidProject.projectType) {
    ProjectType.TEST -> resolveTestProjectVariant(gradleProject, androidProjectContext, variantResolutionContext, priority)
    ProjectType.DYNAMIC_FEATURE ->
      resolveDynamicFeatureStrictVariantMatching(gradleProject, androidProjectContext, variantRequirement, priority)
    ProjectType.APPLICATION -> resolveApplicationVariantMatching(gradleProject, androidProjectContext, variantRequirement, priority)
    else -> {
      // We do not require anything for this project (from other consumers), so we just resolve the variant based on the initial
      // AndroidProjectData variant.
      if (variantRequirement == null) {
        resolveIndependentVariant(gradleProject, androidProjectContext, priority)
      } else {
        // We do require a variant from other consumers (this is the case of libraries).
        resolveLibraryVariant(gradleProject, androidProjectContext, variantRequirement)
      }
    }
  }
}

private fun resolveIndependentVariant(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  priority: Int,
): VariantAndPriority {
  val variant =
    androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == androidProjectContext.selectedVariantName }
      ?: throw IllegalStateException(
        "Variant Conflict: Unable to find variant " +
          "\"${androidProjectContext.selectedVariantName}\" to Sync for project: ${gradleProject.path}."
      )
  return VariantAndPriority(variant, priority)
}

private fun resolveTestProjectVariant(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantResolutionContext: VariantResolutionContext,
  priority: Int,
): VariantAndPriority {
  val targetAppPath =
    androidProjectContext.androidProject.variants.firstOrNull { it.testedTargetVariant != null }?.testedTargetVariant?.targetProjectPath
  val targetAppId = targetAppPath?.let { ProjectBuildInfo(it, gradleProject.projectIdentifier.buildIdentifier.rootDir) }
  val targetAppVariant = targetAppId?.let { variantResolutionContext.projectToSelectedVariants[it] }

  val effectivePriority = targetAppVariant?.priority ?: priority
  val appVariant = targetAppVariant?.variant

  if (appVariant != null) {
    // AppVariant exists: First check if we have an expected variant selected by APP and that matches a variant in the TEST project.
    val matchingVariantForTestProject = androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == appVariant.name }
    // Check if we can get matching per variant attributes and not just the name.
    if (matchingVariantForTestProject != null && verifyAllVariantAttributesMatch(appVariant, matchingVariantForTestProject)) {
      return VariantAndPriority(matchingVariantForTestProject, effectivePriority)
    }
  }

  // If We couldn't get a variant to propagate from APP, then just use the best variant we can select (based on Sync scenario).
  val variant =
    androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == androidProjectContext.selectedVariantName }
      // Only throw if there is no variant at all to sync for This project.
      ?: throw IllegalStateException(
        "Variant Conflict: Unable to find variant \"${androidProjectContext.selectedVariantName}\" to Sync for project: ${gradleProject.path}."
      )

  return VariantAndPriority(variant, priority)
}

private fun resolveDynamicFeatureStrictVariantMatching(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantRequirement: VariantRequirement?,
  priority: Int,
): VariantAndPriority {
  if (variantRequirement != null) {
    val variant = androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == variantRequirement.variant.name }
    if (variant != null && verifyAllVariantAttributesMatch(variantRequirement, variant, androidProjectContext.androidDsl)) {
      return VariantAndPriority(variant, variantRequirement.priority)
    } else {
      throw IllegalStateException(
        "Variant conflict: Unable to find variant \"${variantRequirement.variant.name}\" to Sync for project: ${gradleProject.path}."
      )
    }
  } else {
    // Otherwise, we don't have a requirement and we just pick the variant that we initially wanted.
    val variant =
      androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == androidProjectContext.selectedVariantName }
        ?: throw IllegalStateException(
          "Variant conflict: Unable to find variant " +
            "\"${androidProjectContext.selectedVariantName}\" to Sync for project: ${gradleProject.path}."
        )
    return VariantAndPriority(variant, priority)
  }
}

private fun resolveApplicationVariantMatching(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantRequirement: VariantRequirement?,
  priority: Int,
): VariantAndPriority {
  if (variantRequirement != null) {
    val variant = androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == variantRequirement.variant.name }
    return if (variant != null) {
      // Verify the matching is not by name, but also by the attributes.
      if (verifyAllVariantAttributesMatch(variantRequirement, variant, androidProjectContext.androidDsl))
        VariantAndPriority(variant, variantRequirement.priority)
      else
        throw IllegalStateException(
          "Variant conflict: Unable to find variant \"${variantRequirement.variant.name}\" to Sync for project: ${gradleProject.path}."
        )
    } else {
      // we couldn't find a direct match so use the attribute matching like a library. We handle dynamic feature case because it only passes
      // the variant name down in the requirements for its dependencies
      resolveLibraryVariant(gradleProject, androidProjectContext, variantRequirement)
    }
  }

  val expectedVariantFromSelf =
    androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == androidProjectContext.selectedVariantName }
      ?: throw IllegalStateException(
        "Variant conflict: Unable to find variant \"${androidProjectContext.selectedVariantName}\" to " +
          "Sync for project: ${gradleProject.path}."
      )
  return VariantAndPriority(expectedVariantFromSelf, priority)
}

private fun resolveLibraryVariant(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantRequirement: VariantRequirement,
): VariantAndPriority {
  val variantObject = androidProjectContext.basicAndroidProject.variants.firstOrNull { it.name == variantRequirement.variant.name }
  val variant =
    if (variantObject != null && verifyAllVariantAttributesMatch(variantRequirement, variantObject, androidProjectContext.androidDsl)) {
      variantObject
    } else {
      resolveVariantAttributes(gradleProject, androidProjectContext, variantRequirement)
    }
  return VariantAndPriority(variant, variantRequirement.priority)
}

/** Reconciles variant attributes (BuildType and ProductFlavors) to find a best-fit variant. */
private fun resolveVariantAttributes(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantRequirement: VariantRequirement,
): BasicVariant {
  val dimensionsToFlavors = mutableMapOf<String, ProductFlavor>()

  androidProjectContext.androidDsl.flavorDimensions.forEach { dimension ->
    // 1. multiple flavors: they either need to match as 1 to one mapping or specify fallbacks if they don't match.
    if (variantRequirement.flavorRequirements.containsKey(dimension)) {
      val flavorRequirement = variantRequirement.flavorRequirements[dimension]!!
      dimensionsToFlavors[dimension] =
        androidProjectContext.productFlavorsByDimensionAndName[dimension to flavorRequirement.productFlavor]
          // Otherwise, pick the first existing matchingFallback.
          ?: flavorRequirement.matchingFallbacks.firstNotNullOfOrNull { fallbackFlavorName ->
            androidProjectContext.productFlavorsByName[fallbackFlavorName]
          }
          // We have dimension matching between dependencies, but there is only one productFlavor in this project, so we do
          // not need to resolve any ambiguity and can pick the single product flavor
          ?: androidProjectContext.androidDsl.productFlavors.singleOrNull { it.dimension == dimension }
          ?: throw IllegalStateException(
            "Variant Conflict: Unresolved variant \"${variantRequirement.variant.name}\".\nCause: Could not resolve ProductFlavors ambiguity for project: ${gradleProject.path}."
          )
    }
    // 2. In this case we have a mismatch between dimensions and in this case we need to use missingDimensionStrategy to find what to use
    // for this dimension
    else if (variantRequirement.missingDimensionStrategies.containsKey(dimension)) {
      // in this case we do have some resolutionStrategy for this dimension, so use it.
      dimensionsToFlavors[dimension] =
        variantRequirement.missingDimensionStrategies[dimension]!!.requestedFlavors.firstNotNullOfOrNull { requestedFlavor ->
          // find a productFlavor that matches the requirements (dimension and name).
          androidProjectContext.productFlavorsByDimensionAndName[dimension to requestedFlavor]
          // And if we don't find any matching PF, we warn about it.
        }
          ?: throw IllegalStateException(
            "Variant Conflict: Unresolved variant \"${variantRequirement.variant.name}\".\nCause: Could not resolve ProductFlavors ambiguity for project: ${gradleProject.path}"
          )
    }
    // We have dimension matching between dependencies, so here we use:
    // 3. if we have one flavor -> use it.
    else if (androidProjectContext.androidDsl.productFlavors.singleOrNull { it.dimension == dimension } != null)
      dimensionsToFlavors[dimension] = androidProjectContext.androidDsl.productFlavors.single { it.dimension == dimension }
    else {
      throw IllegalStateException(
        "Variant Conflict: Unresolved variant \"${variantRequirement.variant.name}\".\nCause: Could not resolve ProductFlavors ambiguity for project: ${gradleProject.path}."
      )
    }
  }

  // Determine the BuildType for this project's variant.
  if (variantRequirement.buildTypeRequirement == null && androidProjectContext.androidDsl.buildTypes.isNotEmpty())
    throw IllegalStateException(
      "Variant Conflict: Unresolved variant \"${variantRequirement.variant.name}\".\nCause: Could not resolve BuildTypes ambiguity for project: ${gradleProject.path}."
    )

  val buildTypeOrFallback =
    if (androidProjectContext.androidDsl.buildTypes.isNotEmpty()) {
      androidProjectContext.buildTypesByName[variantRequirement.variant.buildType]
        ?: variantRequirement.buildTypeRequirement?.let { expectedBuildType ->
          // This case means there isn't a buildType direct match , and need to check the fallbacks.
          expectedBuildType.matchingFallbacks.firstNotNullOfOrNull { fallback -> androidProjectContext.buildTypesByName[fallback] }
        }
        ?: throw IllegalStateException(
          "Variant Conflict: Unresolved variant \"${variantRequirement.variant.name}\".\nCause: Could not resolve BuildTypes ambiguity for project: ${gradleProject.path}."
        )
    } else null

  // need to now create a variant out of this build type and productFlavors.
  return androidProjectContext.basicAndroidProject.variants.singleOrNull { variant ->
    buildTypeOrFallback?.let { variant.buildType != null && variant.buildType == it.name } == true &&
      dimensionsToFlavors.values.all { variant.productFlavors.contains(it.name) }
  } ?: throw IllegalStateException("Variant Conflict: Unable to find a variant to Sync for project: ${gradleProject.path}.")
}

/** Verifies if all variant attributes (BuildType and all DimensionsxProductFlavors) strictly match. */
private fun verifyAllVariantAttributesMatch(
  expectedVariantRequirement: VariantRequirement,
  currentVariant: BasicVariant,
  androidDsl: AndroidDsl,
): Boolean {
  if (currentVariant.productFlavors.size != expectedVariantRequirement.variant.productFlavors.size) return false
  if (currentVariant.buildType != expectedVariantRequirement.variant.buildType) return false
  val flavorsMatch = currentVariant.productFlavors.toSet() == expectedVariantRequirement.variant.productFlavors.toSet()
  val dimensionsMatch = androidDsl.flavorDimensions.toSet() == expectedVariantRequirement.flavorRequirements.keys.toSet()

  return dimensionsMatch && flavorsMatch
}

/** Verifies if all variant attributes (BuildType and all ProductFlavors) strictly match. */
private fun verifyAllVariantAttributesMatch(expectedVariant: BasicVariant, currentVariant: BasicVariant): Boolean {
  if (currentVariant.productFlavors.size != expectedVariant.productFlavors.size) return false
  if (currentVariant.buildType != expectedVariant.buildType) return false
  // Heuristics caveat: This is not the best matching as it misses comparing the flavors by their dims as well instead of the names only.
  val flavorsMatch = currentVariant.productFlavors.toSet() == expectedVariant.productFlavors.toSet()

  return flavorsMatch
}

private fun getVariantRequirementAttributesInformation(
  targetProjectId: ProjectBuildInfo,
  variant: BasicVariant,
  variantToPropagate: VariantRequirement?,
  androidProjectContext: AndroidProjectData,
  priority: Int,
): VariantRequirement {
  // 1. Determine BuildType Requirements.
  // If we already have a requirement from a consumer, propagate it directly without copying since the requirement model is immutable.
  val buildTypeRequirement =
    if (variantToPropagate?.buildTypeRequirement != null) {
      variantToPropagate.buildTypeRequirement
    } else if (variantToPropagate == null) {
      val buildType =
        androidProjectContext.buildTypesByName[variant.buildType]
          ?: throw IllegalStateException(
            "Variant Conflict: Unable to resolve BuildType attribute for " +
              "variant \"${variant.name}\" for project: ${targetProjectId.gradleProjectPath}."
          )
      val fallbacks =
        if (androidProjectContext.modelVersions[ModelFeature.HAS_MATCHING_FALLBACKS]) buildType.matchingFallbacks
        else androidProjectContext.legacyAndroidGradlePluginProperties?.buildTypesMatchingFallbacks[buildType.name] ?: emptyList()
      VariantBuildTypeAndFallbacks(buildType.name, variant.name, fallbacks, priority)
    } else null

  // 2. Determine Flavor Requirements. If we have a required variant from other consumer, then we prioritise it over local attributes.
  // We don't need to copy as this is immutable now.
  val flavorRequirements =
    if (variantToPropagate != null && variantToPropagate.flavorRequirements.isNotEmpty()) {
      variantToPropagate.flavorRequirements
    } else if (variantToPropagate == null) {
      androidProjectContext.androidDsl.productFlavors
        .filter { variant.productFlavors.contains(it.name) }
        .associate { flavor ->
          val fallbacks =
            if (androidProjectContext.modelVersions[ModelFeature.HAS_MATCHING_FALLBACKS]) flavor.matchingFallbacks
            else androidProjectContext.legacyAndroidGradlePluginProperties?.productFlavorsMatchingFallbacks[flavor.name] ?: emptyList()
          flavor.dimension!! to ProductFlavorsAndFallbacks(flavor.name, variant.name, fallbacks, priority)
        }
    } else emptyMap()

  // 3. Determine Strategies.
  val strategies =
    if (variantToPropagate != null && variantToPropagate.missingDimensionStrategies.isNotEmpty()) {
      variantToPropagate.missingDimensionStrategies
    } else if (variantToPropagate == null) {
      getMissingDimensionStrategyForCurrentProject(
        androidProjectContext.androidDsl,
        variant,
        androidProjectContext.modelVersions,
        androidProjectContext.legacyAndroidGradlePluginProperties,
        priority,
      )
    } else emptyMap()

  return VariantRequirement(variant, buildTypeRequirement, flavorRequirements, strategies, priority)
}

/**
 * Seeds and propagates variant requirements to downstream dependencies.
 *
 * This function ensures 'Intent Atomicity' by allowing high-priority paths (Switch Target) to overwrite existing variant expectations and
 * implements 'Transitive Tunneling' to bridge silent intermediate modules.
 */
private fun setUpExpectedVariantForDependantProjects(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectData,
  variantToSync: BasicVariant,
  variantToPropagate: VariantRequirement?,
  variantResolutionContext: VariantResolutionContext,
  currentProjectPriority: Int,
) {
  val currentProjectBuildInfo = ProjectBuildInfo(gradleProject.path, gradleProject.projectIdentifier.buildIdentifier.rootDir)

  // Calculate the requirement to propagate once for all outgoing dependencies.
  val currentRequirement =
    getVariantRequirementAttributesInformation(
      currentProjectBuildInfo,
      variantToSync,
      variantToPropagate,
      androidProjectContext,
      currentProjectPriority,
    )

  // Now we set the expectations for projects dependencies.
  androidProjectContext.declaredDependencies.allOutgoingProjectDependencies.forEach { dependencyPath ->
    val dependencyId = ProjectBuildInfo(dependencyPath, gradleProject.projectIdentifier.buildIdentifier.rootDir)
    // Higher priority consumer overrides existing variant expectation.
    val existingVariantRequirement = variantResolutionContext.projectVariantRequirements[dependencyId]
    if (existingVariantRequirement == null || currentProjectPriority < existingVariantRequirement.priority) {
      // Special case for Dynamic feature's dependencies (which should be APP only): we only propagate the variant name requirement because
      // We don't need to resolve the attributes fallbacks as the variant matching should be direct.
      if (androidProjectContext.ideAndroidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE)
        variantResolutionContext.projectVariantRequirements[dependencyId] =
          VariantRequirement(currentRequirement.variant, null, emptyMap(), emptyMap(), priority = currentRequirement.priority)
      else variantResolutionContext.projectVariantRequirements[dependencyId] = currentRequirement
    }
  }
}

/** Aggregates Missing Dimension Strategies from the defaultConfig and the current variant's ProductFlavors. */
private fun getMissingDimensionStrategyForCurrentProject(
  androidDsl: AndroidDsl,
  variantToSync: BasicVariant,
  modelVersions: ModelVersions,
  legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
  priority: Int,
): Map<String, MissingDimensionStrategies> {
  val missingDimensionStrategies = mutableMapOf<String, MissingDimensionStrategies>()
  if (modelVersions[ModelFeature.HAS_MISSING_DIMENSION_STRATEGY]) {
    androidDsl.defaultConfig.missingDimensionStrategy.forEach { (dimension, fallbacks) ->
      missingDimensionStrategies[dimension] = MissingDimensionStrategies(fallbacks, priority = priority)
    }
  }

  // Then go through strategies from the productFlavors
  androidDsl.productFlavors
    .filter { variantToSync.productFlavors.contains(it.name) }
    .forEach { flavor ->
      // Get the missingDimensionStrategy.
      val flavorStrategies =
        if (modelVersions[ModelFeature.HAS_MISSING_DIMENSION_STRATEGY]) flavor.missingDimensionStrategy
        else legacyAndroidGradlePluginProperties?.missingDimensionStrategies[flavor.name] ?: emptyMap()

      flavorStrategies.forEach { (dimension, fallbacks) ->
        // If there are already matching strategies for this dimension defined by the defaultConfig, or if there is no strategy defined yet,
        // create one.
        if (missingDimensionStrategies[dimension]?.overridden != true) {
          missingDimensionStrategies[dimension] = MissingDimensionStrategies(fallbacks, true, priority)
        }
      }
    }
  return missingDimensionStrategies.toImmutableMap()
}

data class VariantBuildTypeAndFallbacks(val buildType: String, val variant: String, val matchingFallbacks: List<String>, val priority: Int)

data class ProductFlavorsAndFallbacks(
  val productFlavor: String,
  val variant: String,
  val matchingFallbacks: List<String>,
  val priority: Int,
)

data class ProjectBuildInfo(val gradleProjectPath: String, val rootBuildPath: File)

data class MissingDimensionStrategies(
  val requestedFlavors: List<String>,
  val overridden: Boolean = false,
  val priority: Int = Int.MAX_VALUE,
)

data class VariantAndPriority(val variant: BasicVariant, val priority: Int)

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
import com.android.ide.gradle.model.dependencies.DeclaredDependenciesModelBuilder
import com.android.tools.idea.gradle.model.IdeBasicVariantName
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantNameImpl
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlinx.collections.immutable.toImmutableMap
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

private const val PRIORITY_USER_REQUEST = 0
private const val PRIORITY_APP_MODULE = 1
private const val PRIORITY_DEFAULT = 2

/**
 * A util Data Adapter class to bridge different project Sync models (phased-sync and legacy-sync) inputs to the variant resolution engine.
 */
class AndroidProjectDataMappingNode(
  val modelVersions: ModelVersions,
  val basicAndroidProject: BasicAndroidProject,
  val androidProject: AndroidProject?,
  val androidDsl: AndroidDsl,
  val declaredDependencies: DeclaredDependencies?,
  var selectedVariantName: String?,
  val legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
  val moduleId: String,
  var isSeen: Boolean = false,
) {
  // Only instantiate these if we need to use attributes resolution disambiguation logic.
  val productFlavorsByDimensionAndName: Map<Pair<String, String>, ProductFlavor> by
    lazy(LazyThreadSafetyMode.PUBLICATION) { androidDsl.productFlavors.associateBy { (it.dimension ?: "") to it.name } }

  val productFlavorsByName: Map<String, ProductFlavor> by
    lazy(LazyThreadSafetyMode.PUBLICATION) { androidDsl.productFlavors.associateBy { it.name } }

  val buildTypesByName: Map<String, BuildType> by lazy(LazyThreadSafetyMode.PUBLICATION) { androidDsl.buildTypes.associateBy { it.name } }
}

data class AndroidProjectData(
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
)

/** Encapsulates the state of variant resolution across all projects during a sync phase. */
class VariantResolutionContext {
  /** Map of project identity (moduleId()) to the variant requirements pushed from its consumers. */
  val projectVariantRequirements = mutableMapOf<String, VariantRequirement>()

  /** Map of project identity to the final variant selection (Registry of Outcomes). */
  val projectToSelectedVariants = mutableMapOf<String, VariantAndPriority>()
}

/** The group of requirements defining a project's variant resolution. */
data class VariantRequirement(
  /** The expected variant */
  val variant: BasicVariant,
  /** The expected buildType details including the fallbacks. */
  val buildTypeRequirement: VariantAttributeAndFallbacks? = null,
  /** The expected productFlavors requirements and their fallbacks. */
  val flavorRequirements: Map<String, VariantAttributeAndFallbacks> = emptyMap(),
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
 * @param projectsData The list of projects and their associated [AndroidProjectData].
 * @param syncOptions that contains the variant switching request (if it exists).
 * @param modelConsumer to report the resolved variant models back to the IDE.
 * @param cachedModels A cache for storing resolved data to be used by other model builders across different sync phases.
 * @param variantsResolutionIssues A map to collect any exceptions that occurred during variant resolution for each project.
 */
fun setupProjectsVariantsAndConsume(
  projectsData: List<Pair<BasicGradleProject, AndroidProjectData>>,
  syncOptions: SyncActionOptions,
  modelConsumer: ProjectImportModelProvider.GradleModelConsumer?,
  cachedModels: ModelProviderCachedData?,
  variantsResolutionIssues: MutableMap<BasicGradleProject, Throwable>,
) {
  val projectNodesRegistry = projectsData.map { (gradleProject, androidData) ->
    gradleProject to
      AndroidProjectDataMappingNode(
        modelVersions = androidData.modelVersions,
        basicAndroidProject = androidData.basicAndroidProject,
        androidProject = androidData.androidProject,
        androidDsl = androidData.androidDsl,
        declaredDependencies = androidData.declaredDependencies,
        selectedVariantName = androidData.selectedVariantName,
        legacyAndroidGradlePluginProperties = androidData.legacyAndroidGradlePluginProperties,
        moduleId = gradleProject.moduleId(),
      )
  }

  // Now sort the projects based on their priority criteria (projectType + number of incoming dependencies).
  val projectsWithPriority = sortProjectsByPriority(projectNodesRegistry, syncOptions)
  val resultsForVariants = resolveVariantsCore(projectsWithPriority, variantsResolutionIssues)

  projectsData.forEach { (gradleProject, androidProjectContext) ->
    // Update the selected variant for this project if there were no exceptions thrown: this is important because this data is
    // passed through to other model builders.
    resultsForVariants[gradleProject]?.let { androidProjectContext.selectedVariantName = it }

    val variantNameModel = IdeBasicVariantNameImpl(androidProjectContext.selectedVariantName)
    modelConsumer?.consumeProjectModel(gradleProject, variantNameModel, IdeBasicVariantName::class.java)
    if (cachedModels != null) {
      // Set the cachedData variant value.
      cachedModels.data[gradleProject] =
        CachedAndroidProjectData(
          androidProjectContext.modelVersions,
          androidProjectContext.selectedVariantName,
          androidProjectContext.ideAndroidProject,
          androidProjectContext.shouldSkipRuntimeClassPathForLibraries,
          androidProjectContext.declaredDependencies.allOutgoingProjectsDependenciesToConfigurations.keys.toList(),
        )
    }
  }
}

fun resolveVariantsForRegularSync(
  projectsData: List<Pair<BasicGradleProject, Pair<AndroidModule, String?>>>,
  syncOptions: SyncActionOptions,
  syncIssues: MutableMap<BasicGradleProject, Throwable>,
): Map<AndroidModule, String> {
  val modulesToVariants = mutableMapOf<AndroidModule, String>()

  val projectNodesRegistry = projectsData.map { (gradleProject, data) ->
    val (module, variant) = data
    gradleProject to
      AndroidProjectDataMappingNode(
        modelVersions = module.modelVersions,
        basicAndroidProject = checkNotNull(module.basicAndroidProject),
        androidProject = module.androidProjectV2,
        androidDsl = checkNotNull(module.androidDsl),
        declaredDependencies = module.declaredDependencies,
        selectedVariantName = variant,
        legacyAndroidGradlePluginProperties = module.legacyAndroidGradlePluginProperties,
        moduleId = gradleProject.moduleId(),
      )
  }

  val projectsWithPriority = sortProjectsByPriority(projectNodesRegistry, syncOptions)

  val resultsForVariants = resolveVariantsCore(projectsWithPriority, syncIssues)

  // Update the selected variant for the module configurations.
  projectsData.forEach { (gradleProject, projectData) ->
    resultsForVariants[gradleProject]?.let { modulesToVariants[projectData.first] = it }
  }

  return modulesToVariants
}

/** Generic variant resolution entry point that can be used by both Phased Sync and Standard Sync. */
private fun resolveVariantsCore(
  projectsWithPriority: Map<Int, List<Pair<BasicGradleProject, AndroidProjectDataMappingNode>>>,
  variantsResolutionIssues: MutableMap<BasicGradleProject, Throwable>,
): Map<BasicGradleProject, String?> {
  val projectsAndVariant = mutableMapOf<BasicGradleProject, String?>()
  val variantResolutionContext = VariantResolutionContext()

  // Resolve the variants at this stage handling each level of priority at a time, and considering the declared project dependencies.
  projectsWithPriority.forEach { (priority, nextBatch) ->
    nextBatch.forEach { (gradleProject, androidProjectContext) ->
      if (androidProjectContext.isSeen) return@forEach

      var selectedVariantName = androidProjectContext.selectedVariantName
      try {
        androidProjectContext.isSeen = true
        if (selectedVariantName == null) throw IllegalStateException("No variants found for ${gradleProject.path}.")
        selectedVariantName = getSelectedVariantName(gradleProject, androidProjectContext, variantResolutionContext, priority)
      } catch (e: Exception) {
        variantsResolutionIssues[gradleProject] = e
      } finally {
        projectsAndVariant[gradleProject] = selectedVariantName
      }
    }
  }

  return projectsAndVariant
}

/**
 * Returns the priority value for a given project.
 *
 * Priority 0: The project specifically requested for variant switching (highest intent). Priority 1: App modules (primary entry points for
 * the graph). Priority 2: All other modules.
 */
fun getPriorityValue(moduleId: String, syncOptions: SyncActionOptions, projectType: ProjectType) =
  when {
    // The project for which we are changing the selected variant has the highest priority.
    moduleId == (syncOptions as? SingleVariantSyncActionOptions)?.switchVariantRequest?.moduleId -> PRIORITY_USER_REQUEST
    // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
    // The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
    // dependencies of others and will be visited sooner and the configurations created below will be discarded without being fetched.
    projectType == ProjectType.APPLICATION -> PRIORITY_APP_MODULE
    // The rest of the projects are treated as similar priority.
    else -> PRIORITY_DEFAULT
  }

/**
 * Groups projects into topological layers (batches) to ensure consumers resolve before producers.
 *
 * This ensures that when a module is resolved, all its consumers have already finished and contributed their resolution requirements
 * (strategies and fallbacks) to the global maps.
 */
fun sortProjectsByPriority(
  projectsNodes: List<Pair<BasicGradleProject, AndroidProjectDataMappingNode>>,
  syncOptions: SyncActionOptions,
): Map<Int, List<Pair<BasicGradleProject, AndroidProjectDataMappingNode>>> {
  val projectNodesByGradlePaths = projectsNodes.associateBy { it.second.moduleId }

  // 1. Build the mapping of each project to the # of incoming dependencies.
  val inWeight = mutableMapOf<String, Int>()
  // Helpers to classify these weighted projects into batches for layers processing.
  val headProjectsToBatch = mutableMapOf<String, Int>() // This will get fed Apps, and Libraries with no incoming dependencies.
  val queue = ArrayDeque<String>()

  val filteredDependencies = mutableMapOf<String, MutableList<String>>()
  projectsNodes.forEach { (gradleProject, node) ->
    node.declaredDependencies?.allOutgoingProjectsDependenciesToConfigurations?.forEach { (dependencyProject, configuration) ->
      val producerModuleId = Modules.createUniqueModuleId(gradleProject.projectIdentifier.buildIdentifier.rootDir, dependencyProject)
      val producerNode = projectNodesByGradlePaths[producerModuleId]?.second ?: return@forEach
      // Dynamic feature project dependencies are special case and are not represented in the project dependencies configuration as a
      // configuration of interest, but we do want to propagate variants to them regardless of this.
      val isDynamicFeature = producerNode.basicAndroidProject.projectType == ProjectType.DYNAMIC_FEATURE
      // For the rest of the projects, we only care about variant propagation if the dependency configuration has an impact on the compile
      // classpath.
      // TODO(b/467047467): figure out list of actual configurations that we care about here.
      val isInterestingConfig = configuration.any { DeclaredDependenciesModelBuilder.CONFIGURATIONS_OF_INTEREST.contains(it) }
      // Only ignore edges targeting an APP module to prevent Graph Inversion.
      // This preserves dependencies on libraries (e.g. Test -> SharedLib) while ensuring Apps resolve first.
      val isTargetingApp = producerNode.basicAndroidProject.projectType == ProjectType.APPLICATION

      // Do the filtering for the projects that we won't handle in prioritisation and variant propagation logic,
      // so we save the cost of doing the processing later when sorting.
      if ((isDynamicFeature || isInterestingConfig) && !isTargetingApp) {
        filteredDependencies.getOrPut(node.moduleId) { mutableListOf() }.add(producerModuleId)
        inWeight[producerModuleId] = (inWeight[producerModuleId] ?: 0) + 1
      }
    }

    // 1.1 Seed initial entry points (Priorities 0 and 1) and handle libraries without consumers.
    val priority = getPriorityValue(node.moduleId, syncOptions, node.basicAndroidProject.projectType)
    when {
      priority == PRIORITY_USER_REQUEST -> {
        headProjectsToBatch[node.moduleId] = 0
        queue.addFirst(node.moduleId)
      }
      priority == PRIORITY_APP_MODULE && !headProjectsToBatch.containsKey(node.moduleId) -> {
        headProjectsToBatch[node.moduleId] = 1
        queue.addLast(node.moduleId)
      }
    }
  }

  // 2.2  Handle libraries that do not have consumers. This way we are sure these are always added last to the deque so they get processed
  // after Apps.
  projectsNodes.forEach { (_, projectNode) ->
    if (!inWeight.containsKey(projectNode.moduleId) && !headProjectsToBatch.containsKey(projectNode.moduleId)) {
      headProjectsToBatch[projectNode.moduleId] = 2
      queue.addLast(projectNode.moduleId)
    }
  }

  // 3. Propagation using transitive dependencies.
  val switchId = (syncOptions as? SingleVariantSyncActionOptions)?.switchVariantRequest?.moduleId
  while (queue.isNotEmpty()) {
    val consumerPath = queue.removeFirst()
    val consumerBatch = headProjectsToBatch[consumerPath]!!
    val node = projectNodesByGradlePaths[consumerPath] ?: continue

    filteredDependencies[node.second.moduleId]?.forEach { producerModuleId ->
      val producerNode = projectNodesByGradlePaths[producerModuleId] ?: return@forEach
      // Skip edges to the switch target (already pinned)
      if (producerNode.second.moduleId == switchId) return@forEach

      // Each time we go through a consumer project, we decrease the amount of incoming deps (handled) for this project.
      // Once all it's consumers have been handled (weight = 0), then we can handle this project (i.e. add it to the queue for processing)
      inWeight[producerModuleId] = inWeight.getValue(producerModuleId) - 1

      // Retrieve the current batch level assigned to this producer module.
      val currentBatch = headProjectsToBatch[producerModuleId] ?: -1
      // A producer must be resolved strictly AFTER its latest consumer (consumerBatch + 1).
      // If we find a path that requires a later batch level, we update the level and re-enqueue
      // the producer to transitively shift downstream library dependencies further down.
      if (inWeight.getValue(producerModuleId) <= 0 && consumerBatch + 1 > currentBatch) {
        headProjectsToBatch[producerModuleId] = consumerBatch + 1
        queue.addLast(producerModuleId)
      }
    }
  }

  return projectsNodes.groupBy(keySelector = { headProjectsToBatch[it.second.moduleId] ?: 1000 }).toSortedMap()
}

/**
 * Resolves the selected variant name for a module during Phased Sync.
 *
 * This function applies a hierarchical matching order (Direct Match -> Attributes -> Fallbacks -> Strategies) to ensure that the IDE
 * selects a stable and functional variant that satisfies all its consumers.
 */
private fun getSelectedVariantName(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectDataMappingNode,
  variantResolutionContext: VariantResolutionContext,
  priority: Int,
): String {

  val currentProjectId = gradleProject.moduleId()
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

  return variantToSync.name
}

private fun resolveVariantByCase(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectDataMappingNode,
  variantResolutionContext: VariantResolutionContext,
  priority: Int,
): VariantAndPriority {
  val variantRequirement = variantResolutionContext.projectVariantRequirements[gradleProject.moduleId()]
  val basicAndroidProject = androidProjectContext.basicAndroidProject

  return when (basicAndroidProject.projectType) {
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
  androidProjectContext: AndroidProjectDataMappingNode,
  priority: Int,
): VariantAndPriority {
  val variants = androidProjectContext.basicAndroidProject.variants
  val variant =
    variants.firstOrNull { it.name == androidProjectContext.selectedVariantName }
      ?: throw IllegalStateException(
        "Variant Conflict: Unable to find variant \"${androidProjectContext.selectedVariantName}\" to Sync for project: ${gradleProject.path}."
      )
  return VariantAndPriority(variant, priority)
}

private fun resolveTestProjectVariant(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectDataMappingNode,
  variantResolutionContext: VariantResolutionContext,
  priority: Int,
): VariantAndPriority {
  val androidProject =
    androidProjectContext.androidProject ?: return resolveIndependentVariant(gradleProject, androidProjectContext, priority)
  val basicAndroidProject = androidProjectContext.basicAndroidProject

  val targetAppPath = androidProject.variants.firstOrNull { it.testedTargetVariant != null }?.testedTargetVariant?.targetProjectPath
  // The target app will have the same Build identifier as the test Project.
  val targetAppModuleId = targetAppPath?.let { Modules.createUniqueModuleId(gradleProject.projectIdentifier.buildIdentifier.rootDir, it) }
  val targetAppVariant = targetAppModuleId?.let { variantResolutionContext.projectToSelectedVariants[it] }

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
    basicAndroidProject.variants.firstOrNull { it.name == androidProjectContext.selectedVariantName }
      // Only throw if there is no variant at all to sync for This project.
      ?: throw IllegalStateException(
        "Variant Conflict: Unable to find variant \"${androidProjectContext.selectedVariantName}\" to Sync for project: ${gradleProject.path}."
      )

  return VariantAndPriority(variant, priority)
}

private fun resolveDynamicFeatureStrictVariantMatching(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectDataMappingNode,
  variantRequirement: VariantRequirement?,
  priority: Int,
): VariantAndPriority {
  val basicAndroidProject = androidProjectContext.basicAndroidProject
  if (variantRequirement != null) {
    val variant = basicAndroidProject.variants.firstOrNull { it.name == variantRequirement.variant.name }
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
      basicAndroidProject.variants.firstOrNull { it.name == androidProjectContext.selectedVariantName }
        ?: throw IllegalStateException(
          "Variant conflict: Unable to find variant " +
            "\"${androidProjectContext.selectedVariantName}\" to Sync for project: ${gradleProject.path}."
        )
    return VariantAndPriority(variant, priority)
  }
}

private fun resolveApplicationVariantMatching(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectDataMappingNode,
  variantRequirement: VariantRequirement?,
  priority: Int,
): VariantAndPriority {
  val basicAndroidProject = androidProjectContext.basicAndroidProject
  if (variantRequirement != null) {
    val variant = basicAndroidProject.variants.firstOrNull { it.name == variantRequirement.variant.name }
    return if (variant != null) {
      // Verify the matching is not by name, but also by the attributes.
      if (verifyAllVariantAttributesMatch(variantRequirement, variant, androidProjectContext.androidDsl))
        VariantAndPriority(variant, variantRequirement.priority)
      else
        throw IllegalStateException(
          "Variant conflict: Unable to find variant \"${variantRequirement.variant.name}\" to Sync for project: ${gradleProject.path}."
        )
    } else {
      // we couldn't find a direct match so use the attribute matching like a library.
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
  androidProjectContext: AndroidProjectDataMappingNode,
  variantRequirement: VariantRequirement,
): VariantAndPriority {
  val basicAndroidProject = androidProjectContext.basicAndroidProject
  val androidDsl = androidProjectContext.androidDsl

  val variantObject = basicAndroidProject.variants.firstOrNull { it.name == variantRequirement.variant.name }
  val variant =
    if (variantObject != null && verifyAllVariantAttributesMatch(variantRequirement, variantObject, androidDsl)) {
      variantObject
    } else {
      resolveVariantAttributes(gradleProject, androidProjectContext, variantRequirement)
    }
  return VariantAndPriority(variant, variantRequirement.priority)
}

/** Reconciles variant attributes (BuildType and ProductFlavors) to find a best-fit variant. */
private fun resolveVariantAttributes(
  gradleProject: BasicGradleProject,
  androidProjectContext: AndroidProjectDataMappingNode,
  variantRequirement: VariantRequirement,
): BasicVariant {
  val basicAndroidProject = androidProjectContext.basicAndroidProject
  val androidDsl = androidProjectContext.androidDsl

  val dimensionsToFlavors = mutableMapOf<String, ProductFlavor>()

  androidDsl.flavorDimensions.forEach { dimension ->
    // 1. multiple flavors: they either need to match as 1 to one mapping or specify fallbacks if they don't match.
    if (variantRequirement.flavorRequirements.containsKey(dimension)) {
      val flavorRequirement = variantRequirement.flavorRequirements[dimension]!!
      dimensionsToFlavors[dimension] =
        androidProjectContext.productFlavorsByDimensionAndName[dimension to flavorRequirement.attributeName]
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
    else if (androidDsl.productFlavors.singleOrNull { it.dimension == dimension } != null)
      dimensionsToFlavors[dimension] = androidDsl.productFlavors.single { it.dimension == dimension }
    else {
      throw IllegalStateException(
        "Variant Conflict: Unresolved variant \"${variantRequirement.variant.name}\".\nCause: Could not resolve ProductFlavors ambiguity for project: ${gradleProject.path}."
      )
    }
  }

  // Determine the BuildType for this project's variant.
  if (variantRequirement.buildTypeRequirement == null && androidDsl.buildTypes.isNotEmpty())
    throw IllegalStateException(
      "Variant Conflict: Unresolved variant \"${variantRequirement.variant.name}\".\nCause: Could not resolve BuildTypes ambiguity for project: ${gradleProject.path}."
    )

  val buildTypeOrFallback =
    if (androidDsl.buildTypes.isNotEmpty()) {
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
  return basicAndroidProject.variants.singleOrNull { variant ->
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
  targetProject: BasicGradleProject,
  variant: BasicVariant,
  variantToPropagate: VariantRequirement?,
  androidProjectContext: AndroidProjectDataMappingNode,
  priority: Int,
): VariantRequirement {
  val dsl = androidProjectContext.androidDsl
  val modelVersions = androidProjectContext.modelVersions
  val legacyProps = androidProjectContext.legacyAndroidGradlePluginProperties

  // 1. Determine BuildType Requirements
  // If we already have a requirement from a consumer, propagate it directly without copying since the requirement model is immutable.
  val buildTypeRequirement =
    if (variantToPropagate?.buildTypeRequirement != null) {
      variantToPropagate.buildTypeRequirement
    } else if (variantToPropagate == null) {
      val buildType =
        androidProjectContext.buildTypesByName[variant.buildType]
          ?: throw IllegalStateException(
            "Variant Conflict: Unable to resolve BuildType attribute for " +
              "variant \"${variant.name}\" for project: ${targetProject.path}."
          )
      val fallbacks =
        if (modelVersions[ModelFeature.HAS_MATCHING_FALLBACKS]) buildType.matchingFallbacks
        else legacyProps?.buildTypesMatchingFallbacks?.get(buildType.name) ?: emptyList()
      VariantAttributeAndFallbacks(buildType.name, variant.name, fallbacks, priority)
    } else null

  // 2. Determine Flavor Requirements. If we have a required variant from other consumer, then we prioritise it over local attributes.
  // We don't need to copy as this is immutable now.
  val flavorRequirements =
    if (variantToPropagate != null && variantToPropagate.flavorRequirements.isNotEmpty()) {
      variantToPropagate.flavorRequirements
    } else if (variantToPropagate == null) {
      dsl.productFlavors
        .filter { variant.productFlavors.contains(it.name) }
        .associate { flavor ->
          val fallbacks =
            if (modelVersions[ModelFeature.HAS_MATCHING_FALLBACKS]) flavor.matchingFallbacks
            else legacyProps?.productFlavorsMatchingFallbacks?.get(flavor.name) ?: emptyList()
          flavor.dimension!! to VariantAttributeAndFallbacks(flavor.name, variant.name, fallbacks, priority)
        }
    } else emptyMap()

  // 3. Determine Strategies.
  val strategies =
    if (variantToPropagate != null && variantToPropagate.missingDimensionStrategies.isNotEmpty()) {
      variantToPropagate.missingDimensionStrategies
    } else if (variantToPropagate == null) {
      getMissingDimensionStrategyForCurrentProject(dsl, variant, modelVersions, legacyProps, priority)
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
  androidProjectContext: AndroidProjectDataMappingNode,
  variantToSync: BasicVariant,
  variantToPropagate: VariantRequirement?,
  variantResolutionContext: VariantResolutionContext,
  currentProjectPriority: Int,
) {
  val declaredDependencies = androidProjectContext.declaredDependencies ?: return

  // Calculate the requirement to propagate once for all outgoing dependencies.
  val currentRequirement =
    getVariantRequirementAttributesInformation(
      gradleProject,
      variantToSync,
      variantToPropagate,
      androidProjectContext,
      currentProjectPriority,
    )

  // Now we set the expectations for projects dependencies.
  declaredDependencies.allOutgoingProjectsDependenciesToConfigurations.forEach { (dependency, configurations) ->
    // TODO(b/467047467): figure out list of actual configurations that we care about here.
    if (
      configurations.any { DeclaredDependenciesModelBuilder.CONFIGURATIONS_OF_INTEREST.contains(it) } ||
        androidProjectContext.androidProject?.dynamicFeatures?.contains(dependency) == true
    ) {
      // The outgoing project dependencies represent those within the same Build, so we can use the given Gradle project's build ID for the
      // dependencies.
      val dependencyId = Modules.createUniqueModuleId(gradleProject.projectIdentifier.buildIdentifier.rootDir.path, dependency)
      val existingVariantRequirement = variantResolutionContext.projectVariantRequirements[dependencyId]
      // Higher priority consumer overrides existing variant expectation.
      if (existingVariantRequirement == null || currentProjectPriority < existingVariantRequirement.priority) {
        variantResolutionContext.projectVariantRequirements[dependencyId] = currentRequirement
      }
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
  val strategies = mutableMapOf<String, MissingDimensionStrategies>()
  if (modelVersions[ModelFeature.HAS_MISSING_DIMENSION_STRATEGY]) {
    androidDsl.defaultConfig.missingDimensionStrategy.forEach { (dimension, fallbacks) ->
      strategies[dimension] = MissingDimensionStrategies(fallbacks, priority = priority)
    }
  }

  // Then go through strategies from the productFlavors
  androidDsl.productFlavors
    .filter { variantToSync.productFlavors.contains(it.name) }
    .forEach { flavor ->
      // Get the missingDimensionStrategy.
      val flavorStrategies =
        if (modelVersions[ModelFeature.HAS_MISSING_DIMENSION_STRATEGY]) flavor.missingDimensionStrategy
        else legacyAndroidGradlePluginProperties?.missingDimensionStrategies?.get(flavor.name) ?: emptyMap()

      flavorStrategies.forEach { (dimension, fallbacks) ->
        // If there are already matching strategies for this dimension defined by the defaultConfig, or if there is no strategy defined yet,
        // create one.
        if (strategies[dimension]?.overridden != true) {
          strategies[dimension] = MissingDimensionStrategies(fallbacks, overridden = true, priority = priority)
        }
      }
    }
  return strategies.toImmutableMap()
}

/**
 * Represents the information of either a BuildType or ProductFlavor for a given variant.
 *
 * @param attributeName buildType/ productFlavor name.
 * @param variant the expected variant's name.
 * @param matchingFallbacks the list of matching fallbacks in priority order.
 * @param priority the priority for the variant's requirement. This is used in case we have different variants requirements from different
 *   consumers, and we need to pick the highest priority variant.
 */
data class VariantAttributeAndFallbacks(
  val attributeName: String,
  val variant: String,
  val matchingFallbacks: List<String>,
  val priority: Int,
)

data class MissingDimensionStrategies(
  val requestedFlavors: List<String>,
  val overridden: Boolean = false,
  val priority: Int = Int.MAX_VALUE,
)

data class VariantAndPriority(val variant: BasicVariant, val priority: Int)

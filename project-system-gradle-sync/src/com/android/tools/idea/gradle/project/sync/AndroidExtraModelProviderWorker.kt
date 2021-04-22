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
package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.AndroidProject
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.ProjectSyncIssues
import com.android.builder.model.SyncIssue
import com.android.builder.model.Variant
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.utils.appendCapitalized
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.gradle.KotlinGradleModel
import org.jetbrains.kotlin.gradle.KotlinMPPGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.util.LinkedList

internal class AndroidExtraModelProviderWorker(
  controller: BuildController,
  private val syncOptions: SyncActionOptions,
  private val buildModels: List<GradleBuild>, // Always not empty.
  private val consumer: ProjectImportModelProvider.BuildModelConsumer
) {
  private val androidModulesById: MutableMap<String, AndroidModule> = HashMap()
  private val buildFolderPaths = ModelConverter.populateModuleBuildDirs(
    controller)
  private val actionRunner = createActionRunner(controller, syncOptions.flags)
  private val modelCache = ModelCache.create(buildFolderPaths)

  fun populateBuildModels() {
    try {
      val modules: List<GradleModule> =
        when (syncOptions) {
          is SyncProjectActionOptions -> {
            populateAndroidModels(syncOptions)
          }
          is NativeVariantsSyncActionOptions -> {
            consumer.consume(
              buildModels.first(),
              actionRunner.runAction { controller -> controller.getModel(IdeaProject::class.java) },
              IdeaProject::class.java
            )
            fetchNativeVariantsAndroidModels(syncOptions, buildFolderPaths)
          }
          // Note: No more cases.
        }
      // Requesting ProjectSyncIssues must be performed "last" since all other model requests may produces additional issues.
      // Note that "last" here means last among Android models since many non-Android models are requested after this point.
      populateProjectSyncIssues(modules)
      modules.forEach { it.deliverModels(consumer) }
    }
    catch (e: AndroidSyncException) {
      consumer.consume(
        buildModels.first(),
        IdeAndroidSyncError(e.message.orEmpty(), e.stackTrace.map { it.toString() }),
        IdeAndroidSyncError::class.java
      )
    }
  }

  /**
   * Requests Android project models for the given [buildModels]
   *
   * We do this by going through each module and query Gradle for the following models:
   *   1. Query for the AndroidProject for the module
   *   2. Query for the NativeModule (V2 API) (only if we also obtain an AndroidProject)
   *   3. Query for the NativeAndroidProject (V1 API) (only if we can obtain AndroidProject but cannot obtain V2 NativeModule)
   *   4. Query for the GlobalLibraryMap for the module (we ALWAYS do this regardless of the other two models)
   *   5. (Single Variant Sync only) Work out which variant for which models we need to request, and request them.
   *      See IdeaSelectedVariantChooser for more details.
   *
   * If single variant sync is enabled then [findParameterizedAndroidModel] will use Gradle parameterized model builder API
   * in order to stop Gradle from building the variant.
   * All of the requested models are registered back to the external project system via the
   * [ProjectImportModelProvider.BuildModelConsumer] callback.
   */
  private fun populateAndroidModels(syncOptions: SyncProjectActionOptions): List<GradleModule> {
    val isFullSync = when (syncOptions) {
      is FullSyncActionOptions -> true
      is SingleVariantSyncActionOptions -> false
      // Note: No other cases.
    }
    val modules: List<GradleModule> = actionRunner.runActions(
      buildModels.projects.map { gradleProject ->
        fun(controller: BuildController): GradleModule {
          val androidProject = controller.findParameterizedAndroidModel(
            gradleProject,
            AndroidProject::class.java,
            shouldBuildVariant = isFullSync
          )
          if (androidProject != null) {
            // TODO(solodkyy): Perhaps request the version interface depending on AGP version.
            val nativeModule = controller.getNativeModuleFromGradle(gradleProject, syncAllVariantsAndAbis = isFullSync)
            val nativeAndroidProject: NativeAndroidProject? =
              if (nativeModule == null)
                controller.findParameterizedAndroidModel(
                  gradleProject,
                  NativeAndroidProject::class.java,
                  shouldBuildVariant = isFullSync
                )
              else null

            return createAndroidModule(
              gradleProject,
              androidProject,
              nativeAndroidProject,
              nativeModule,
              modelCache
            )
          }
          val kotlinGradleModel = controller.findModel(gradleProject, KotlinGradleModel::class.java)
          return JavaModule(gradleProject, kotlinGradleModel)
        }
      }.toList()
    )

    modules.filterIsInstance<AndroidModule>().forEach { androidModulesById[it.id] = it }

    if (syncOptions is SingleVariantSyncActionOptions) {
      // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
      // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
      // e.g the ones that should be selected by the IDE.
      chooseSelectedVariants(modules.filterIsInstance<AndroidModule>(), syncOptions)
    }

    // AdditionalClassifierArtifactsModel must be requested after AndroidProject and Variant model since it requires the library list in dependency model.
    getAdditionalClassifierArtifactsModel(
      actionRunner,
      modules.filterIsInstance<AndroidModule>(),
      syncOptions.additionalClassifierArtifactsAction.cachedLibraries,
      syncOptions.additionalClassifierArtifactsAction.downloadAndroidxUISamplesSources
    )

    return modules
  }

  private fun fetchNativeVariantsAndroidModels(
    syncOptions: NativeVariantsSyncActionOptions,
    buildFolderPaths: BuildFolderPaths
  ): List<GradleModule> {
    val modelCache = ModelCache.create(buildFolderPaths)
    return actionRunner.runActions(
      buildModels.projects.map { gradleProject ->
        fun(controller: BuildController): GradleModule? {
          val projectIdentifier = gradleProject.projectIdentifier
          val moduleId = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, projectIdentifier.projectPath)
          val variantName = syncOptions.moduleVariants[moduleId] ?: return null

          fun tryV2(): NativeVariantsAndroidModule? {
            controller.findModel(gradleProject, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
              it.variantsToGenerateBuildInformation = listOf(variantName)
              it.abisToGenerateBuildInformation = syncOptions.requestedAbis.toList()
            } ?: return null
            return NativeVariantsAndroidModule.createV2(gradleProject)
          }

          fun fetchV1Abi(abi: String): IdeNativeVariantAbi? {
            val model = controller.findModel(
              gradleProject,
              NativeVariantAbi::class.java,
              ModelBuilderParameter::class.java
            ) { parameter ->
              parameter.setVariantName(variantName)
              parameter.setAbiName(abi)
            } ?: return null
            return modelCache.nativeVariantAbiFrom(model)
          }

          fun tryV1(): NativeVariantsAndroidModule {
            return NativeVariantsAndroidModule.createV1(gradleProject, syncOptions.requestedAbis.mapNotNull { abi -> fetchV1Abi(abi) })
          }

          return tryV2() ?: tryV1()
        }
      }.toList()
    )
      .filterNotNull()
  }

  private fun populateProjectSyncIssues(androidModules: List<GradleModule>) {
    actionRunner.runActions(
      androidModules.map { module ->
        fun(controller: BuildController) {
          val syncIssues = controller.findModel(module.findModelRoot, ProjectSyncIssues::class.java)
          if (syncIssues != null) {
            // It is FINE to assign on the worker thread since we operate at one module at a time and the same AndroidModule instance
            // cannot accessed from multiple worker threads.
            module.setSyncIssues(syncIssues.syncIssues.toSyncIssueData())
          }
        }
      }
    )
  }

  /**
   * Gets the [AndroidProject] or [NativeAndroidProject] (based on [modelType]) for the given [BasicGradleProject].
   */
  private fun <T> BuildController.findParameterizedAndroidModel(
    project: BasicGradleProject,
    modelType: Class<T>,
    shouldBuildVariant: Boolean
  ): T? {
    if (!shouldBuildVariant) {
      try {
        val model = getModel(project, modelType, ModelBuilderParameter::class.java) { parameter ->
          parameter.shouldBuildVariant = false
        }
        if (model != null) return model
      }
      catch (e: UnsupportedVersionException) {
        // Using old version of Gradle. Fall back to full variants sync for this module.
      }
    }
    return findModel(project, modelType)
  }

  private fun BuildController.getNativeModuleFromGradle(project: BasicGradleProject, syncAllVariantsAndAbis: Boolean): NativeModule? {
    return try {
      if (!syncAllVariantsAndAbis) {
        // With single variant mode, we first only collect basic project information. The more complex information will be collected later
        // for the selected variant and ABI.
        getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
          it.variantsToGenerateBuildInformation = emptyList()
          it.abisToGenerateBuildInformation = emptyList()
        }
      }
      else {
        // If single variant is not enabled, we sync all variant and ABIs at once.
        getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
          it.variantsToGenerateBuildInformation = null
          it.abisToGenerateBuildInformation = null
        }
      }
    }
    catch (e: UnsupportedVersionException) {
      // Using old version of Gradle that does not support V2 models.
      null
    }
  }

  private fun BuildController.isKotlinMppProject(root: Model) = findModel(root, KotlinMPPGradleModel::class.java) != null

  private fun BuildController.findKotlinGradleModelForAndroidProject(root: Model, variantName: String): KotlinGradleModel? {
    // Do not apply single-variant sync optimization to Kotlin multi-platform projects. We do not know the exact set of source sets
    // that needs to be processed.
    return if (isKotlinMppProject(root)) findModel(root, KotlinGradleModel::class.java)
    else findModel(root, KotlinGradleModel::class.java, ModelBuilderService.Parameter::class.java) {
      it.value = androidArtifactSuffixes.joinToString(separator = ",") { artifactSuffix -> variantName.appendCapitalized(artifactSuffix) }
    }
  }

  /**
   * This method requests all of the required [Variant] models from Gradle via the tooling API.
   *
   * Function for choosing the build variant that will be selected in Android Studio (the IDE can only work with one variant at a time.)
   * works as follows:
   *  1. For Android app modules using new versions of the plugin, the [Variant] to select is obtained from the model.
   *     For older plugins, the "debug" variant is selected. If the module doesn't have a variant named "debug", it sorts all the
   *     variant names alphabetically and picks the first one.
   *  2. For Android library modules, it chooses the variant needed by dependent modules. For example, if variant "debug" in module "app"
   *     depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a leaf
   *     (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.
   */
  fun chooseSelectedVariants(
    inputModules: List<AndroidModule>,
    syncOptions: SingleVariantSyncActionOptions
  ) {
    val allModulesToSetUp = prepareRequestedOrDefaultModuleConfigurations(inputModules, syncOptions)

    // When re-syncing a project without changing the selected variants it is likely that the selected variants won't in the end.
    // However, variant resolution is not perfectly parallelizable. To overcome this we try to fetch the previously selected variant
    // models in parallel and discard any that happen to change.
    val preResolvedVariants =
      when {
        !syncOptions.flags.studioFlagParallelSyncEnabled -> emptyMap()
        !syncOptions.flags.studioFlagParallelSyncPrefetchVariantsEnabled -> emptyMap()
        !actionRunner.parallelActionsSupported -> emptyMap()
        // TODO(b/181028873): Predict changed variants and build models in parallel.
        syncOptions.moduleIdWithVariantSwitched != null -> emptyMap()
        else -> {
          actionRunner
            .runActions(allModulesToSetUp.map { getVariantAndModuleDependenciesAction(it, syncOptions.selectedVariants) })
            .filterNotNull()
            .associateBy { it.moduleConfiguration }
        }
      }

    // This first starts by requesting models for all the modules that can be reached from the app modules (via dependencies) and then
    // requests any other modules that can't be reached.
    var propagatedToModules: List<ModuleConfiguration>? = null
    val visitedModules = HashSet<String>()

    while (propagatedToModules != null || allModulesToSetUp.isNotEmpty()) {
      // Walk the tree of module dependencies in the breadth-first-search order.
      val moduleConfigurationsToRequest: List<ModuleConfiguration> =
        if (propagatedToModules != null) {
          // If there are modules for which we know the selected variant, build models for these modules in parallel.
          val modules = propagatedToModules
          propagatedToModules = null
          modules.filter { visitedModules.add(it.id) }
        }
        else {
          // Otherwise take the next module from the main queue and follow its dependencies.
          val moduleConfiguration = allModulesToSetUp.removeFirst()
          if (!visitedModules.add(moduleConfiguration.id)) emptyList() else listOf(moduleConfiguration)
        }
      if (moduleConfigurationsToRequest.isEmpty()) continue

      val actions =
        moduleConfigurationsToRequest.map { moduleConfiguration ->
          val prefetchedModel = preResolvedVariants[moduleConfiguration]
          if (prefetchedModel != null) {
            // Return an action that simply returns the `prefetchedModel`.
            fun(_: BuildController) = prefetchedModel
          }
          else {
            getVariantAndModuleDependenciesAction(
              moduleConfiguration,
              syncOptions.selectedVariants
            )
          }
        }

      val preModuleDependencies = actionRunner.runActions(actions)

      preModuleDependencies.filterNotNull().forEach { result ->
        result.module.syncedVariant = result.ideVariant
        result.module.syncedNativeVariant = when (val nativeVariantAbiResult = result.nativeVariantAbi) {
          is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi
          is NativeVariantAbiResult.V2 -> null
          NativeVariantAbiResult.None -> null
        }
        result.module.syncedNativeVariantAbiName = when (val nativeVariantAbiResult = result.nativeVariantAbi) {
          is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi.abi
          is NativeVariantAbiResult.V2 -> nativeVariantAbiResult.selectedAbiName
          NativeVariantAbiResult.None -> null
        }
      }
      propagatedToModules = preModuleDependencies.filterNotNull().flatMap { it.moduleDependencies }.takeUnless { it.isEmpty() }
    }
  }

  private fun prepareRequestedOrDefaultModuleConfigurations(
    inputModules: List<AndroidModule>,
    syncOptions: SingleVariantSyncActionOptions
  ): LinkedList<ModuleConfiguration> {
    // The module whose variant selection was changed from UI, the dependency modules should be consistent with this module. Achieve this by
    // adding this module to the head of allModules so that its dependency modules are resolved first.
    return inputModules
      .asSequence()
      .filter { it.fetchedVariantNames.isEmpty() }
      .mapNotNull { module -> selectedOrDefaultModuleConfiguration(module, syncOptions)?.let { module to it } }
      .sortedBy { (module, _) ->
        when {
          module.id == syncOptions.moduleIdWithVariantSwitched -> 0
          // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
          // The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
          // dependencies of others and will be visited sooner and the configurations created below will be discarded. This is fine since
          // `createRequestedModuleConfiguration()` is cheap.
          module.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP -> 1
          else -> 2
        }
      }
      .map { it.second }
      .toCollection(LinkedList<ModuleConfiguration>())
  }

  private fun selectedOrDefaultModuleConfiguration(
    module: AndroidModule,
    syncOptions: SingleVariantSyncActionOptions
  ): ModuleConfiguration? {
    // TODO(b/181028873): Better predict variants that needs to be prefetched. Add a new field to record the predicted variant.
    val selectedVariants = syncOptions.selectedVariants
    val requestedVariantName = selectVariantForAppOrLeaf(module, selectedVariants) ?: return null
    val requestedAbi = selectedVariants.getSelectedAbi(module.id)
    return ModuleConfiguration(module.id, requestedVariantName, requestedAbi)
  }

  private fun selectVariantForAppOrLeaf(
    androidModule: AndroidModule,
    selectedVariants: SelectedVariants
  ): String? {
    val variantNames = androidModule.allVariantNames ?: return null
    return selectedVariants
             .getSelectedVariant(androidModule.id)
             // Check to see if we have a variant selected in the IDE, and that it is still a valid one.
             ?.takeIf { variantNames.contains(it) }
           ?: androidModule.defaultVariantName
  }

  private class SyncVariantResult(
    val moduleConfiguration: ModuleConfiguration,
    val module: AndroidModule,
    val ideVariant: IdeVariant,
    val nativeVariantAbi: NativeVariantAbiResult,
    val moduleDependencies: List<ModuleConfiguration>
  )

  /**
   * Given a [moduleConfiguration] returns an action that fetches variant specific models for the given module+variant and returns the set
   * of [SyncVariantResult]s containing the fetched models and describing resolved module configurations for the given module.
   */
  private fun getVariantAndModuleDependenciesAction(
    moduleConfiguration: ModuleConfiguration,
    selectedVariants: SelectedVariants
  ): (BuildController) -> SyncVariantResult? {
    val selectedVariantDetails = selectedVariants.selectedVariants[moduleConfiguration.id]?.details
    val module = androidModulesById[moduleConfiguration.id] ?: return { null }
    return fun(controller: BuildController): SyncVariantResult? {
      val variant = controller.findVariantModel(module, moduleConfiguration.variant) ?: return null
      module.kotlinGradleModel = controller.findKotlinGradleModelForAndroidProject(module.findModelRoot, variant.name)
      val abiToRequest = chooseAbiToRequest(module, variant.name, moduleConfiguration.abi)
      val nativeVariantAbi = abiToRequest
        ?.let { controller.findNativeVariantAbiModel(modelCache, module, variant.name, abiToRequest) } ?: NativeVariantAbiResult.None
      // Regardless of the current selection in the IDE we try to select the same ABI in all modules the "top" module depends on even
      // when intermediate modules do not have native code.
      val abiToPropagate = nativeVariantAbi.abi ?: moduleConfiguration.abi

      val ideVariant = modelCache.variantFrom(module.androidProject, variant, module.modelVersion)
      val newlySelectedVariantDetails = createVariantDetailsFrom(module.androidProject.flavorDimensions, ideVariant, nativeVariantAbi.abi)
      val variantDiffChange =
        VariantSelectionChange.extractVariantSelectionChange(from = newlySelectedVariantDetails, base = selectedVariantDetails)

      fun propagateVariantSelectionChangeFallback(dependencyModuleId: String): ModuleConfiguration? {
        val dependencyModule = androidModulesById[dependencyModuleId] ?: return null
        val dependencyModuleCurrentlySelectedVariant = selectedVariants.selectedVariants[dependencyModuleId]
        val dependencyModuleSelectedVariantDetails = dependencyModuleCurrentlySelectedVariant?.details

        val newSelectedVariantDetails = dependencyModuleSelectedVariantDetails?.applyChange(
          variantDiffChange ?: VariantSelectionChange.EMPTY
        )
                                        ?: return null

        // Make sure the variant name we guessed in fact exists.
        if (dependencyModule.allVariantNames?.contains(newSelectedVariantDetails.name) != true) return null

        return ModuleConfiguration(dependencyModuleId, newSelectedVariantDetails.name, abiToPropagate)
      }

      fun generateDirectModuleDependencies(): List<ModuleConfiguration> {
        return ideVariant.mainArtifact.level2Dependencies.moduleDependencies.mapNotNull { moduleDependency ->
          val dependencyProject = moduleDependency.projectPath
          val dependencyModuleId = Modules.createUniqueModuleId(moduleDependency.buildId ?: "", dependencyProject)
          val dependencyVariant = moduleDependency.variant
          if (dependencyVariant != null) {
            ModuleConfiguration(dependencyModuleId, dependencyVariant, abiToPropagate)
          }
          else {
            propagateVariantSelectionChangeFallback(dependencyModuleId)
          }
        }
      }

      /**
       * Attempt to propagate variant changes to feature modules. This is not guaranteed to be correct, but since we do not know what the
       * real dependencies of each feature module variant are we can only guess.
       */
      fun generateDynamicFeatureDependencies(): List<ModuleConfiguration> {
        val rootProjectGradleDirectory = module.gradleProject.projectIdentifier.buildIdentifier.rootDir
        return module.androidProject.dynamicFeatures.mapNotNull { featureModuleGradlePath ->
          val featureModuleId = Modules.createUniqueModuleId(rootProjectGradleDirectory, featureModuleGradlePath)
          propagateVariantSelectionChangeFallback(featureModuleId)
        }
      }

      return SyncVariantResult(
        moduleConfiguration,
        module,
        ideVariant,
        nativeVariantAbi,
        generateDirectModuleDependencies() + generateDynamicFeatureDependencies()
      )
    }
  }

  private fun BuildController.findVariantModel(
    module: AndroidModule,
    variantName: String
  ): Variant? {
    return findModel(
      module.findModelRoot,
      Variant::class.java,
      ModelBuilderParameter::class.java
    ) { parameter ->
      parameter.setVariantName(variantName)
    }
  }

  sealed class NativeVariantAbiResult {
    class V1(val variantAbi: IdeNativeVariantAbi) : NativeVariantAbiResult()
    class V2(val selectedAbiName: String) : NativeVariantAbiResult()
    object None : NativeVariantAbiResult()

    val abi: String? get() = when(this) {
      is V1 -> variantAbi.abi
      is V2 -> selectedAbiName
      None -> null
    }
  }

  private fun BuildController.findNativeVariantAbiModel(
    modelCache: ModelCache,
    module: AndroidModule,
    variantName: String,
    abiToRequest: String
  ): NativeVariantAbiResult {
    return if (module.nativeModelVersion == AndroidModule.NativeModelVersion.V2) {
      // V2 model is available, trigger the sync with V2 API
      // NOTE: Even though we drop the value returned the side effects of this code are important. Native sync creates file on the disk
      // which are later used.
      val model = findModel(module.findModelRoot, NativeModule::class.java, NativeModelBuilderParameter::class.java) { parameter ->
        parameter.variantsToGenerateBuildInformation = listOf(variantName)
        parameter.abisToGenerateBuildInformation = listOf(abiToRequest)
      }
      if (model != null) NativeVariantAbiResult.V2(abiToRequest) else NativeVariantAbiResult.None
    }
    else {
      // Fallback to V1 models otherwise.
      val model = findModel(module.findModelRoot, NativeVariantAbi::class.java, ModelBuilderParameter::class.java) { parameter ->
        parameter.setVariantName(variantName)
        parameter.setAbiName(abiToRequest)
      }
      if (model != null) NativeVariantAbiResult.V1(modelCache.nativeVariantAbiFrom(model)) else NativeVariantAbiResult.None
    }
  }

  /**
   * [selectedAbi] if null or the abi doesn't exist a default will be picked. This default will be "x86" if
   *               it exists in the abi names returned by the [NativeAndroidProject] otherwise the first item of this list will be
   *               chosen.
   */
  private fun chooseAbiToRequest(
    module: AndroidModule,
    variantName: String,
    selectedAbi: String?
  ): String? {
    // This module is not a native one, nothing to do
    if (module.nativeModelVersion == AndroidModule.NativeModelVersion.None) return null

    // Attempt to get the list of supported abiNames for this variant from the NativeAndroidProject
    // Otherwise return from this method with a null result as abis are not supported.
    val abiNames = module.getVariantAbiNames(variantName) ?: return null

    return (if (selectedAbi != null && abiNames.contains(selectedAbi)) selectedAbi else abiNames.getDefaultOrFirstItem("x86"))
           ?: throw AndroidSyncException("No valid Native abi found to request!")
  }
}

private val List<GradleBuild>.projects: Sequence<BasicGradleProject> get() = asSequence().flatMap { it.projects.asSequence() }
private val androidArtifactSuffixes = listOf("", "unitTest", "androidTest")

private fun Collection<SyncIssue>.toSyncIssueData(): List<IdeSyncIssue> {
  return map { syncIssue ->
    IdeSyncIssueImpl(
      message = syncIssue.message,
      data = syncIssue.data,
      multiLineMessage = ModelCache.safeGet(syncIssue::multiLineMessage, null)?.toList(),
      severity = syncIssue.severity,
      type = syncIssue.type
    )
  }
}

private fun createAndroidModule(
  gradleProject: BasicGradleProject,
  androidProject: AndroidProject,
  nativeAndroidProject: NativeAndroidProject?,
  nativeModule: NativeModule?,
  modelCache: ModelCache
): AndroidModule {
  val modelVersionString = ModelCache.safeGet(androidProject::getModelVersion, "")
  val modelVersion: GradleVersion? = GradleVersion.tryParseAndroidGradlePluginVersion(modelVersionString)

  val ideAndroidProject = modelCache.androidProjectFrom(androidProject)
  val idePrefetchedVariants =
    ModelCache.safeGet(androidProject::getVariants, emptyList())
      .map { modelCache.variantFrom(ideAndroidProject, it, modelVersion) }
      .takeUnless { it.isEmpty() }

  // Single-variant-sync models have variantNames property and pre-single-variant sync model should have all variants present instead.
  val allVariantNames: Set<String>? = (ModelCache.safeGet(androidProject::getVariantNames, null)
                                       ?: idePrefetchedVariants?.map { it.name })?.toSet()

  val defaultVariantName: String? = ModelCache.safeGet(androidProject::getDefaultVariant, null)
                                    ?: allVariantNames?.getDefaultOrFirstItem("debug")

  val ideNativeAndroidProject = nativeAndroidProject?.let(modelCache::nativeAndroidProjectFrom)
  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  val androidModule = AndroidModule(
    modelVersion,
    gradleProject,
    ideAndroidProject,
    allVariantNames,
    defaultVariantName,
    idePrefetchedVariants,
    ideNativeAndroidProject,
    ideNativeModule
  )

  @Suppress("DEPRECATION")
  ModelCache.safeGet(androidProject::getSyncIssues, null)?.let {
    // It will be overridden if we receive something here but also a proper sync issues model later.
    syncIssues ->
    androidModule.setSyncIssues(syncIssues.toSyncIssueData())
  }

  return androidModule
}

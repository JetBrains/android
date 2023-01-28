/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeUnresolvedDependency
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.ignoreExceptionsAndGet
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.mapCatching
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.mapNull
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.BuildModel
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock

internal class SyncProjectActionWorker(
  private val buildInfo: BuildInfo,
  private val syncCounters: SyncCounters,
  private val syncOptions: SyncProjectActionOptions,
  private val actionRunner: SyncActionRunner
) {
  private val modelCacheLock = ReentrantLock()
  private val internedModels = InternedModels(buildInfo.buildRootDirectory)
  private val androidModulesById: MutableMap<String, AndroidModule> = HashMap()
  private val rootBuildModel: BuildModel get() = buildInfo.rootBuild

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
   * All the requested models are registered back to the external project system via the
   * [ProjectImportModelProvider.BuildModelConsumer] callback.
   */
  fun populateAndroidModels(basicIncompleteModules: List<BasicIncompleteGradleModule>): List<GradleModelCollection> {
    val modules = syncCounters.projectInfoPhase { fetchGradleModulesAction(basicIncompleteModules) }

    val androidModules = modules.filterIsInstance<AndroidModule>()
    androidModules.forEach { androidModulesById[it.id] = it }

    val androidModulesByProjectPath = androidModules
      .associateBy { (BuildId(it.gradleProject.projectIdentifier.buildIdentifier.rootDir) to it.gradleProject.path) }

    fun resolveAndroidProjectPath(buildId: BuildId, projectPath: String): AndroidModule? =
      androidModulesByProjectPath[buildId to projectPath]

    syncCounters.variantAndDependencyResolutionPhase {
      when (syncOptions) {
        is SingleVariantSyncActionOptions -> {
          // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
          // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
          // e.g the ones that should be selected by the IDE.
          chooseSelectedVariants(androidModules, syncOptions, ::resolveAndroidProjectPath)
        }

        is AllVariantsSyncActionOptions -> {
          syncAllVariants(androidModules, ::resolveAndroidProjectPath)
        }
      }
    }

    syncCounters.additionalArtifactsPhase {
      // AdditionalClassifierArtifactsModel must be requested after AndroidProject and Variant model since it requires the library list in dependency model.
      getAdditionalClassifierArtifactsModel(
        actionRunner,
        androidModules,
        internedModels::lookup,
        syncOptions.additionalClassifierArtifactsAction.cachedLibraries,
        syncOptions.additionalClassifierArtifactsAction.downloadAndroidxUISamplesSources,
        syncOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport
      )
    }


    // Requesting ProjectSyncIssues must be performed "last" since all other model requests may produces additional issues.
    // Note that "last" here means last among Android models since many non-Android models are requested after this point.
    actionRunner.runActions(androidModules.mapNotNull { it.getFetchSyncIssuesAction() })
    internedModels.prepare(modelCacheLock)
    val indexedModels = indexModels(modules)
    return modules.map { it.prepare(indexedModels) } + GradleProject(rootBuildModel, internedModels.createLibraryTable())
  }

  private fun indexModels(modules: List<GradleModule>): IndexedModels {
    return IndexedModels(
      dynamicFeatureToBaseFeatureMap =
      modules.filterIsInstance<AndroidModule>()
        .flatMap { androidModule ->
          val projectIdentifier = androidModule.gradleProject.projectIdentifier
          val rootDir = projectIdentifier.buildIdentifier.rootDir
          androidModule.androidProject.dynamicFeatures
            .map { ModuleId(it, rootDir.path) }
            .map { it to androidModule.moduleId }
        }
        .toMap()
    )
  }

  private fun fetchGradleModulesAction(
    incompleteBasicModules: List<BasicIncompleteGradleModule>
  ): List<GradleModule> {
    return actionRunner.runActions(
      incompleteBasicModules
        .map { it.getGradleModuleAction(internedModels, modelCacheLock, buildInfo) }
        .toList()
    )
  }

  private fun syncAllVariants(
    inputModules: List<AndroidModule>,
    androidProjectPathResolver: AndroidProjectPathResolver
  ) {
    if (inputModules.isEmpty()) return

    val variants =
      actionRunner
        .runActions(inputModules.flatMap { module ->
          module.allVariantNames.orEmpty().map { variant ->
            getVariantAction(ModuleConfiguration(module.id, variant, abi = null), androidProjectPathResolver)
          }
        })
        .mapNotNull { it.ignoreExceptionsAndGet()?.let { result -> result.module to it } }
        .groupBy({ it.first }, { it.second })

    // TODO(b/220325253): Kotlin parallel model fetching is broken.
    actionRunner.runActions(variants.entries.map { (module, result) ->
      ActionToRun(fun(controller: BuildController) {
        result.map {
          it.mapCatching {
            when (it) {
              is SyncVariantResultCoreFailure -> Unit
              is SyncVariantResultCoreSuccess -> {
                val allKotlinModels = controller.findKotlinModelsForAndroidProject(module.findModelRoot, it.ideVariant.name)
                module.kotlinGradleModel = allKotlinModels.kotlinModel
                module.kaptGradleModel = allKotlinModels.kaptModel
              }
            }
          }
        }
      }, fetchesKotlinModels = true)
    })

    variants.entries.forEach { (module, result) ->
      module.allVariants = result.mapNotNull { (it.ignoreExceptionsAndGet() as? SyncVariantResultCoreSuccess)?.ideVariant }
      module.recordExceptions(result.flatMap { it.exceptions })
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
  private fun chooseSelectedVariants(
    inputModules: List<AndroidModule>,
    syncOptions: SingleVariantSyncActionOptions,
    androidProjectPathResolver: AndroidProjectPathResolver
  ) {
    if (inputModules.isEmpty()) return
    val allModulesToSetUp = prepareRequestedOrDefaultModuleConfigurations(inputModules, syncOptions)

    // When re-syncing a project without changing the selected variants it is likely that the selected variants won't in the end.
    // However, variant resolution is not perfectly parallelizable. To overcome this we try to fetch the previously selected variant
    // models in parallel and discard any that happen to change.
    val preResolvedVariants =
      when {
        !syncOptions.flags.studioFlagParallelSyncEnabled -> emptyMap()
        !syncOptions.flags.studioFlagParallelSyncPrefetchVariantsEnabled -> emptyMap()
        !actionRunner.parallelActionsForV2ModelsSupported -> emptyMap()
        // TODO(b/181028873): Predict changed variants and build models in parallel.
        syncOptions.switchVariantRequest != null -> emptyMap()
        else -> {
          actionRunner
            .runActions(allModulesToSetUp.map {
              getVariantAndModuleDependenciesAction(
                it,
                syncOptions.selectedVariants,
                androidProjectPathResolver
              )
            })
            .mapNotNull { it.ignoreExceptionsAndGet()?.let { result -> result.moduleConfiguration to it } }
            .toMap()
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
          // Otherwise, take the next module from the main queue and follow its dependencies.
          val moduleConfiguration = allModulesToSetUp.removeFirst()
          if (!visitedModules.add(moduleConfiguration.id)) emptyList() else listOf(moduleConfiguration)
        }
      if (moduleConfigurationsToRequest.isEmpty()) continue

      val actions: List<ActionToRun<ModelResult<SyncVariantResult>>> =
        moduleConfigurationsToRequest.map { moduleConfiguration ->
          val prefetchedModel = preResolvedVariants[moduleConfiguration]
          if (prefetchedModel != null) {
            // Return an action that simply returns the `prefetchedModel`.
            val action = (fun(_: BuildController): ModelResult<SyncVariantResult> = prefetchedModel)
            ActionToRun(action)
          } else {
            getVariantAndModuleDependenciesAction(
              moduleConfiguration,
              syncOptions.selectedVariants,
              androidProjectPathResolver
            )
          }
        }

      val preModuleDependenciesWithoutKotlin: List<ModelResult<SyncVariantResult>> = actionRunner.runActions(actions)

      // TODO(b/220325253): Kotlin parallel model fetching is broken.
      val preModuleDependencies = actionRunner.runActions(preModuleDependenciesWithoutKotlin.map { syncResult ->
        ActionToRun(fun(controller: BuildController): ModelResult<SyncVariantResult> {
          return syncResult.mapCatching { syncResult ->
            when (syncResult) {
              is SyncVariantResultFailure -> syncResult
              is SyncVariantResultSuccess -> {
                val allKotlinModels =
                  controller.findKotlinModelsForAndroidProject(syncResult.module.findModelRoot, syncResult.ideVariant.name)

                syncResult.module.kotlinGradleModel = allKotlinModels.kotlinModel
                syncResult.module.kaptGradleModel = allKotlinModels.kaptModel
                syncResult // Not changing value as results are returned as side effects.
              }
            }
          }
        }, fetchesKotlinModels = true)
      }
      )

      preModuleDependencies.forEach { result ->
        val updatedResult = result.mapCatching { result ->
          when (result) {
            is SyncVariantResultFailure -> result
            is SyncVariantResultSuccess -> {
              result.module.syncedVariant = result.ideVariant
              result.module.unresolvedDependencies = result.unresolvedDependencies
              result.module.syncedNativeVariant = when (val nativeVariantAbiResult = result.nativeVariantAbi) {
                is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi
                is NativeVariantAbiResult.V2 -> null
                NativeVariantAbiResult.None -> null
              }
              result.module.syncedNativeVariantAbiName = when (

                val nativeVariantAbiResult = result.nativeVariantAbi
              ) {
                is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi.abi
                is NativeVariantAbiResult.V2 -> nativeVariantAbiResult.selectedAbiName
                NativeVariantAbiResult.None -> null
              }

              result
            }
          }
        }
        updatedResult.ignoreExceptionsAndGet()?.module?.recordExceptions(updatedResult.exceptions) // TODO(solodkyy): Record exceptions when module is unresolved.
      }
      propagatedToModules =
        preModuleDependencies
          .flatMap { (it.ignoreExceptionsAndGet() as? SyncVariantResultSuccess)?.moduleDependencies.orEmpty() }
          .takeUnless { it.isEmpty() }
    }
  }

  /**
   * Given a [moduleConfiguration] returns an action that fetches variant specific models for the given module+variant and returns the set
   * of [SyncVariantResult]s containing the fetched models and describing resolved module configurations for the given module.
   */
  private fun getVariantAndModuleDependenciesAction(
    moduleConfiguration: ModuleConfiguration,
    selectedVariants: SelectedVariants,
    androidProjectPathResolver: AndroidProjectPathResolver
  ): ActionToRun<ModelResult<SyncVariantResult>> {
    val getVariantActionToRun = getVariantAction(moduleConfiguration, androidProjectPathResolver)
    return getVariantActionToRun.map { syncVariantResultCoreResult ->
      syncVariantResultCoreResult.mapCatching { syncVariantResultCore ->
        when (syncVariantResultCore) {
          is SyncVariantResultCoreFailure -> SyncVariantResultFailure(syncVariantResultCore)
          is SyncVariantResultCoreSuccess -> SyncVariantResultSuccess(
            syncVariantResultCore,
            syncVariantResultCore.getModuleDependencyConfigurations(selectedVariants, androidModulesById, internedModels::lookup),
          )
        }
      }
    }
  }

  private fun getVariantAction(
    moduleConfiguration: ModuleConfiguration,
    androidProjectPathResolver: AndroidProjectPathResolver
  ): ActionToRun<ModelResult<SyncVariantResultCore>> {
    val module = androidModulesById[moduleConfiguration.id]
      ?: return ActionToRun({ ModelResult.create { error("Module with id '${moduleConfiguration.id}' not found") } }, false)
    val isV2Action =
      when (module) { // Exhaustive when, do not replace with `is`.
        is AndroidModule.V1 -> false
        is AndroidModule.V2 -> true
      }
    return ActionToRun(
      fun(controller: BuildController): ModelResult<SyncVariantResultCore> {
        return ModelResult.create {
          val ideVariant: IdeVariantWithPostProcessor =
            module
              .variantFetcher(controller, androidProjectPathResolver, module, moduleConfiguration)
              .mapNull { error("Resolved variant '${moduleConfiguration.variant}' does not exist.") }
              .recordAndGet()
              ?: return@create SyncVariantResultCoreFailure(moduleConfiguration, module)

          val variantName = ideVariant.name
          val abiToRequest: String? = chooseAbiToRequest(module, variantName, moduleConfiguration.abi)
          val nativeVariantAbi: NativeVariantAbiResult = abiToRequest?.let {
            controller.findNativeVariantAbiModel(
              modelCacheV1Impl(internedModels, buildInfo.buildFolderPaths, modelCacheLock),
              module,
              variantName,
              it
            )
          } ?: NativeVariantAbiResult.None

          fun getUnresolvedDependencies(): List<IdeUnresolvedDependency> {
            val unresolvedDependencies = mutableListOf<IdeUnresolvedDependency>()
            unresolvedDependencies.addAll(ideVariant.mainArtifact.unresolvedDependencies)
            ideVariant.androidTestArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
            ideVariant.testFixturesArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
            ideVariant.unitTestArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
            return unresolvedDependencies
          }

          SyncVariantResultCoreSuccess(
            moduleConfiguration,
            module,
            ideVariant,
            nativeVariantAbi,
            getUnresolvedDependencies()
          )
        }
      }, fetchesV2Models = isV2Action, fetchesV1Models = !isV2Action
    )
  }

  private fun prepareRequestedOrDefaultModuleConfigurations(
    inputModules: List<AndroidModule>,
    syncOptions: SingleVariantSyncActionOptions
  ): LinkedList<ModuleConfiguration> {
    // The module whose variant selection was changed from UI, the dependency modules should be consistent with this module. Achieve this by
    // adding this module to the head of allModules so that its dependency modules are resolved first.
    return inputModules
      .asSequence()
      .mapNotNull { module -> selectedOrDefaultModuleConfiguration(module, syncOptions)?.let { module to it } }
      .sortedBy { (module, _) ->
        when {
          module.id == syncOptions.switchVariantRequest?.moduleId -> 0
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

  private fun selectedOrDefaultModuleConfiguration(
    module: AndroidModule,
    syncOptions: SingleVariantSyncActionOptions
  ): ModuleConfiguration? {
    // TODO(b/181028873): Better predict variants that needs to be prefetched. Add a new field to record the predicted variant.
    val selectedVariants = syncOptions.selectedVariants
    val switchVariantRequest = syncOptions.switchVariantRequest
    val selectedVariantName = selectVariantForAppOrLeaf(module, selectedVariants) ?: return null
    val selectedAbi = selectedVariants.getSelectedAbi(module.id)

    fun variantContainsAbi(variantName: String, abi: String): Boolean {
      return module.getVariantAbiNames(variantName)?.contains(abi) == true
    }

    fun firstVariantContainingAbi(abi: String): String? {
      return (setOfNotNull(module.defaultVariantName) + module.allVariantNames.orEmpty())
        .firstOrNull { module.getVariantAbiNames(it)?.contains(abi) == true }
    }

    return when (switchVariantRequest?.moduleId) {
      module.id -> {
        val requestedAbi = switchVariantRequest.abi
        val selectedVariantName = when (requestedAbi) {
          null -> switchVariantRequest.variantName
          else ->
            when {
              variantContainsAbi(switchVariantRequest.variantName ?: selectedVariantName, requestedAbi) ->
                switchVariantRequest.variantName
              else ->
                firstVariantContainingAbi(requestedAbi)
            }
        } ?: selectedVariantName
        val selectedAbi = requestedAbi.takeIf { module.getVariantAbiNames(selectedVariantName)?.contains(it) == true } ?: selectedAbi
        ModuleConfiguration(module.id, selectedVariantName, selectedAbi)
      }
      else -> {
        ModuleConfiguration(module.id, selectedVariantName, selectedAbi)
      }
    }
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
}

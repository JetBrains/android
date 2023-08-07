/*
 * Copyright (C) 2023 The Android Open Source Project
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
import java.util.Collections
import java.util.LinkedList

// This is used as a key to track the variant fetch requests
// It will be expanded with variant itself because it is possible we end up
// fetching multiple variants for the same module
private data class FetchedVariant(val moduleId: String, val variantName: String?)


private sealed class FetchedVariantResult {
  class Finished(val result: ModelResult<SyncVariantResult>) : FetchedVariantResult()
  object InProgress: FetchedVariantResult()
}
private fun ModelResult<SyncVariantResult>.toFinishedResult() = FetchedVariantResult.Finished(this)

internal class VariantDiscovery(
  private val actionRunner: SyncActionRunner,
  private val androidModulesById: Map<String, AndroidModule>,
  private val syncOptions: SyncProjectActionOptions,
  private val buildInfo: BuildInfo,
  private val internedModels: InternedModels,
  private val androidProjectPathResolver: AndroidProjectPathResolver,
) {

  // In all variant sync we sync all the variants available,
  // so that's also used in the result map keys which determines which variants are fetched.
  private fun ModuleConfiguration.toMapKey() = FetchedVariant(id, variant.takeIf { syncOptions is AllVariantsSyncActionOptions })

  private val inputModules = androidModulesById.values
  private val moduleFetchResults = Collections.synchronizedMap(HashMap<FetchedVariant, FetchedVariantResult>())

  fun syncAllVariants() {
    if (inputModules.isEmpty()) return

    val variants =
      actionRunner
        .runActions(inputModules.flatMap { module ->
          module.allVariantNames.orEmpty().map { variant ->
            ModuleConfiguration(module.id, variant, abi = null, isRoot = true).toFetchVariantDependenciesAction()
          }
        })
        .filterIsInstance<FetchedVariantResult.Finished>()
        .mapNotNull { it.result.ignoreExceptionsAndGet()?.let { result -> result.module to it } }
        .groupBy({ it.first }, { it.second })

    actionRunner.runActions(variants.entries.map { (module, result) ->
      ActionToRun(fun(controller: BuildController) {
        result.map {
          it.result.mapCatching {
            when (it) {
              is SyncVariantResultFailure -> Unit
              is SyncVariantResultSuccess -> {
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
      module.allVariants = result.mapNotNull { (it.result.ignoreExceptionsAndGet() as? SyncVariantResultSuccess)?.ideVariant }
      module.recordExceptions(result.flatMap { it.result.exceptions })
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
   *     depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a root
   *     (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.
   */
  fun discoverVariantsAndSync() {
    if (inputModules.isEmpty()) return
    val modulesToSetUp = prepareRequestedOrDefaultModuleConfigurations()


    if (shouldUsePreviouslyResolvedVariants) {
      // If we know which variants to fetch for each module, fetch and populate those without traversing the dependencies
      populatePreviouslyResolvedVariantResults(modulesToSetUp)
    } else {
      // Otherwise fetch each root module (and its dependencies) one-by-one to figure out the variant for each module
      while (modulesToSetUp.isNotEmpty()) {
        val nextModule = modulesToSetUp.removeFirstOrNull() ?: continue
        if (moduleFetchResults.containsKey(nextModule.toMapKey())) continue
        actionRunner.runActions(listOf(nextModule.toTraverseAction()))
      }
    }

    check(moduleFetchResults.values.all { it is FetchedVariantResult.Finished }) { "No work should be in progress at this point!" }
    val results = moduleFetchResults.values
      .filterIsInstance<FetchedVariantResult.Finished>()
      .map { it.result }
    // Fetch the kotlin dependencies for the successfully fetched modules
    actionRunner.runActions(
        results
        .filterIsInstance<ModelResult<SyncVariantResultSuccess>>()
        .mapNotNull {
          it.ignoreExceptionsAndGet()?.let { fetchKotlinModelsAction(it) }
        }
    )

    // Populate the AndroidModule models with the fetched information
    results.forEach {
      updateAndroidModuleWithResult(it)
    }
  }

  /** Returns the default configuration to be fetched for each module and sorts them according to their priorities.
   *
   * The module whose variant was specified explicitly by the user has the highest priority.
   *
   * Then, app modules must be requested since they are used to work out which variants to request for their dependencies.
   * The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
   * dependencies of others and will be visited sooner and the configurations created below will be discarded as they will have been already
   * fetched
   */
  private fun prepareRequestedOrDefaultModuleConfigurations(): LinkedList<ModuleConfiguration> {
    return inputModules
      .asSequence()
      .mapNotNull { module -> userSelectedOrDefaultModuleConfiguration(module)?.let { module to it } }
      .sortedBy { (module, _) ->
        when {
          module.id == (syncOptions as SingleVariantSyncActionOptions).switchVariantRequest?.moduleId -> 0
          module.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP -> 1
          else -> 2
        }
      }
      .map { it.second }
      .toCollection(LinkedList<ModuleConfiguration>())
  }

  /** Converts a [ModuleConfiguration] to the action that fetches and processed the variant dependencies model for the module. */
  private fun ModuleConfiguration.toFetchVariantDependenciesAction(): ActionToRun<FetchedVariantResult> {
    val module = androidModulesById[id]
      ?: return ActionToRun({ ModelResult.create { error("Module with id '${id}' not found") }.toFinishedResult() }, false)
    val isV2Action =
      when (module) { // Exhaustive when, do not replace with `is`.
        is AndroidModule.V1 -> false
        is AndroidModule.V2 -> true
      }
    return ActionToRun(
      { controller ->
        // If a result exists, return that, otherwise set in progress and calculate it
        // Existing value in this case, can be either an in progress marker or an actual finished result,
        // in either of these cases, we can just return that without calculating anything.
        val existingValue = moduleFetchResults.putIfAbsent(toMapKey(), FetchedVariantResult.InProgress)
        existingValue ?: ModelResult.create {
          val ideVariant: IdeVariantWithPostProcessor =
            module
              .variantFetcher(controller, androidProjectPathResolver, module, this@toFetchVariantDependenciesAction)
              .mapNull { error("Resolved variant '${variant}' does not exist.") }
              .recordAndGet()
            ?: return@create SyncVariantResultFailure(this@toFetchVariantDependenciesAction, module)

          val variantName = ideVariant.name
          val abiToRequest: String? = chooseAbiToRequest(module, variantName, abi)
          val nativeVariantAbi: NativeVariantAbiResult = abiToRequest?.let {
            controller.findNativeVariantAbiModel(
              modelCacheV1Impl(internedModels, buildInfo.buildFolderPaths),
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

          SyncVariantResultSuccess(
            this@toFetchVariantDependenciesAction,
            module,
            ideVariant,
            nativeVariantAbi,
            getUnresolvedDependencies()
          )
        }.toFinishedResult()
      }, fetchesV2Models = isV2Action, fetchesV1Models = !isV2Action
    )
  }

  private fun userSelectedOrDefaultModuleConfiguration(
    module: AndroidModule
  ): ModuleConfiguration? {
    // TODO(b/181028873): Better predict variants that needs to be prefetched. Add a new field to record the predicted variant.
    val selectedVariants = (syncOptions as SingleVariantSyncActionOptions).selectedVariants
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
            if (variantContainsAbi(switchVariantRequest.variantName ?: selectedVariantName, requestedAbi)) {
              switchVariantRequest.variantName
            } else {
              firstVariantContainingAbi(requestedAbi)
            }
        } ?: selectedVariantName
        val selectedAbi = requestedAbi.takeIf { module.getVariantAbiNames(selectedVariantName)?.contains(it) == true } ?: selectedAbi
        ModuleConfiguration(module.id, selectedVariantName, selectedAbi, isRoot = true)
      }
      else -> {
        ModuleConfiguration(module.id, selectedVariantName, selectedAbi, isRoot = true)
      }
    }
  }

  /**
   * After the dependencies are fetched and processed, this will traverse the dependencies of the given module, in depth-first order.
   *
   * This action doesn't return anything. Results will be stored in [moduleFetchResults]. Make sure it's thread safe as it will be written
   * by multiple actions at the same time to update the results.
   */
  private fun ModuleConfiguration.toTraverseAction(): ActionToRun<Unit> {
    return toFetchVariantDependenciesAction().map { wrappedResult ->
      if (wrappedResult is FetchedVariantResult.Finished) {
        wrappedResult.result.mapCatching { result ->
          moduleFetchResults[toMapKey()] = wrappedResult
          if (result is SyncVariantResultSuccess) {
            val moduleDependencies = result.getModuleDependencyConfigurations(
              (syncOptions as SingleVariantSyncActionOptions).selectedVariants,
              androidModulesById,
              internedModels::lookup
            )
            actionRunner.runActions(
              moduleDependencies
                .filter { !moduleFetchResults.containsKey(it.toMapKey()) }
                .map { it.toTraverseAction() }
            )
          }
        }
      }
    }
  }

  /** Populate the AndroidModule models of each fetched module with the result */
  private fun updateAndroidModuleWithResult(result: ModelResult<SyncVariantResult>) {
    val updatedResult = result.mapCatching { result ->
      if (result is SyncVariantResultSuccess) {
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
      }
      result
    }
    if (syncOptions.syncTestMode == SyncTestMode.TEST_EXCEPTION_WITH_UNRESOLVED_MODULE) {
      error("**internal error for tests**")
    }
    // If the module is known, just record exceptions there, otherwise throw the first exception from the list
    // TODO(b/276454275): Record exceptions when module is unresolved.
    updatedResult.ignoreExceptionsAndGet()?.module?.recordExceptions(updatedResult.exceptions)
      ?: updatedResult.exceptions.firstOrNull()?.let { throw it }
  }

  private val shouldUsePreviouslyResolvedVariants get() =
    syncOptions.flags.studioFlagParallelSyncEnabled &&
    actionRunner.parallelActionsForV2ModelsSupported &&
    syncOptions.flags.studioFlagParallelSyncPrefetchVariantsEnabled &&
    // TODO(b/181028873): Predict changed variants and build models in parallel.
    (syncOptions as SingleVariantSyncActionOptions).switchVariantRequest == null


  /* When re-syncing a project without changing the selected variants it is likely that the selected variants won't in the end.
   * However, variant resolution is not perfectly parallelizable. To overcome this we try to fetch the previously selected variant
   * models in parallel. This is experimental and not yet enabled in production.
   */
  private fun populatePreviouslyResolvedVariantResults(allModulesToSetUp: LinkedList<ModuleConfiguration>) {
    actionRunner.runActions(allModulesToSetUp.map { it.toFetchVariantDependenciesAction() })
      .filterIsInstance<FetchedVariantResult.Finished>()
      .forEach {
        val moduleConfiguration = it.result.ignoreExceptionsAndGet()?.moduleConfiguration ?: return@forEach
        moduleFetchResults[moduleConfiguration.toMapKey()] = it
      }
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
    ?: throw AndroidSyncException(AndroidSyncExceptionType.NO_VALID_NATIVE_ABI_FOUND, "No valid Native abi found to request!")
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

private fun fetchKotlinModelsAction(result: SyncVariantResultSuccess) =
  ActionToRun({ controller ->
    val allKotlinModels =
      controller.findKotlinModelsForAndroidProject(result.module.findModelRoot, result.ideVariant.name)

    result.module.kotlinGradleModel = allKotlinModels.kotlinModel
    result.module.kaptGradleModel = allKotlinModels.kaptModel
  }, fetchesKotlinModels = true)

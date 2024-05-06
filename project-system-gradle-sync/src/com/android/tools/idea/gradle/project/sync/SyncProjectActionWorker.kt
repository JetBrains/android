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

import org.gradle.tooling.model.BuildModel

internal class SyncProjectActionWorker(
  private val buildInfo: BuildInfo,
  private val syncCounters: SyncCounters,
  private val syncOptions: SyncProjectActionOptions,
  private val actionRunner: SyncActionRunner
) {
  private val internedModels = InternedModels(buildInfo.buildRootDirectory)
  private val androidModulesById: MutableMap<String, AndroidModule> = LinkedHashMap()
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
      val variantDiscovery = VariantDiscovery(
        actionRunner,
        androidModulesById,
        syncOptions,
        buildInfo,
        internedModels,
        ::resolveAndroidProjectPath
      )
      when (syncOptions) {
        is SingleVariantSyncActionOptions -> {
          // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
          // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
          // e.g the ones that should be selected by the IDE.
          variantDiscovery.discoverVariantsAndSync()
        }

        is AllVariantsSyncActionOptions -> {
          variantDiscovery.syncAllVariants()
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
        syncOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport,
      )
    }


    // Requesting ProjectSyncIssues must be performed "last" since all other model requests may produces additional issues.
    // Note that "last" here means last among Android models since many non-Android models are requested after this point.
    actionRunner.runActions(androidModules.mapNotNull { it.getFetchSyncIssuesAction() })
    internedModels.prepare()
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
        .map { it.getGradleModuleAction(internedModels, buildInfo) }
        .toList()
    )
  }

}




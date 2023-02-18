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

import com.android.builder.model.v2.models.Versions
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.LegacyV1AgpVersionModel
import com.android.tools.idea.gradle.model.IdeCompositeBuildMap
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import java.io.File

internal class BuildInfo(
  buildModels: List<GradleBuild>, // Always not empty.
  buildMap: IdeCompositeBuildMap,
  val buildFolderPaths: BuildFolderPaths,
) {
  val buildNameMap = buildMap.builds.associate { it.buildName to BuildId(it.buildId) }
  val buildIdMap = buildMap.builds.associate { BuildId(it.buildId) to it.buildName }

  val rootBuild: GradleBuild = buildModels.first()
  val projects: Sequence<BasicGradleProject> = buildModels.asSequence().flatMap { it.projects.asSequence() }
  val buildRootDirectory: File get() = buildFolderPaths.buildRootDirectory!!
}

/**
 * Implementation of the Android Gradle sync processes.
 *
 * [syncOptions] defines the sync variant (mode) to run: single-variant-sync (the regular sync), all-variant-sync (requested by the
 * project structure dialog) and native-variant-sync (supports run/debug with older AGP versions).
 *
 * The overall flow is:
 *   buildInfo ->
 *   List<BasicIncompleteGradleModule>
 *     via AndroidProjectResult for Android modules ->
 *   List<GradleModule>
 *     Android build variant selection
 *     via SyncVariantResultCore -> SyncVariantResult ->
 *   List<GradleModule> (mutable properties populated) ->
 *   List<DeliverableGradleModule>
 */
internal class AndroidExtraModelProviderWorker(
  controller: BuildController, // NOTE: Do not make it a property. [controller] should be accessed via [SyncActionRunner]'s only.
  private val syncCounters: SyncCounters,
  private val syncOptions: SyncActionOptions,
  private val buildInfo: BuildInfo,
  private val consumer: ProjectImportModelProvider.BuildModelConsumer
) {
  private val safeActionRunner =
    SyncActionRunner.create(controller, syncCounters, syncOptions.flags.studioFlagParallelSyncEnabled)

  fun populateBuildModels() {
    try {
      val modelCollections: List<GradleModelCollection> =
        when (syncOptions) {
          is SyncProjectActionOptions -> {
            val modules: List<BasicIncompleteGradleModule> = syncCounters.projectListPhase { getBasicIncompleteGradleModules() }
            val v2AndroidGradleModules = modules.filterIsInstance<BasicV2AndroidModuleGradleProject>()

            modules.filterIsInstance<BasicIncompleteAndroidModule>().forEach { checkAgpVersionCompatibility(it.agpVersion, syncOptions) }
            verifyIncompatibleAgpVersionsAreNotUsedOrFailSync(modules)

            val gradleVersion = safeActionRunner.runAction { it.getModel(BuildEnvironment::class.java).gradle.gradleVersion }
            val v2ModelBuildersSupportParallelSync =
              v2AndroidGradleModules
                .all { canUseParallelSync(AgpVersion.tryParse(it.versions.agp), gradleVersion) }

            val configuredSyncActionRunner = safeActionRunner.enableParallelFetchForV2Models(v2ModelBuildersSupportParallelSync)

            val models =
              SyncProjectActionWorker(buildInfo, syncCounters, syncOptions, configuredSyncActionRunner)
                .populateAndroidModels(modules)

            val syncExecutionReport = IdeSyncExecutionReport(
              parallelFetchForV2ModelsEnabled =
              configuredSyncActionRunner.parallelActionsForV2ModelsSupported && v2AndroidGradleModules.isNotEmpty()
            )

            models + StandaloneDeliverableModel.createModel(syncExecutionReport, buildInfo.rootBuild)
          }
          is NativeVariantsSyncActionOptions -> {
            consumer.consume(
              buildInfo.rootBuild,
              // TODO(b/215344823): Idea parallel model fetching is broken for now, so we need to request it sequentially.
              safeActionRunner.runAction { controller -> controller.getModel(IdeaProject::class.java) },
              IdeaProject::class.java
            )
            NativeVariantsSyncActionWorker(buildInfo, syncOptions, safeActionRunner).fetchNativeVariantsAndroidModels()
          }
          // Note: No more cases.
        }
      modelCollections.forEach { it.deliverModels(consumer) }
    }
    catch (e: AndroidSyncException) {
      consumer.consume(
        buildInfo.rootBuild,
        IdeAndroidSyncError(e.message.orEmpty(), e.stackTrace.map { it.toString() }),
        IdeAndroidSyncError::class.java
      )
    }
  }

  private fun getBasicIncompleteGradleModules(): List<BasicIncompleteGradleModule> {
    return safeActionRunner.runActions(
      buildInfo.projects.map { gradleProject ->
        val buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
        val buildName = buildInfo.buildIdMap[buildId] ?: error("Build not found: $buildId")
        ActionToRun(
          fun(controller: BuildController): BasicIncompleteGradleModule {
            // Request V2 models if flag is enabled.
            if (syncOptions.flags.studioFlagUseV2BuilderModels) {
              // First request the Versions model to make sure we can fetch V2 models.
              val versions = controller.findNonParameterizedV2Model(gradleProject, Versions::class.java)
              if (versions != null && canFetchV2Models(AgpVersion.tryParse(versions.agp))) {
                // This means we can request V2.
                return BasicV2AndroidModuleGradleProject(gradleProject, buildName, versions, syncOptions.syncTestMode)
              }
            }
            // We cannot request V2 models.
            // Check if we have android projects that cannot be requested using V2, but can be requested using V1.
            val legacyV1AgpVersionModel = controller.findModel(gradleProject, LegacyV1AgpVersionModel::class.java)
            // LegacyV1AgpVersionModel is always available if `com.android.base` plugin is applied.
            if (legacyV1AgpVersionModel != null)
              return BasicV1AndroidModuleGradleProject(
                gradleProject,
                buildName,
                legacyV1AgpVersionModel
              )

            return BasicNonAndroidIncompleteGradleModule(gradleProject, buildName) // Check here tha Version does not return anything.
          },
          fetchesV2Models = true,
          fetchesV1Models = true
        )
      }.toList()
    )
  }
}

private fun verifyIncompatibleAgpVersionsAreNotUsedOrFailSync(modules: List<BasicIncompleteGradleModule>) {
  val agpVersionsAndGradleBuilds = modules
    .filterIsInstance<BasicIncompleteAndroidModule>()
    .map { it.agpVersion to it.buildName }
  // Fail Sync if we do not use the same AGP version across all the android projects.
  if (agpVersionsAndGradleBuilds.isNotEmpty() && agpVersionsAndGradleBuilds.map { it.first }.distinct().singleOrNull() == null)
    throw AgpVersionsMismatch(agpVersionsAndGradleBuilds)
}

private fun canFetchV2Models(gradlePluginVersion: AgpVersion?): Boolean {
  return gradlePluginVersion != null && gradlePluginVersion.isAtLeast(7, 2, 0, "alpha", 1, true)
}

/**
 * Checks if we can request the V2 models in parallel.
 * We need to make sure we only request the models in parallel if:
 * - we are fetching android models
 * - we are using Gradle 7.4.2+ (https://github.com/gradle/gradle/issues/18587)
 * - using a stable AGP version higher than or equal to AGP 7.2.0 and lower than AGP 7.3.0-alpha01, or
 * - using at least AGP 7.3.0-alpha-04.
 *  @returns true if we can fetch the V2 models in parallel, otherwise, returns false.
 */
private fun canUseParallelSync(agpVersion: AgpVersion?, gradleVersion: String): Boolean {
  return GradleVersion.version(gradleVersion) >= GradleVersion.version("7.4.2") &&
         agpVersion != null &&
         ((agpVersion >= AgpVersion(7, 2, 0) && agpVersion < "7.3.0-alpha01") ||
          agpVersion.isAtLeast(7, 3, 0, "alpha", 4, true))
}

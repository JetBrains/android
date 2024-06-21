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
import com.android.ide.gradle.model.LegacyV1AgpVersionModel
import com.android.tools.idea.gradle.model.IdeCompositeBuildMap
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.intellij.gradle.toolingExtension.impl.model.projectModel.GradleExternalProjectModelProvider
import com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyModelProvider
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetModelProvider
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
  /** Mapping from Gradle identity path to [BuildId]. */
  val buildPathMap = buildMap.builds.associate { it.buildPath to BuildId(it.buildId) }
  /** Mapping from [BuildId] to Gradle identity path. */
  val buildIdMap = buildMap.builds.associate { BuildId(it.buildId) to it.buildPath }

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
  private val consumer: ProjectImportModelProvider.GradleModelConsumer
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

            modules.filterIsInstance<BasicIncompleteAndroidModule>().forEach {
              it.modelVersions.checkAgpVersionCompatibility(syncOptions.flags)
            }
            verifyIncompatibleAgpVersionsAreNotUsedOrFailSync(modules)

            val gradleVersion = safeActionRunner.runAction { it.getModel(BuildEnvironment::class.java).gradle.gradleVersion }

            val v2ModelBuildersSupportParallelSync =
              GradleVersion.version(gradleVersion) >= GradleVersion.version("7.4.2") &&
              v2AndroidGradleModules.all { it.modelVersions[ModelFeature.SUPPORTS_PARALLEL_SYNC] }

            val configuredSyncActionRunner = safeActionRunner.enableParallelFetchForV2Models(
              v2ModelBuildersSupportParallelSync,
              syncOptions.flags.studioFlagFetchKotlinModelsInParallel
            )

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
            // Native sync may run with just a subset of resolvers, so make sure to fetch  IdeaProject and ExternalProject directly
            consumer.consumeBuildModel(
              buildInfo.rootBuild,
              // TODO(b/215344823): Idea parallel model fetching is broken for now, so we need to request it sequentially.
              safeActionRunner.runAction { controller -> controller.getModel(IdeaProject::class.java) },
              IdeaProject::class.java
            )
            safeActionRunner.runAction { controller ->
              // TODO(b/215344823): Idea parallel model fetching is broken for now, so we need to request it sequentially.
              GradleSourceSetModelProvider().runModelProvider(controller, buildInfo, consumer)
              GradleSourceSetDependencyModelProvider().runModelProvider(controller, buildInfo, consumer)
              GradleExternalProjectModelProvider().runModelProvider(controller, buildInfo, consumer)
            }
            NativeVariantsSyncActionWorker(buildInfo, syncOptions, safeActionRunner).fetchNativeVariantsAndroidModels()
          }
          // Note: No more cases.
        }
      modelCollections.forEach { it.deliverModels(consumer) }
    }
    catch (e: AndroidSyncException) {
      consumer.consumeBuildModel(
        buildInfo.rootBuild,
        IdeAndroidSyncError(
          type = e.type,
          message = e.message.orEmpty(),
          stackTrace = e.stackTrace.map { it.toString() },
          buildPath = e.buildPath,
          modulePath = e.modulePath,
          syncIssues = e.syncIssues
        ),
        IdeAndroidSyncError::class.java
      )
    }
  }

  private fun ProjectImportModelProvider.runModelProvider(
    controller: BuildController,
    buildInfo: BuildInfo,
    modelConsumer: ProjectImportModelProvider.GradleModelConsumer,
  ) {
    for (gradleProject in buildInfo.projects) {
      populateProjectModels(controller, gradleProject, modelConsumer)
    }
    populateBuildModels(controller, buildInfo.rootBuild, modelConsumer)
    populateModels(controller, listOf(buildInfo.rootBuild), modelConsumer)
  }

  private fun getBasicIncompleteGradleModules(): List<BasicIncompleteGradleModule> {
    return safeActionRunner.runActions(
      buildInfo.projects.map { gradleProject ->
        // org.gradle.tooling.model.BuildIdentifier.getRootDir does not return canonical path, so we need to convert it to one
        // as other Gradle APIs e.g. Project.projectDir does return canonical paths.
        val buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir.canonicalFile)
        val buildPath = buildInfo.buildIdMap[buildId] ?: error("Build not found: $buildId \n" +
                                                               "available builds ${buildInfo.buildIdMap}")
        ActionToRun(
          fun(controller: BuildController): BasicIncompleteGradleModule {
            // Request V2 models if flag is enabled.
            if (syncOptions.flags.studioFlagUseV2BuilderModels) {
              // First request the Versions model to make sure we can fetch V2 models.
              val versions = controller.findNonParameterizedV2Model(gradleProject, Versions::class.java)?.convert()
              if (versions?.get(ModelFeature.HAS_V2_MODELS) == true) {
                // This means we can request V2.
                return BasicV2AndroidModuleGradleProject(gradleProject, buildPath, versions, syncOptions)
              }
            }
            // We cannot request V2 models.
            // Check if we have android projects that cannot be requested using V2, but can be requested using V1.
            val legacyV1AgpVersionModel = controller.findModel(gradleProject, LegacyV1AgpVersionModel::class.java)
            // LegacyV1AgpVersionModel is always available if `com.android.base` plugin is applied.
            if (legacyV1AgpVersionModel != null)
              return BasicV1AndroidModuleGradleProject(
                gradleProject,
                buildPath,
                legacyV1AgpVersionModel.convert()
              )

            return BasicNonAndroidIncompleteGradleModule(gradleProject, buildPath) // Check here tha Version does not return anything.
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
    .map { it.modelVersions.agpVersionAsString to it.buildPath }
  // Fail Sync if we do not use the same AGP version across all the android projects.
  if (agpVersionsAndGradleBuilds.isNotEmpty() && agpVersionsAndGradleBuilds.map { it.first }.distinct().singleOrNull() == null)
    throw AgpVersionsMismatch(agpVersionsAndGradleBuilds)
}

/**
 * This is the only model-reading version check that should compare AGP versions directly.
 * All other checks should use [ModelVersions.get] with a [ModelFeature]
 */
private val MINIMUM_AGP_FOR_VERSIONS_MAP = AgpVersion.parse("7.3.0")

private fun Versions.convert(): ModelVersions {
  val agpVersion = AgpVersion.parse(agp)
  // This is the only check that should be formulated this way, as ModelVersions hasn't been constructed yet
  val versions: Map<String, Versions.Version> = if (agpVersion >= MINIMUM_AGP_FOR_VERSIONS_MAP) versions else emptyMap()
  val minimumModelConsumer = versions[Versions.MINIMUM_MODEL_CONSUMER]?.let { version ->
    // Human-readable field was added before MINIMUM_MODEL_CONSUMER was reported, and is required for MINIMUM_MODEL_CONSUMER.
    ModelConsumerVersion(version.major, version.minor, version.humanReadable ?: error(
      "AGP that reports a MINIMUM_MODEL_CONSUMER version must have a human readable version"))
  }
  val modelVersion = versions[Versions.MODEL_PRODUCER]?.let { version ->
    ModelVersion(version.major, version.major, version.humanReadable ?: error(
      "AGP that reports a MODEL_PRODUCER version must have a human readable version"))
  } ?: ModelVersion(Int.MIN_VALUE, Int.MIN_VALUE, agpVersion.toString())

  return ModelVersions(
    agp = agpVersion,
    modelVersion = modelVersion,
    minimumModelConsumer = minimumModelConsumer,
  )
}

private fun LegacyV1AgpVersionModel.convert(): ModelVersions {
  val agpVersion = AgpVersion.parse(agp)
  return ModelVersions(
    agp = agpVersion,
    modelVersion = ModelVersion(Int.MIN_VALUE, Int.MIN_VALUE, agpVersion.toString()),
    minimumModelConsumer = null,
  )
}


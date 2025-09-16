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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.GradlePropertiesModel
import com.android.ide.gradle.model.composites.BuildMap
import com.android.ide.gradle.model.dependencies.DeclaredDependencies
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.ignoreExceptionsAndGet
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.diagnostic.logger
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider


class PhasedSyncProjectModelProvider(val syncOptions: SyncActionOptions, val cachedModels: ModelProviderCachedData) : ProjectImportModelProvider {
   /**
    * This is just indicating which phase the provider will  on. To match the names it can technically run on
    * [GradleModelFetchPhase.PROJECT_MODEL_PHASE] but we populate source sets with this information, so it's kept in the source set phase.
    * The source of the clash is the mismatch between the platform models and our models. Platform models for the projects don't have source
    * set information whereas Android models do, so some sort of inconsistency will always be there with the current definition of phases.
   */
  override fun getPhase() =  GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE

  override fun populateModels(controller: BuildController,
                              buildModels: MutableCollection<out GradleBuild>,
                              modelConsumer: ProjectImportModelProvider.GradleModelConsumer) {
    val exceptionsPerProject = mutableListOf<Pair<BasicGradleProject, Throwable>>()
    // .first call seems a bit silly, but it's what we do on the other side too
    val rootBuildId = BuildId(buildModels.first().buildIdentifier.rootDir)
    // Build root directory is used for dependency models only, not used in a meaningful way here.
    // Apart from that, we use interned models only to intern strings.
    val buildRootDirectory = null
    val internedModels = InternedModels(buildRootDirectory)
    controller.run(buildModels.flatMap { buildModel ->
      buildModel.projects.mapNotNull { gradleProject ->
        BuildAction {
          runCatching {
            if (controller.findModel(gradleProject, GradlePluginModel::class.java)?.hasKotlinMultiPlatform() == true) {
              // Kotlin multiplatform projects are not supported for phased sync yet.
              return@BuildAction null
            }
            val versions = controller.findModel(gradleProject, Versions::class.java)
                             // TODO(b/384022658): Reconsider this check if we implement a cache between model providers to avoid fetching the models twice
                             ?.takeIf { it.isAtLeastAgp8() } ?: return@BuildAction null
            val modelVersions = versions.convert()
            val basicAndroidProject = controller.findModel(gradleProject, BasicAndroidProject::class.java)
            val androidProject = controller.findModel(gradleProject, AndroidProject::class.java)
            val androidDsl = controller.findModel(gradleProject, AndroidDsl::class.java)
            val gradlePropertiesModel = controller.findModel(gradleProject, GradlePropertiesModel::class.java)

            val selectedVariantName = computeVariantNameToBeSynced(syncOptions, gradleProject.moduleId(), basicAndroidProject, androidDsl)
                                      ?: return@BuildAction null

            val modelCache = modelCacheV2Impl(internedModels, modelVersions, syncTestMode = syncOptions.syncTestMode)

            val ideAndroidProject = modelCache.androidProjectFrom(
              rootBuildId,
              buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir),
              basicAndroidProject,
              androidProject,
              modelVersions,
              androidDsl,
              legacyAndroidGradlePluginProperties = null, // Is this actually needed now?
              gradlePropertiesModel,
              defaultVariantName = null // Is this actually needed now?
            ).let { it.exceptions.takeIf { it.isNotEmpty() }?.first()?.let { throw it } ?: it.ignoreExceptionsAndGet()!! }
            gradleProject to AndroidProjectData(
              versions,
              basicAndroidProject,
              androidProject,
              androidDsl,
              controller.findModel(gradleProject, DeclaredDependencies::class.java),
              controller.findModel(gradleProject, GradlePluginModel::class.java),
              ideAndroidProject,
              selectedVariantName,
              shouldSkipRuntimeClasspathForLibraries(androidProject.flags, gradlePropertiesModel)
            )
          }.onFailure {
            exceptionsPerProject += gradleProject to it
          }.getOrNull()
        }
      }
    }).filterNotNull().forEach { (gradleProject, data) ->
        modelConsumer.consumeProjectModel(gradleProject, gradleProject, BasicGradleProject::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.versions, Versions::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.basicAndroidProject, BasicAndroidProject::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.androidProject, AndroidProject::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.androidDsl, AndroidDsl::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.declaredDependencies, DeclaredDependencies::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.gradlePluginModel, GradlePluginModel::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.ideAndroidProject, IdeAndroidProject::class.java)
        cachedModels.data[gradleProject] = CachedAndroidProjectData(
          data.selectedVariantName,
          data.ideAndroidProject.projectType,
          data.shouldSkipRuntimeClassPathForLibraries,
          data.declaredDependencies.allOutgoingProjectDependencies
        )
      }
    buildModels.map { it.rootProject }.distinct().forEach { projectModel ->
      runCatching {
        controller.findModel(projectModel, BuildMap::class.java)?.let {
          modelConsumer.consumeProjectModel(projectModel, it, BuildMap::class.java)
        }
        val basicModelsMap = projectModel.getAllChildren { it.children.toList() }.associateBy { it.path }

        controller.findModel(projectModel, GradleProject::class.java)?.let {
          it.getAllChildren { it.children.toList() }.forEach {
            modelConsumer.consumeProjectModel(basicModelsMap[it.path]!!, it, GradleProject::class.java)
          }
        }
      }.onFailure {
        exceptionsPerProject += projectModel to it
      }
    }
    exceptionsPerProject
      .groupBy ({ it.first }) { it.second }
      .filter { (_, exceptions) -> exceptions.isNotEmpty()}
      .forEach { (gradleProject, exceptions) ->
        // TODO: Explicitly fetch sync issues as well
        val issuesAndExceptions = IdeAndroidSyncIssuesAndExceptions(syncIssues = emptyList(), exceptions = exceptions)
        modelConsumer.consumeProjectModel(gradleProject, issuesAndExceptions, IdeAndroidSyncIssuesAndExceptions::class.java)
    }
  }
}


private val LOG = logger<PhasedSyncProjectModelProvider>()

/** Use [Modules.createUniqueModuleId] to provide module id. */
fun computeVariantNameToBeSynced(syncOptions: SyncActionOptions, moduleId: String, basicAndroidProject: BasicAndroidProject, androidDsl: AndroidDsl): String? =
  when (syncOptions) {
    is SingleVariantSyncActionOptions ->
      // newly user-selected variant
      syncOptions.switchVariantRequest.takeIf { it?.moduleId == moduleId }?.variantName?.also {
        LOG.debug("Picked user-selected variant $it for $moduleId")
      }
      // variants selected by the last sync, only if it still exists
      ?: syncOptions.selectedVariants.getSelectedVariant(moduleId).takeIf { basicAndroidProject.variants.map { it.name }.contains(it) }?.also {
        LOG.debug("Picked selected variant from last sync $it for $moduleId")
      }
    else -> null
  } // default variant as specified by the build script (computation could still end up being null)
  ?: basicAndroidProject.variants.toList().getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors).also {
    LOG.debug("Picked the default variant $it for $moduleId")
  }

private fun Versions.isAtLeastAgp8() = AgpVersion.parse(agp).isAtLeast(8, 0, 0)

private fun BasicGradleProject.moduleId() = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, path)

private data class AndroidProjectData(
  val versions: Versions,
  val basicAndroidProject: BasicAndroidProject,
  val androidProject: AndroidProject,
  val androidDsl: AndroidDsl,
  val declaredDependencies: DeclaredDependencies,
  val gradlePluginModel: GradlePluginModel,
  val ideAndroidProject: IdeAndroidProject,
  val selectedVariantName: String,
  val shouldSkipRuntimeClassPathForLibraries: Boolean
)


private fun <T> T.getAllChildren(childrenFunction: (T) -> List<T>): List<T> {
  val result = mutableListOf<T>(this)
  val stack = ArrayDeque<T>(result)
  while(stack.isNotEmpty()) {
    val next = stack.removeLast()
    val children  = childrenFunction(next)
    result.addAll(children)
    stack.addAll(children)
  }
  return result
}

internal fun shouldSkipRuntimeClasspathForLibraries(flags: AndroidGradlePluginProjectFlags, gradlePropertiesModel: GradlePropertiesModel) =
  !AndroidGradlePluginProjectFlags.BooleanFlag.ENABLE_COMPILE_RUNTIME_CLASSPATH_ALIGNMENT.getValue(flags, true) || // true because we always used to align
  AndroidGradlePluginProjectFlags.BooleanFlag.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS.getValue(flags, gradlePropertiesModel.excludeLibraryComponentsFromConstraints)
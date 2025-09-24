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

import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.VariantDependenciesAdjacencyList
import com.android.ide.gradle.model.composites.BuildMap
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.ignoreExceptionsAndGet
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase

import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import kotlin.collections.forEach
import kotlin.collections.orEmpty
import kotlin.to
import org.gradle.tooling.model.gradle.BasicGradleProject

class PhasedSyncDependencyModelProvider(val syncOptions: SyncActionOptions, val cachedData: ModelProviderCachedData) : ProjectImportModelProvider {
  override fun getPhase() =  GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE

  override fun populateModels(controller: BuildController,
                              buildModels: MutableCollection<out GradleBuild>,
                              modelConsumer: ProjectImportModelProvider.GradleModelConsumer) {
    val exceptionsPerProject = mutableListOf<Pair<BasicGradleProject, Throwable>>()
    val buildPathMap = buildBuildPathMap(controller, buildModels)
    val internedModels = InternedModels(buildModels.first().buildIdentifier.rootDir)

    val result = controller.run(buildModels.flatMap { buildModel ->
      // For each build, build a map per-project to indicate whether it has inbound dependencies
      // This is used as an optimization when determining whether to skip library runtime classpaths
      val hasInboundDependencyByPath = mutableMapOf<String, Boolean>()
      buildModel.projects.forEach {
        cachedData.data[it]?.allOutgoingProjectDependencies.orEmpty().forEach { gradleProjectPath ->
          hasInboundDependencyByPath[gradleProjectPath] = true
        }
      }

      val allProjects = buildModels.flatMap { it.projects }
      val ideAndroidProjectMap = allProjects.mapNotNull { cachedData.data[it]?.let { data -> it to data.ideAndroidProject }}.toMap()
      val androidProjectPathResolver = buildAndroidProjectPathResolver(allProjects, ideAndroidProjectMap)


      buildModel.projects
        .mapNotNull { gradleProject ->
          BuildAction {
            runCatching {
              val data = cachedData.data[gradleProject] ?: return@BuildAction null
              val ideAndroidProject = data.ideAndroidProject
              val variantDependencies = controller.findModel(
                gradleProject, VariantDependenciesAdjacencyList::class.java, ModelBuilderParameter::class.java
              ) {
                it.variantName = data.selectedVariantName
                // If the studio flag for it is enabled, we don't fetch runtime classpath for libraries with inbound depenencis
                if (data.shouldSkipRuntimeClassPathForLibraries
                    && ideAndroidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
                    && hasInboundDependencyByPath[gradleProject.path] == true) {
                  it.buildOnlyTestRuntimeClasspaths(
                    syncOptions.flags.studioFlagBuildRuntimeClasspathForLibraryUnitTests,
                    syncOptions.flags.studioFlagBuildRuntimeClasspathForLibraryScreenshotTests,
                    addAdditionalArtifactsInModel = syncOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport
                  )
                }
                else {
                  it.buildAllRuntimeClasspaths(
                    addAdditionalArtifactsInModel = syncOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport)
                }
              } ?: return@BuildAction null
              val selectedVariant = ideAndroidProject.coreVariants.single { it.name == data.selectedVariantName }
              val result = fetchAndProcessAndroidDependenciesModel(
                variantDependencies,
                cachedData.data[gradleProject]!!.modelVersions,
                buildModel,
                gradleProject,
                selectedVariant,
                ideAndroidProject.bootClasspath,
                internedModels,
                androidProjectPathResolver = androidProjectPathResolver,
                buildPathMap = buildPathMap
              )
              exceptionsPerProject += result.exceptions.map { gradleProject to it }
              result.ignoreExceptionsAndGet()?.let {
                gradleProject to it
              }
            }.onFailure {
              exceptionsPerProject += gradleProject to it
            }.getOrNull()
          }
        }
    })

    modelConsumer.consumeBuildModel(buildModels.first(), internedModels.apply { prepare() }.createLibraryTable(), IdeUnresolvedLibraryTableImpl::class.java)

    result.filterNotNull().forEach { (gradleProject, variantWithPostProcessor) ->
      val ideVariant = variantWithPostProcessor.postProcess()
      modelConsumer.consumeProjectModel(gradleProject, ideVariant, IdeVariantCore::class.java)
      // Store the selected variant to later create IdeAndroidModels with it in the additional model provider. IdeAndroidModels is still
      // required by a few different code paths.
      cachedData.selectedVariant[gradleProject] = ideVariant
    }

    exceptionsPerProject
      .groupBy ({ it.first }) { it.second }
      .filter { (_, exceptions) -> exceptions.isNotEmpty()}
      .forEach { (gradleProject, exceptions) ->
        // TODO: This is a bit silly because the model provider for source set can also deliver the same model (probably overriding each other)
        // We need to reconcile the exceptions and the sync issue model together and only deliver this once for this to work properly.
        val issuesAndExceptions = IdeAndroidSyncIssuesAndExceptions(syncIssues = emptyList(), exceptions = exceptions)
        modelConsumer.consumeProjectModel(gradleProject, issuesAndExceptions, IdeAndroidSyncIssuesAndExceptions::class.java)
      }
  }
}

private fun fetchAndProcessAndroidDependenciesModel(
  variantDependencies: VariantDependenciesAdjacencyList,
  versions: ModelVersions,
  buildModel: GradleBuild,
  projectModel: BasicGradleProject,
  ideVariant: IdeVariantCore,
  bootClasspath: Collection<String>,
  internedModels: InternedModels,
  androidProjectPathResolver: AndroidProjectPathResolver,
  buildPathMap: Map<String, BuildId>,
): ModelResult<IdeVariantWithPostProcessor> {
  // We need to be lenient when modules are being resolved as some might not be set up yet if not supported by phased sync
  // - for instance, the KMP modules are not supported and not yet populated in this case
  val modelCacheV2Impl = modelCacheV2Impl(internedModels, versions, syncTestMode = SyncTestMode.PRODUCTION, lenientModuleResolution = true)

  return modelCacheV2Impl.variantFrom(
    BuildId(buildModel.buildIdentifier.rootDir),
    projectModel.projectIdentifier.projectPath,
    ideVariant as IdeVariantCoreImpl,
    VariantDependenciesCompat.AdjacencyList(variantDependencies, versions),
    bootClasspath,
    androidProjectPathResolver,
    buildPathMap,
  )
}


/** Builds an [AndroidProjectPathResolver] instance, used to later refer to Android projects and resolve variant names for them. */
private fun buildAndroidProjectPathResolver(
  allProjects: List<BasicGradleProject>,
  ideAndroidProjects: Map<BasicGradleProject, IdeAndroidProject>
): AndroidProjectPathResolver {
  val projectIdentifierToResolvedProjectPathMap = allProjects.mapNotNull { projectModel ->
    run {
      val ideAndroidProject = ideAndroidProjects[projectModel] ?: return@mapNotNull null
      BuildId(projectModel.projectIdentifier.buildIdentifier.rootDir) to projectModel.path to
        ResolvedAndroidProjectPathImpl(
          projectModel,
          buildVariantNameResolver(ideAndroidProject, ideAndroidProject.coreVariants),
          ideAndroidProject.lintJar
        )
    }
  }.toMap()
  return AndroidProjectPathResolver { buildId, projectPath -> projectIdentifierToResolvedProjectPathMap[buildId to projectPath] }
}

/** Map from the Gradle path (composite aware, supports nested builds) to the Gradle build root directory. */
private fun buildBuildPathMap(
  controller: BuildController,
  allBuilds: Collection<GradleBuild>
): Map<String, BuildId> = allBuilds.mapNotNull { buildModel ->
  controller.findModel(buildModel.rootProject, BuildMap::class.java)
}.flatMap {
  it.buildIdMap.entries
}.associate { it.key to BuildId(it.value) }

/** Used to refer to Android projects and resolve variant names for them. */
private data class ResolvedAndroidProjectPathImpl(
  override val gradleProject: BasicGradleProject,
  override val androidVariantResolver: AndroidVariantResolver,
  // Lint jar is not really relevant here, but required by the IdeLibrary model when resolving
  override val lintJar: File?
): ResolvedAndroidProjectPath

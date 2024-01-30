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

import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePropertiesModel
import com.android.ide.gradle.model.LegacyAndroidGradlePluginProperties
import com.android.ide.gradle.model.LegacyAndroidGradlePluginPropertiesModelParameters
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.ignoreExceptionsAndGet
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.mapCatching
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel

/**
 * The container class of modules we couldn't fetch using parallel Gradle TAPI API.
 * For now this list has :
 *  - All the non-Android modules
 *  - The android modules using an older AGP version than the minimum supported for V2 sync
 */
internal sealed class BasicIncompleteGradleModule(
  val gradleProject: BasicGradleProject,
  val buildPath: String
) {
  val buildId: BuildId get() = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
  val projectPath: String get() = gradleProject.path

  abstract fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule>
}

/** The information about the model consumer version required by AGP */
data class ModelConsumerVersion(val major: Int, val minor: Int, val description: String) : Comparable<ModelConsumerVersion> {
  override fun compareTo(other: ModelConsumerVersion): Int {
    return if (this.major != other.major) this.major.compareTo(other.major) else this.minor.compareTo(other.minor)
  }
}

data class ModelVersion(val major: Int, val minor: Int, val description: String) : Comparable<ModelVersion> {
  override fun compareTo(other: ModelVersion): Int {
    return if (this.major != other.major) this.major.compareTo(other.major) else this.minor.compareTo(other.minor)
  }
}

data class ModelVersions(
  val agp: AgpVersion,
  val modelVersion: ModelVersion,
  val minimumModelConsumer: ModelConsumerVersion?
)

/**
 * The container class of Android modules.
 */
internal sealed class BasicIncompleteAndroidModule(gradleProject: BasicGradleProject, buildPath: String, val modelVersions: ModelVersions)
  :  BasicIncompleteGradleModule(gradleProject, buildPath) {
}

/**
 *  The container class of Android modules that can be fetched using V1 builder models.
 *  legacyV1AgpVersion: The model that contains the agp version used by the AndroidProject. This can be null if the AndroidProject is using
 *  an AGP version lower than the minimum supported version by Android Studio
 */
internal class BasicV1AndroidModuleGradleProject(
  gradleProject: BasicGradleProject,
  buildPath: String,
  modelVersions: ModelVersions,
) :  BasicIncompleteAndroidModule(gradleProject, buildPath, modelVersions) {

  override fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val androidProject = controller.findParameterizedAndroidModel(
          gradleProject,
          AndroidProject::class.java,
          shouldBuildVariant = false
        ) ?: error("Cannot fetch AndroidProject models for V1 projects.")

        val legacyAndroidGradlePluginProperties = controller.findModel(gradleProject, LegacyAndroidGradlePluginProperties::class.java, LegacyAndroidGradlePluginPropertiesModelParameters::class.java) {
          it.componentToApplicationIdMap = true
          it.namespace = modelVersions.agp.major < 7
        }
        val gradlePropertiesModel = controller.findModel(gradleProject, GradlePropertiesModel::class.java)
          ?: error("Cannot get GradlePropertiesModel (V1) for project '$gradleProject'")

        val modelCache = modelCacheV1Impl(internedModels, buildInfo.buildFolderPaths)
        val buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
        val rootBuildId = buildInfo.buildPathMap[":"] ?: error("Root build (':') not found")
        val androidProjectResult = AndroidProjectResult.V1Project(
          modelCache = modelCache,
          rootBuildId = rootBuildId,
          buildId = buildId,
          projectPath = gradleProject.path,
          androidProject = androidProject,
          legacyAndroidGradlePluginProperties = legacyAndroidGradlePluginProperties,
          gradlePropertiesModel = gradlePropertiesModel
        )

        return androidProjectResult
          .mapCatching { androidProjectResult ->
            val nativeModule = controller.findNativeModuleModel(gradleProject, syncAllVariantsAndAbis = false)
            val nativeAndroidProject: NativeAndroidProject? =
              if (nativeModule == null)
                controller.findParameterizedAndroidModel(
                  gradleProject, NativeAndroidProject::class.java,
                  shouldBuildVariant = false
                )
              else null

            createAndroidModuleV1(
              modelVersions,
              gradleProject,
              androidProjectResult,
              nativeAndroidProject,
              nativeModule,
              buildInfo.buildPathMap,
              modelCache
            )
          }
          .let {
            val result = it.ignoreExceptionsAndGet()
            // If we were unable to create an AndroidModule we have enough data to create a JavaModule. This is a fallback allowing users
            // access to at least build configuration files.
              ?: JavaModule(gradleProject, kotlinGradleModel = null, kaptGradleModel = null)
            result.recordExceptions(it.exceptions)
            result
          }
      },
      fetchesV1Models = true,
      fetchesKotlinModels = true
    )
  }
}

/**
 * The container class of Android modules that can be fetched using V2 builder models.
 */
internal class BasicV2AndroidModuleGradleProject(
  gradleProject: BasicGradleProject,
  buildPath: String,
  modelVersions: ModelVersions,
  val syncActionOptions: SyncActionOptions,
) : BasicIncompleteAndroidModule(gradleProject, buildPath, modelVersions) {


  override fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo,
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val basicAndroidProject = controller.findNonParameterizedV2Model(gradleProject, BasicAndroidProject::class.java)
          ?: error("Cannot get BasicAndroidProject model for $gradleProject")
        val androidProject = controller.findNonParameterizedV2Model(gradleProject, com.android.builder.model.v2.models.AndroidProject::class.java)
          ?: error("Cannot get V2AndroidProject model for $gradleProject")
        val androidDsl = controller.findNonParameterizedV2Model(gradleProject, AndroidDsl::class.java)
          ?: error("Cannot get AndroidDsl model for $gradleProject")
        val modelIncludesApplicationId = modelVersions.agpModelIncludesApplicationId
        val legacyAndroidGradlePluginProperties = if (!modelIncludesApplicationId) {
          controller.findModel(gradleProject, LegacyAndroidGradlePluginProperties::class.java, LegacyAndroidGradlePluginPropertiesModelParameters::class.java) {
            it.componentToApplicationIdMap = !modelIncludesApplicationId
            it.namespace = false // Always present in Model V2
          }
        } else {
          null
        }
        val gradlePropertiesModel = controller.findModel(gradleProject, GradlePropertiesModel::class.java)
          ?: error("Cannot get GradlePropertiesModel (V2) for project '$gradleProject'")

        val modelCache = modelCacheV2Impl(internedModels, modelVersions, syncActionOptions.syncTestMode,
                                          syncActionOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport)
        val rootBuildId = buildInfo.buildPathMap[":"] ?: error("Root build (':') not found")
        val androidProjectResult =
          AndroidProjectResult.V2Project(
            modelCache = modelCache,
            rootBuildId = rootBuildId,
            buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir),
            basicAndroidProject = basicAndroidProject,
            androidProject = androidProject,
            modelVersions = modelVersions,
            androidDsl = androidDsl,
            legacyAndroidGradlePluginProperties = legacyAndroidGradlePluginProperties,
            gradlePropertiesModel = gradlePropertiesModel,
            skipRuntimeClasspathForLibraries = syncActionOptions.flags.studioFlagSkipRuntimeClasspathForLibraries,
            useNewDependencyGraphModel = syncActionOptions.flags.studioFlagUseNewDependencyGraphModel
                                         && modelVersions.agp.isAtLeast(8,2,0, "alpha", 3, false)
          )

        return androidProjectResult.mapCatching { androidProjectResult ->
          // TODO(solodkyy): Perhaps request the version interface depending on AGP version.
          val nativeModule = controller.findNativeModuleModel(gradleProject, syncAllVariantsAndAbis = false)

          createAndroidModuleV2(
            modelVersions,
            gradleProject,
            androidProjectResult,
            nativeModule,
            buildInfo.buildPathMap,
            modelCache
          )
        }
          .let {
            val result = it.ignoreExceptionsAndGet()
            // If we were unable to create an AndroidModule we have enough data to create a JavaModule. This is a fallback allowing users
            // access to at least build configuration files.
            // TODO(b/254045637): Provide a fallback in the case when `BasicAndroidProject` is available but `AndroidProject` is not.
              ?: JavaModule(gradleProject, kotlinGradleModel = null, kaptGradleModel = null)
            result.recordExceptions(it.exceptions)
            result
          }
      },
      fetchesV2Models = true
    )
  }
}

/**
 * The container class of non-Android modules.
 */
internal class BasicNonAndroidIncompleteGradleModule(gradleProject: BasicGradleProject, buildPath: String) :
  BasicIncompleteGradleModule(gradleProject, buildPath) {
  override fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val kotlinGradleModel = controller.findModel(gradleProject, KotlinGradleModel::class.java)
        val kaptGradleModel = controller.findModel(gradleProject, KaptGradleModel::class.java)
        return JavaModule(gradleProject, kotlinGradleModel, kaptGradleModel)
      },
      fetchesV1Models = false,
      fetchesKotlinModels = true
    )
  }
}

private fun createAndroidModuleV1(
  modelVersions: ModelVersions,
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidProjectResult.V1Project,
  nativeAndroidProject: NativeAndroidProject?,
  nativeModule: NativeModule?,
  buildPathMap: Map<String, BuildId>,
  modelCache: ModelCache.V1
): AndroidModule {
  val ideAndroidProject = androidProjectResult.ideAndroidProject
  val allVariantNames = androidProjectResult.allVariantNames
  val defaultVariantName: String? = androidProjectResult.defaultVariantName

  val ideNativeAndroidProject = nativeAndroidProject?.let {
    modelCache.nativeAndroidProjectFrom(it, androidProjectResult.ndkVersion)
  }
  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  val androidModule = AndroidModule.V1(
    modelVersions = modelVersions,
    buildPathMap = buildPathMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    variantFetcher = androidProjectResult.createVariantFetcher(),
    nativeAndroidProject = ideNativeAndroidProject,
    nativeModule = ideNativeModule,
    legacyAndroidGradlePluginProperties = androidProjectResult.legacyAndroidGradlePluginProperties,
  )

  val syncIssues = androidProjectResult.syncIssues
  // It will be overridden if we receive something here but also a proper sync issues model later.
  if (syncIssues != null) {
    androidModule.setSyncIssues(syncIssues.toSyncIssueData() + androidModule.legacyAndroidGradlePluginProperties.getProblemsAsSyncIssues())
  }

  return androidModule
}

private fun createAndroidModuleV2(
  modelVersions: ModelVersions,
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidProjectResult.V2Project,
  nativeModule: NativeModule?,
  buildPathMap: Map<String, BuildId>,
  modelCache: ModelCache
): AndroidModule {

  val ideAndroidProject = androidProjectResult.ideAndroidProject
  val allVariantNames = androidProjectResult.allVariantNames
  val defaultVariantName: String? = androidProjectResult.defaultVariantName

  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  return AndroidModule.V2(
    modelVersions = modelVersions,
    buildPathMap = buildPathMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    androidVariantResolver = androidProjectResult.androidVariantResolver,
    variantFetcher = androidProjectResult.createVariantFetcher(),
    nativeModule = ideNativeModule,
    legacyAndroidGradlePluginProperties = androidProjectResult.legacyAndroidGradlePluginProperties,
  )
}

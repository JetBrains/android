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
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.ide.gradle.model.LegacyApplicationIdModel
import com.android.ide.gradle.model.LegacyV1AgpVersionModel
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import java.util.concurrent.locks.ReentrantLock

/**
 * The container class of modules we couldn't fetch using parallel Gradle TAPI API.
 * For now this list has :
 *  - All the non-Android modules
 *  - The android modules using an older AGP version than the minimum supported for V2 sync
 */
internal sealed class BasicIncompleteGradleModule(
  val gradleProject: BasicGradleProject,
  val buildName: String
) {
  val buildId: BuildId get() = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
  val projectPath: String get() = gradleProject.path

  abstract fun getGradleModuleAction(
    internedModels: InternedModels,
    modelCacheLock: ReentrantLock,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule>
}

/**
 * The container class of Android modules.
 */
internal sealed class BasicIncompleteAndroidModule(gradleProject: BasicGradleProject, buildName: String)
  :  BasicIncompleteGradleModule(gradleProject, buildName) {
  abstract val agpVersion: String
}

/**
 *  The container class of Android modules that can be fetched using V1 builder models.
 *  legacyV1AgpVersion: The model that contains the agp version used by the AndroidProject. This can be null if the AndroidProject is using
 *  an AGP version lower than the minimum supported version by Android Studio
 */
internal class BasicV1AndroidModuleGradleProject(
  gradleProject: BasicGradleProject,
  buildName: String,
  private val legacyV1AgpVersion: LegacyV1AgpVersionModel
) :  BasicIncompleteAndroidModule(gradleProject, buildName) {
  override val agpVersion: String
    get() = legacyV1AgpVersion.agp

  override fun getGradleModuleAction(
    internedModels: InternedModels,
    modelCacheLock: ReentrantLock,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val androidProject = controller.findParameterizedAndroidModel(
          gradleProject,
          AndroidProject::class.java,
          shouldBuildVariant = false
        ) ?: error("Cannot fetch AndroidProject models for V1 projects.")

        val legacyApplicationIdModel = controller.findModel(gradleProject, LegacyApplicationIdModel::class.java)

        val modelCache = modelCacheV1Impl(internedModels, buildInfo.buildFolderPaths, modelCacheLock)
        val buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
        val buildName = buildInfo.buildIdMap[buildId] ?: error("Unknown build id: $buildId")
        val rootBuildDir = buildInfo.buildNameMap[":"] ?: error("Root build (':') not found")
        val androidProjectResult = AndroidProjectResult.V1Project(
          modelCache = modelCache,
          rootBuildId = rootBuildDir,
          buildId = buildId,
          buildName = buildName,
          projectPath = gradleProject.path,
          androidProject = androidProject,
          legacyApplicationIdModel = legacyApplicationIdModel
        )

        val nativeModule = controller.findNativeModuleModel(gradleProject, syncAllVariantsAndAbis = false)
        val nativeAndroidProject: NativeAndroidProject? =
          if (nativeModule == null)
            controller.findParameterizedAndroidModel(
              gradleProject, NativeAndroidProject::class.java,
              shouldBuildVariant = false
            )
          else null

        return createAndroidModuleV1(
          gradleProject,
          androidProjectResult,
          nativeAndroidProject,
          nativeModule,
          buildInfo.buildNameMap,
          buildInfo.buildIdMap,
          modelCache
        )

      },
      fetchesV1Models = true,
      fetchesKotlinModels = true
    )
  }
}

/**
 * The container class of Android modules that can be fetched using V2 builder models.
 */
internal class BasicV2AndroidModuleGradleProject(gradleProject: BasicGradleProject, buildName: String, val versions: Versions) :
  BasicIncompleteAndroidModule(gradleProject, buildName)
{
  override val agpVersion: String
    get() = versions.agp

  override fun getGradleModuleAction(
    internedModels: InternedModels,
    modelCacheLock: ReentrantLock,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val basicAndroidProject = controller.findNonParameterizedV2Model(gradleProject, BasicAndroidProject::class.java)
          ?: error("Cannot get BasicAndroidProject model for $gradleProject")
        val androidProject = controller.findNonParameterizedV2Model(gradleProject, com.android.builder.model.v2.models.AndroidProject::class.java)
          ?: error("Cannot get V2AndroidProject model for $gradleProject")
        val androidDsl = controller.findNonParameterizedV2Model(gradleProject, AndroidDsl::class.java)
          ?: error("Cannot get AndroidDsl model for $gradleProject")
        val agpVersion = GradleVersion.tryParse(versions.agp)
          ?: error("AGP returned incorrect version: ${versions.agp}")
        val modelIncludesApplicationId = agpVersion.agpModelIncludesApplicationId
        val legacyApplicationIdModel = if (!modelIncludesApplicationId) {
          controller.findModel(gradleProject, LegacyApplicationIdModel::class.java)
        } else {
          null
        }

        val modelCache = modelCacheV2Impl(internedModels, modelCacheLock, agpVersion)
        val rootBuildId = buildInfo.buildNameMap[":"] ?: error("Root build (':') not found")
        val buildId = buildInfo.buildNameMap[basicAndroidProject.buildName]
          ?: error("(Included) build named '${basicAndroidProject.buildName}' not found")
        val androidProjectResult =
          AndroidProjectResult.V2Project(
            modelCache = modelCache,
            rootBuildId = rootBuildId,
            buildId = buildId,
            basicAndroidProject = basicAndroidProject,
            androidProject = androidProject,
            modelVersions = versions,
            androidDsl = androidDsl,
            legacyApplicationIdModel = legacyApplicationIdModel
          )

        // TODO(solodkyy): Perhaps request the version interface depending on AGP version.
        val nativeModule = controller.findNativeModuleModel(gradleProject, syncAllVariantsAndAbis = false)

        return createAndroidModuleV2(
          gradleProject,
          androidProjectResult,
          nativeModule,
          buildInfo.buildNameMap,
          buildInfo.buildIdMap,
          modelCache
        )
      },
      fetchesV2Models = true
    )
  }
}

/**
 * The container class of non-Android modules.
 */
internal class BasicNonAndroidIncompleteGradleModule(gradleProject: BasicGradleProject, buildName: String) :
  BasicIncompleteGradleModule(gradleProject, buildName) {
  override fun getGradleModuleAction(
    internedModels: InternedModels,
    modelCacheLock: ReentrantLock,
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
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidProjectResult.V1Project,
  nativeAndroidProject: NativeAndroidProject?,
  nativeModule: NativeModule?,
  buildNameMap: Map<String, BuildId>,
  buildIdMap: Map<BuildId, String>,
  modelCache: ModelCache.V1
): AndroidModule {
  val agpVersion: AgpVersion? = AgpVersion.tryParse(androidProjectResult.agpVersion)

  val ideAndroidProject = androidProjectResult.ideAndroidProject
  val allVariantNames = androidProjectResult.allVariantNames
  val defaultVariantName: String? = androidProjectResult.defaultVariantName

  val ideNativeAndroidProject = nativeAndroidProject?.let {
    modelCache.nativeAndroidProjectFrom(it, androidProjectResult.ndkVersion)
  }
  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  val androidModule = AndroidModule.V1(
    agpVersion = agpVersion,
    buildName = androidProjectResult.buildName,
    buildNameMap = buildNameMap,
    buildIdMap = buildIdMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    variantFetcher = androidProjectResult.createVariantFetcher(),
    nativeAndroidProject = ideNativeAndroidProject,
    nativeModule = ideNativeModule,
    legacyApplicationIdModel = androidProjectResult.legacyApplicationIdModel,
  )

  val syncIssues = androidProjectResult.syncIssues
  // It will be overridden if we receive something here but also a proper sync issues model later.
  if (syncIssues != null) {
    androidModule.setSyncIssues(syncIssues.toSyncIssueData() + androidModule.legacyApplicationIdModel.getProblemsAsSyncIssues())
  }

  return androidModule
}

private fun createAndroidModuleV2(
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidProjectResult.V2Project,
  nativeModule: NativeModule?,
  buildNameMap: Map<String, BuildId>,
  buildIdMap: Map<BuildId, String>,
  modelCache: ModelCache
): AndroidModule {
  val agpVersion: AgpVersion? = AgpVersion.tryParse(androidProjectResult.agpVersion)

  val ideAndroidProject = androidProjectResult.ideAndroidProject
  val allVariantNames = androidProjectResult.allVariantNames
  val defaultVariantName: String? = androidProjectResult.defaultVariantName

  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  return AndroidModule.V2(
    agpVersion = agpVersion,
    buildName = androidProjectResult.buildName,
    buildNameMap = buildNameMap,
    buildIdMap = buildIdMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    androidVariantResolver = androidProjectResult.androidVariantResolver,
    variantFetcher = androidProjectResult.createVariantFetcher(),
    nativeModule = ideNativeModule,
    legacyApplicationIdModel = androidProjectResult.legacyApplicationIdModel,
  )
}

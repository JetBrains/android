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

import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import org.gradle.tooling.BuildController
import java.util.concurrent.locks.ReentrantLock

internal class NativeVariantsSyncActionWorker(
  private val buildInfo: BuildInfo,
  private val syncOptions: NativeVariantsSyncActionOptions,
  private val actionRunner: SyncActionRunner
) {
  private val modelCacheLock = ReentrantLock()
  private val internedModels = InternedModels(buildInfo.buildRootDirectory)
  // NativeVariantsSyncAction is only used with AGPs not supporting v2 models and thus not supporting parallel sync.

  fun fetchNativeVariantsAndroidModels(): List<GradleModelCollection> {
    val modelCache = modelCacheV1Impl(internedModels, buildInfo.buildFolderPaths, modelCacheLock)
    val nativeModules = actionRunner.runActions(
      buildInfo.projects.map { gradleProject ->
        ActionToRun(fun(controller: BuildController): GradleModule? {
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
        }, fetchesV1Models = true) // It should never run with Gradle V2 models.
      }.toList()
    ).filterNotNull()

    actionRunner.runActions(nativeModules.mapNotNull { it.getFetchSyncIssuesAction() })
    return nativeModules.map { it.prepare(IndexedModels(dynamicFeatureToBaseFeatureMap = emptyMap())) }
  }
}
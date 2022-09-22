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
package com.android.build.attribution.proto

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.google.common.annotations.VisibleForTesting
import java.io.File

class GradleBuildInvokerRequestRequestDataMessageConverter {
  companion object {
    fun transform(requestData: GradleBuildInvoker.Request.RequestData): BuildAnalysisResultsMessage.RequestData =
      BuildAnalysisResultsMessage.RequestData.newBuilder()
        .setBuildMode(transformBuildMode(requestData.mode))
        .setRootProjectPathString(requestData.rootProjectPath.path)
        .addAllGradleTasks(requestData.gradleTasks)
        .addAllEnv(requestData.env.map { transformEnv(it.key, it.value) })
        .addAllCommandLineArguments(requestData.commandLineArguments)
        .addAllJvmArguments(requestData.jvmArguments)
        .setIsPassParentEnvs(requestData.isPassParentEnvs)
        .build()

    fun construct(requestData: BuildAnalysisResultsMessage.RequestData)
      : GradleBuildInvoker.Request.RequestData {
      val buildMode = constructBuildMode(requestData.buildMode)
      val env = mutableMapOf<String, String>()
      requestData.envList.forEach { env[it.envKey] = it.envValue }
      return GradleBuildInvoker.Request.RequestData(
        mode = buildMode,
        rootProjectPath = File(requestData.rootProjectPathString),
        gradleTasks = requestData.gradleTasksList,
        jvmArguments = requestData.jvmArgumentsList,
        commandLineArguments = requestData.commandLineArgumentsList,
        env = env,
        isPassParentEnvs = requestData.isPassParentEnvs
      )
    }

    @VisibleForTesting
    fun transformBuildMode(mode: BuildMode?) = when (mode) {
      BuildMode.CLEAN -> BuildAnalysisResultsMessage.RequestData.BuildMode.CLEAN
      BuildMode.ASSEMBLE -> BuildAnalysisResultsMessage.RequestData.BuildMode.ASSEMBLE
      BuildMode.REBUILD -> BuildAnalysisResultsMessage.RequestData.BuildMode.REBUILD
      BuildMode.COMPILE_JAVA -> BuildAnalysisResultsMessage.RequestData.BuildMode.COMPILE_JAVA
      BuildMode.SOURCE_GEN -> BuildAnalysisResultsMessage.RequestData.BuildMode.SOURCE_GEN
      BuildMode.BUNDLE -> BuildAnalysisResultsMessage.RequestData.BuildMode.BUNDLE
      BuildMode.APK_FROM_BUNDLE -> BuildAnalysisResultsMessage.RequestData.BuildMode.APK_FROM_BUNDLE
      BuildMode.DEFAULT_BUILD_MODE -> BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED
      null -> BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED
    }

    private fun transformEnv(key: String, value: String) =
      BuildAnalysisResultsMessage.RequestData.Env.newBuilder()
        .setEnvKey(key)
        .setEnvValue(value)
        .build()

    @VisibleForTesting
    fun constructBuildMode(mode: BuildAnalysisResultsMessage.RequestData.BuildMode) =
      when (mode) {
        BuildAnalysisResultsMessage.RequestData.BuildMode.CLEAN -> BuildMode.CLEAN
        BuildAnalysisResultsMessage.RequestData.BuildMode.ASSEMBLE -> BuildMode.ASSEMBLE
        BuildAnalysisResultsMessage.RequestData.BuildMode.REBUILD -> BuildMode.REBUILD
        BuildAnalysisResultsMessage.RequestData.BuildMode.COMPILE_JAVA -> BuildMode.COMPILE_JAVA
        BuildAnalysisResultsMessage.RequestData.BuildMode.SOURCE_GEN -> BuildMode.SOURCE_GEN
        BuildAnalysisResultsMessage.RequestData.BuildMode.BUNDLE -> BuildMode.BUNDLE
        BuildAnalysisResultsMessage.RequestData.BuildMode.APK_FROM_BUNDLE -> BuildMode.APK_FROM_BUNDLE
        BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED -> null
        BuildAnalysisResultsMessage.RequestData.BuildMode.UNRECOGNIZED -> throw IllegalStateException("Unrecognized build mode")
        null -> throw IllegalStateException("Unrecognized build mode")
      }
  }
}
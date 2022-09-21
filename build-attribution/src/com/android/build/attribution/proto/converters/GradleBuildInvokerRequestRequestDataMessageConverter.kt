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
package com.android.build.attribution.proto.converters

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.proto.PairEnumFinder
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
    fun transformBuildMode(mode: BuildMode?): BuildAnalysisResultsMessage.RequestData.BuildMode =
      when (mode) {
        null -> BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED
        else -> PairEnumFinder.aToB(mode)
      }

    private fun transformEnv(key: String, value: String) =
      BuildAnalysisResultsMessage.RequestData.Env.newBuilder()
        .setEnvKey(key)
        .setEnvValue(value)
        .build()

    @VisibleForTesting
    fun constructBuildMode(mode: BuildAnalysisResultsMessage.RequestData.BuildMode) = when (mode) {
      BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED -> null
      else -> PairEnumFinder.bToA<BuildMode, BuildAnalysisResultsMessage.RequestData.BuildMode>(mode)
    }
  }
}
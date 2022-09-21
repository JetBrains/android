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
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.ide.common.attribution.BuildAnalyzerTaskCategoryIssue
import com.android.ide.common.attribution.TaskCategory
import com.android.tools.idea.gradle.util.BuildMode
import kotlin.reflect.KClass

class PairEnumFinder {
  companion object {
    private inline fun <reified A : Enum<A>, reified B : Enum<B>> getDefault() =
      A::class to EnumConverter(A::class.java, B::class.java)

    val permissibleConversions = mapOf<KClass<out Enum<*>>, EnumConverter<out Enum<*>, out Enum<*>>>(
      getDefault<AlwaysRunTaskData.Reason, BuildAnalysisResultsMessage.AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason>(),
      getDefault<PluginData.PluginType, BuildAnalysisResultsMessage.PluginData.PluginType>(),
      getDefault<DownloadsAnalyzer.DownloadStatus, BuildAnalysisResultsMessage.DownloadsAnalyzerResult.DownloadResult.DownloadStatus>(),
      getDefault<BuildMode, BuildAnalysisResultsMessage.RequestData.BuildMode>(),
      getDefault<ProjectConfigurationData.ConfigurationStep.Type, BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type>(),
      getDefault<TaskData.TaskExecutionMode, BuildAnalysisResultsMessage.TaskData.TaskExecutionMode>(),
      getDefault<TaskCategory, BuildAnalysisResultsMessage.TaskData.TaskCategory>(),
      getDefault<BuildAnalyzerTaskCategoryIssue, BuildAnalysisResultsMessage.TaskCategoryWarningsAnalyzerResult.BuildAnalyzerTaskCategoryIssue>()
    )

    inline fun <reified A : Enum<A>, reified B : Enum<B>> getConverter(keyClass: KClass<out A>): EnumConverter<A, B> {
      val protoConverter = permissibleConversions[keyClass] ?: throw IllegalStateException("Converter for class $keyClass not found")

      @Suppress("UNCHECKED_CAST")
      return protoConverter as EnumConverter<A, B>
    }

    inline fun <reified A : Enum<A>, reified B : Enum<B>> aToB(a: A): B {
      val converter = getConverter<A, B>(A::class)
      return converter.aToB(a)
    }

    inline fun <reified A : Enum<A>, reified B : Enum<B>> bToA(b: B): A {
      val converter = getConverter<A, B>(A::class)
      return converter.bToA(b)
    }
  }
}

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
package com.android.build.attribution

import com.android.build.attribution.analyzers.AlwaysRunTasksAnalyzer
import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.analyzers.NoncacheableTasksAnalyzer
import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.intellij.openapi.project.Project
import java.io.File
import java.util.UUID
import java.util.concurrent.Future

fun BuildAnalyzerStorageManager.getSuccessfulResult() = getLatestBuildAnalysisResults()  as BuildAnalysisResults
fun constructEmptyBuildResultsObject(buildSessionId: String, projectRoot: File, repositoryResults : List<DownloadsAnalyzer.RepositoryResult> = emptyList()): BuildAnalysisResults {
  return BuildAnalysisResults(
    GradleBuildInvoker.Request.RequestData(
      BuildMode.DEFAULT_BUILD_MODE,
      projectRoot,
      listOf(":assembleDebug")
    ),
    AnnotationProcessorsAnalyzer.Result(emptyList(), emptyList()),
    AlwaysRunTasksAnalyzer.Result(emptyList()),
    CriticalPathAnalyzer.Result(
      emptyList(),
      emptyList(),
      0,
      0
    ),
    NoncacheableTasksAnalyzer.Result(emptyList()),
    GarbageCollectionAnalyzer.Result(emptyList(), null, null),
    ProjectConfigurationAnalyzer.Result(
      emptyMap(),
      emptyList(),
      emptyMap()
    ),
    TasksConfigurationIssuesAnalyzer.Result(emptyList()),
    NoIncompatiblePlugins(emptyList()),
    JetifierUsageAnalyzerResult(AnalyzerNotRun),
    DownloadsAnalyzer.ActiveResult(repositoryResults = repositoryResults),
    TaskCategoryWarningsAnalyzer.IssuesResult(emptyList()),
    buildSessionId,
    emptyMap(),
    emptyMap()
  )
}

internal fun constructBuildAnalyzerResultData(project: Project,
                                             buildStartedTimestamp: Long = 12345,
                                             buildFinishedTimestamp: Long = 12345,
                                             buildID: String = UUID.randomUUID().toString()): BuildAnalyzerStorageManagerTest.BuildAnalyzerResultData {
  val taskContainer = TaskContainer()
  val pluginContainer = PluginContainer()
  val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
  val setPrivateField: (Any, String, Any) -> Unit = { classInstance: Any, fieldName: String, newValue: Any ->
    val field = classInstance.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(classInstance, newValue)
  }
  val criticalPathAnalyzer = analyzersProxy.criticalPathAnalyzer
  setPrivateField(criticalPathAnalyzer, "buildStartedTimestamp", buildStartedTimestamp)
  setPrivateField(criticalPathAnalyzer, "buildFinishedTimestamp", buildFinishedTimestamp)
  val request = GradleBuildInvoker.Request
    .builder(project, Projects.getBaseDirPath(project), "assembleDebug").build()
  return BuildAnalyzerStorageManagerTest.BuildAnalyzerResultData(analyzersProxy, buildID, BuildRequestHolder(request))
}

internal fun storeBuildAnalyzerResultData(project: Project,
                                         buildStartedTimestamp: Long = 12345,
                                         buildFinishedTimestamp: Long = 12345,
                                         buildID: String = UUID.randomUUID().toString()): Future<BuildAnalysisResults> {
  val result = constructBuildAnalyzerResultData(project, buildStartedTimestamp, buildFinishedTimestamp, buildID)
  return BuildAnalyzerStorageManager.getInstance(project).storeNewBuildResults(
    result.analyzersProxy,
    result.buildID,
    result.buildRequestHolder)
}
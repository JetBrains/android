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
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.analyzers.NoncacheableTasksAnalyzer
import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import java.io.File

fun BuildAnalyzerStorageManager.getSuccessfulResult() = getLatestBuildAnalysisResults()  as BuildAnalysisResults
fun constructEmptyBuildResultsObject(buildSessionId: String, projectRoot: File): BuildAnalysisResults {
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
    DownloadsAnalyzer.ActiveResult(repositoryResults = emptyList()),
    TaskCategoryWarningsAnalyzer.Result(listOf()),
    buildSessionId,
    emptyMap(),
    emptyMap()
  )
}
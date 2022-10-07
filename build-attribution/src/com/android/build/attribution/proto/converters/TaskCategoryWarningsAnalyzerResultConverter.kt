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
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.proto.PairEnumFinder
import com.android.buildanalyzer.common.TaskCategoryIssue

class TaskCategoryWarningsAnalyzerResultConverter {
  companion object {
    fun transform(taskCategoryWarningsAnalyzerResult: TaskCategoryWarningsAnalyzer.Result) =
      BuildAnalysisResultsMessage.TaskCategoryWarningsAnalyzerResult.newBuilder()
        .addAllTaskCategoryIssues(
          taskCategoryWarningsAnalyzerResult.taskCategoryIssues.map(this::transformTaskCategoryIssue))
        .build()

    fun construct(taskCategoryWarningsAnalyzerResult: BuildAnalysisResultsMessage.TaskCategoryWarningsAnalyzerResult): TaskCategoryWarningsAnalyzer.Result =
      TaskCategoryWarningsAnalyzer.Result(
        taskCategoryWarningsAnalyzerResult.taskCategoryIssuesList.map(this::constructTaskCategoryIssue))

    private fun transformTaskCategoryIssue(taskCategoryIssue: TaskCategoryIssue): BuildAnalysisResultsMessage.TaskCategoryWarningsAnalyzerResult.TaskCategoryIssue =
      PairEnumFinder.aToB(taskCategoryIssue)

    private fun constructTaskCategoryIssue(taskCategoryIssue: BuildAnalysisResultsMessage.TaskCategoryWarningsAnalyzerResult.TaskCategoryIssue): TaskCategoryIssue =
      PairEnumFinder.bToA(taskCategoryIssue)
  }
}
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
import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData

class TaskConfigurationAnalyzerResultMessageConverter {
  companion object {
    fun transform(tasksSharingOutputData: List<TasksSharingOutputData>)
      : BuildAnalysisResultsMessage.TasksConfigurationIssuesAnalyzerResult? =
      BuildAnalysisResultsMessage.TasksConfigurationIssuesAnalyzerResult.newBuilder()
        .addAllTasksSharingOutputData(tasksSharingOutputData.map(Companion::transformTasksSharingOutputData))
        .build()

    fun construct(
      tasksConfigurationAnalyzerResult: BuildAnalysisResultsMessage.TasksConfigurationIssuesAnalyzerResult,
      tasks: Map<String, TaskData>
    ): TasksConfigurationIssuesAnalyzer.Result {
      val tasksSharingOutputData = mutableListOf<TasksSharingOutputData>()
      for (task in tasksConfigurationAnalyzerResult.tasksSharingOutputDataList) {
        val outputFilePath = task.outputFilePath
        val taskList = task.taskIdListList.mapNotNull { tasks[it] }
        tasksSharingOutputData.add(TasksSharingOutputData(outputFilePath, taskList))
      }
      return TasksConfigurationIssuesAnalyzer.Result(tasksSharingOutputData)
    }

    private fun transformTasksSharingOutputData(tasksSharingOutputData: TasksSharingOutputData) =
      BuildAnalysisResultsMessage.TasksConfigurationIssuesAnalyzerResult.TasksSharingOutputData.newBuilder()
        .addAllTaskIdList(tasksSharingOutputData.taskList.map { it.getTaskPath() })
        .setOutputFilePath(tasksSharingOutputData.outputFilePath)
        .build()
  }
}
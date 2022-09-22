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

import com.android.build.attribution.BuildAnalysisResultsMessage.AlwaysRunTasksAnalyzerResult
import com.android.build.attribution.analyzers.AlwaysRunTasksAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.TaskData
import com.google.common.annotations.VisibleForTesting

class AlwaysRunTasksAnalyzerResultMessageConverter {
  companion object {
    fun transform(alwaysRunTasks: List<AlwaysRunTaskData>):
      AlwaysRunTasksAnalyzerResult? =
      AlwaysRunTasksAnalyzerResult.newBuilder()
        .addAllAlwaysRunTasksData(alwaysRunTasks.map { transformAlwaysRunTaskData(it) })
        .build()

    fun construct(
      alwaysRunTasksAnalyzerResult: AlwaysRunTasksAnalyzerResult,
      tasks: Map<String, TaskData>
    ): AlwaysRunTasksAnalyzer.Result {
      val alwaysRunTaskData = mutableListOf<AlwaysRunTaskData>()
      for (alwaysRunTaskDatum in alwaysRunTasksAnalyzerResult.alwaysRunTasksDataList) {
        val taskData = tasks[alwaysRunTaskDatum.taskId]
        val reason = constructAlwaysRunTaskReason(alwaysRunTaskDatum.reason)
        taskData?.let { AlwaysRunTaskData(it, reason) }?.let { alwaysRunTaskData.add(it) }
      }
      return AlwaysRunTasksAnalyzer.Result(alwaysRunTaskData)
    }

    private fun transformAlwaysRunTaskData(alwaysRunTaskData: AlwaysRunTaskData):
      AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData {
      val arTaskData = AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.newBuilder()
        .setReason((transformAlwaysRunTaskReason(alwaysRunTaskData.rerunReason)))
      arTaskData.taskId = alwaysRunTaskData.taskData.getTaskPath()
      return arTaskData.build()
    }

    private fun transformAlwaysRunTaskReason(reason: AlwaysRunTaskData.Reason) =
      when (reason) {
        AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS ->
          AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.NO_OUTPUTS_WITH_ACTIONS

        AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE ->
          AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UP_TO_DATE_WHEN_FALSE
      }

    @VisibleForTesting
    fun constructAlwaysRunTaskReason(reason: AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason) =
      when (reason) {
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.NO_OUTPUTS_WITH_ACTIONS -> AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UP_TO_DATE_WHEN_FALSE -> AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UNRECOGNIZED -> throw IllegalStateException("Unrecognised reason")
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UNSPECIFIED -> throw IllegalStateException("Unrecognised reason")
      }
  }
}
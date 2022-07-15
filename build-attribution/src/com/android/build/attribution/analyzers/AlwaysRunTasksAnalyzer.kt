/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult

/**
 * Analyzer for reporting tasks that always run due to misconfiguration.
 */
class AlwaysRunTasksAnalyzer(
  private val taskContainer: TaskContainer,
  private val pluginContainer: PluginContainer
) : BaseAnalyzer<AlwaysRunTasksAnalyzer.Result>(),
    BuildEventsAnalyzer,
    PostBuildProcessAnalyzer {
  private val alwaysRunTasksSet = HashSet<AlwaysRunTaskData>()

  override fun receiveEvent(event: ProgressEvent) {
    if (event is TaskFinishEvent && event.result is TaskSuccessResult) {
      (event.result as TaskSuccessResult).executionReasons?.forEach {
        when (it) {
          AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS.message -> alwaysRunTasksSet.add(
            AlwaysRunTaskData(getTask(event), AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS))
          AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE.message -> alwaysRunTasksSet.add(
            AlwaysRunTaskData(getTask(event), AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE))
        }
      }
    }
  }

  private fun getTask(event: TaskFinishEvent): TaskData {
    return taskContainer.getTask(event, pluginContainer)
  }

  override fun cleanupTempState() {
    alwaysRunTasksSet.clear()
  }

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalyzersProxy, studioProvidedInfo: StudioProvidedInfo) {
    // This has to run after all build data received as it uses taskType in filter and task type is received from BuildAttributionData file.
    ensureResultCalculated()
  }

  override fun calculateResult(): Result = Result(
    alwaysRunTasksSet.filter { applyIgnoredTasksFilter(it.taskData) }
  )

  data class Result(val alwaysRunTasks: List<AlwaysRunTaskData>) : AnalyzerResult
}

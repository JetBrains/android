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

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult

/**
 * Analyzer for reporting tasks that always run due to misconfiguration.
 */
class AlwaysRunTasksAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter,
                             taskContainer: TaskContainer,
                             pluginContainer: PluginContainer)
  : BaseAnalyzer(taskContainer, pluginContainer), BuildEventsAnalyzer {
  private val alwaysRunTasksSet = HashSet<AlwaysRunTaskData>()
  lateinit var alwaysRunTasks: List<AlwaysRunTaskData>
    private set

  override fun receiveEvent(event: ProgressEvent) {
    if (event is TaskFinishEvent && event.result is TaskSuccessResult) {
      (event.result as TaskSuccessResult).executionReasons?.forEach {
        when (it) {
          AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS.message -> alwaysRunTasksSet.add(
            AlwaysRunTaskData(getTask(event), AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS))
          AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS.message -> alwaysRunTasksSet.add(
            AlwaysRunTaskData(getTask(event), AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS))
          AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE.message -> alwaysRunTasksSet.add(
            AlwaysRunTaskData(getTask(event), AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE))
        }
      }
    }
  }

  override fun onBuildSuccess() {
    alwaysRunTasks = alwaysRunTasksSet.filter {
      warningsFilter.applyAlwaysRunTaskFilter(it.taskData) && applyIgnoredTasksFilter(it.taskData)
    }
    alwaysRunTasksSet.clear()
  }

  override fun onBuildFailure() {
    alwaysRunTasksSet.clear()
  }
}

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

import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData

/**
 * Analyzer for reporting tasks that are not cacheable.
 */
class NoncacheableTasksAnalyzer(
  private val taskContainer: TaskContainer
) : BaseAnalyzer<NoncacheableTasksAnalyzer.Result>(), BuildAttributionReportAnalyzer {
  private var noncacheableTasks: List<TaskData> = emptyList()

  override fun cleanupTempState() {
    noncacheableTasks = emptyList()
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    noncacheableTasks = androidGradlePluginAttributionData.noncacheableTasks
      .mapNotNull(taskContainer::getTask)
      .filter { task ->
        applyIgnoredTasksFilter(task)
      }
    ensureResultCalculated()
  }

  override fun calculateResult(): Result = Result(noncacheableTasks)

  data class Result(
    val noncacheableTasks: List<TaskData>
  ) : AnalyzerResult
}

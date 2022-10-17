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

import com.android.build.attribution.data.TaskData
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * This is a marker interface for the output of any build analyzer.
 */
interface AnalyzerResult

/**
 * Base class for all build analyzers, provides logic for computing the final result lazily and caching it,
 * requests cleaning internal temporal state when it is not needed anymore.
 * Sub-classes should implement the result computation and cleaning internal state logic.
 * Any new analyzer should extend this.
 *
 * The general flow for each analyzer would be:
 * 1) collect all data required from events and other input sources in internal variables.
 * 2) convert this collected data into a final result object in [calculatingResult] when requested.
 * 3) clean up collected temporal data in [cleanupTempState], called automatically when data is no more needed.
 */
abstract class BaseAnalyzer<T : AnalyzerResult> {

  /** Cache for calculated result. */
  private var _result: T? = null

  /** Marks that result computation is in progress to detect dependency cycles. */
  private var calculatingResult = false

  val result: T
    get() {
      ensureResultCalculated()
      return _result!!
    }

  fun ensureResultCalculated() {
    if (calculatingResult) throw ResultComputationLoopException()
    if (_result == null) {
      calculatingResult = true
      _result = calculateResult()
      cleanupTempState()
      calculatingResult = false
    }
  }

  protected abstract fun calculateResult(): T

  abstract fun cleanupTempState()

  fun onBuildStart() {
    _result = null
    calculatingResult = false
    cleanupTempState()
  }

  fun onBuildFailure() = cleanupTempState()

  /**
   * Filter to ignore certain tasks or tasks from certain plugins.
   */
  protected fun applyIgnoredTasksFilter(task: TaskData): Boolean {
    // ignore tasks from our plugins
    return !task.originPlugin.isAndroidPlugin() &&
           // ignore tasks from Gradle plugins
           !task.originPlugin.isGradlePlugin() &&
           // This task is not cacheable and runs all the time intentionally on invoking "clean". We should not surface this as an issue.
           !(task.taskName == "clean" && task.originPlugin.idName == LifecycleBasePlugin::class.java.canonicalName) &&
           // ignore custom delete tasks
           task.taskType != org.gradle.api.tasks.Delete::class.java.canonicalName &&
           // Workaround for using configuration caching as gradle doesn't send plugin information with task-finished events
           // TODO(b/244314356) patch plugin information from the build attribution file in builds with configuration cache
          !task.isAndroidTask() && !task.isGradleTask()
  }

  class ResultComputationLoopException : Exception("Loop detected in build analyzer computation dependencies, see stacktrace.")
}

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
package com.android.build.attribution

import com.android.build.attribution.data.SuppressedWarnings
import com.android.build.attribution.data.TaskData
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project

@State(name = "BuildAttributionWarningsFilter")
class BuildAttributionWarningsFilter : PersistentStateComponent<SuppressedWarnings> {
  private var suppressedWarnings: SuppressedWarnings = SuppressedWarnings()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BuildAttributionWarningsFilter {
      return project.getService(BuildAttributionWarningsFilter::class.java)
    }
  }

  /**
   * We identify the task that we suppress warnings for by its task class name, if not available then the task name.
   */
  private fun getTaskIdentifier(task: TaskData): String {
    if (task.taskType == TaskData.UNKNOWN_TASK_TYPE) {
      return task.taskName
    }
    return task.taskType
  }

  fun applyAlwaysRunTaskFilter(task: TaskData): Boolean {
    return !suppressedWarnings.alwaysRunTasks.contains(Pair(getTaskIdentifier(task), task.originPlugin.displayName))
  }

  fun applyNonIncrementalAnnotationProcessorFilter(annotationProcessorClassName: String): Boolean {
    return !suppressedWarnings.nonIncrementalAnnotationProcessors.contains(annotationProcessorClassName)
  }

  fun applyNoncacheableTaskFilter(task: TaskData): Boolean {
    return !suppressedWarnings.noncacheableTasks.contains(Pair(getTaskIdentifier(task), task.originPlugin.displayName))
  }

  var suppressNoGCSettingWarning: Boolean
    get() = suppressedWarnings.noGCSettingWarning
    set(value) { suppressedWarnings.noGCSettingWarning = value }

  fun suppressAlwaysRunTaskWarning(taskIdentifier: String, pluginDisplayName: String) {
    suppressedWarnings.alwaysRunTasks.add(Pair(taskIdentifier, pluginDisplayName))
  }

  fun suppressNonIncrementalAnnotationProcessorWarning(annotationProcessorClassName: String) {
    suppressedWarnings.nonIncrementalAnnotationProcessors.add(annotationProcessorClassName)
  }

  fun suppressNoncacheableTaskWarning(taskIdentifier: String, pluginDisplayName: String) {
    suppressedWarnings.noncacheableTasks.add(Pair(taskIdentifier, pluginDisplayName))
  }

  fun unsuppressAlwaysRunTaskWarning(taskName: String, pluginDisplayName: String) {
    suppressedWarnings.alwaysRunTasks.remove(Pair(taskName, pluginDisplayName))
  }

  fun unsuppressNonIncrementalAnnotationProcessorWarning(annotationProcessorClassName: String) {
    suppressedWarnings.nonIncrementalAnnotationProcessors.remove(annotationProcessorClassName)
  }

  fun unsuppressNoncacheableTaskWarning(taskName: String, pluginDisplayName: String) {
    suppressedWarnings.noncacheableTasks.remove(Pair(taskName, pluginDisplayName))
  }

  fun suppressNoGCSettingWarning() {
    suppressedWarnings.noGCSettingWarning = true
  }

  override fun getState(): SuppressedWarnings? {
    return suppressedWarnings
  }

  override fun loadState(state: SuppressedWarnings) {
    suppressedWarnings = state
  }
}

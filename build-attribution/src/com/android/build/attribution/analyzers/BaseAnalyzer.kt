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

import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.task.TaskFinishEvent

abstract class BaseAnalyzer(private val taskContainer: TaskContainer, private val pluginContainer: PluginContainer) {
  open fun onBuildStart() {
    taskContainer.clear()
    pluginContainer.clear()
  }

  protected fun getPlugin(pluginType: PluginData.PluginType, displayName: String, projectPath: String): PluginData {
    if (pluginType == PluginData.PluginType.SCRIPT) {
      return pluginContainer.getPlugin(pluginType, "$projectPath:$displayName")
    }
    return pluginContainer.getPlugin(pluginType, displayName)
  }

  protected fun getPlugin(pluginIdentifier: PluginIdentifier, projectPath: String): PluginData {
    return pluginContainer.getPlugin(pluginIdentifier, projectPath)
  }

  protected fun getTask(taskPath: String): TaskData? {
    return taskContainer.getTask(taskPath)
  }

  protected fun getTask(event: TaskFinishEvent): TaskData {
    return taskContainer.getTask(event, pluginContainer)
  }

  protected fun anyTask(predicate: (TaskData) -> Boolean) = taskContainer.any(predicate)

  /**
   * Filter to ignore certain tasks or tasks from certain plugins.
   */
  protected fun applyIgnoredTasksFilter(task: TaskData): Boolean {
    // ignore tasks from our plugins
    return !isAndroidPlugin(task.originPlugin) &&
           // ignore tasks from Gradle plugins
           !isGradlePlugin(task.originPlugin) &&
           // This task is not cacheable and runs all the time intentionally on invoking "clean". We should not surface this as an issue.
           !(task.taskName == "clean" && task.originPlugin.displayName == LifecycleBasePlugin::class.java.canonicalName) &&
           // ignore custom delete tasks
           task.taskType != org.gradle.api.tasks.Delete::class.java.canonicalName
  }
}

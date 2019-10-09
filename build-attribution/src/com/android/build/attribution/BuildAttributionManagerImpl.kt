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

import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.analyzers.BuildEventsAnalyzersWrapper
import com.android.build.attribution.data.TaskContainer
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildContentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.gradle.tooling.events.ProgressEvent
import java.io.File
import java.time.Duration

class BuildAttributionManagerImpl(
  private val myProject: Project,
  private val myBuildContentManager: BuildContentManager
) : BuildAttributionManager {
  private val taskContainer = TaskContainer()
  @get:VisibleForTesting
  val analyzersProxy = BuildEventsAnalyzersProxy(BuildAttributionWarningsFilter.getInstance(myProject), taskContainer)
  private val analyzersWrapper = BuildEventsAnalyzersWrapper(analyzersProxy.getBuildEventsAnalyzers(),
                                                             analyzersProxy.getBuildAttributionReportAnalyzers())

  override fun onBuildStart() {
    analyzersWrapper.onBuildStart()
  }

  override fun onBuildSuccess(attributionFilePath: String) {
    val attributionData = AndroidGradlePluginAttributionData.load(File(attributionFilePath))
    if (attributionData != null) {
      taskContainer.updateTasksData(attributionData)
    }
    analyzersWrapper.onBuildSuccess(attributionData)

    // TODO: add proper UI
    logBuildAttributionResults()
  }

  override fun onBuildFailure() {
    analyzersWrapper.onBuildFailure()
  }

  override fun statusChanged(event: ProgressEvent?) {
    if (event == null) return

    analyzersWrapper.receiveEvent(event)
  }

  private fun logBuildAttributionResults() {
    val stringBuilder = StringBuilder()

    analyzersProxy.getNonIncrementalAnnotationProcessorsData().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Non incremental annotation processors:")
        it.forEach { processor -> stringBuilder.appendln(processor.className + " " + processor.compilationDuration) }
      }
    }

    analyzersProxy.getTasksCriticalPath().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Tasks critical path:")
        it.forEach { taskData ->
          val percentage = taskData.executionTime * 100 / analyzersProxy.getTotalBuildTime()
          stringBuilder.append("Task ${taskData.getTaskPath()} from ${taskData.originPlugin}")
            .appendln(", time ${Duration.ofMillis(taskData.executionTime)} ($percentage%)")
        }
      }
    }

    analyzersProxy.getPluginsCriticalPath().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Plugins determining build duration:")
        it.forEach { pluginBuildData ->
          val percentage = pluginBuildData.buildDuration * 100 / analyzersProxy.getTotalBuildTime()
          stringBuilder.append("${pluginBuildData.plugin}, time ${Duration.ofMillis(pluginBuildData.buildDuration)}")
            .appendln(" ($percentage%)")
        }
      }
    }

    analyzersProxy.getPluginsSlowingConfiguration().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Plugins slowing configuration:")
        it.forEach { projectConfigurationData ->
          stringBuilder.appendln("> project ${projectConfigurationData.project}:")

          projectConfigurationData.pluginsConfigurationData.forEach { pluginConfigurationData ->
            val percentage = pluginConfigurationData.configurationDuration.toMillis() * 100 / projectConfigurationData.totalConfigurationTime

            stringBuilder.append("> ${pluginConfigurationData.plugin} took ${pluginConfigurationData.configurationDuration} ")
              .appendln("($percentage%)")
          }
        }
      }
    }

    analyzersProxy.getAlwaysRunTasks().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Always-run tasks:")
        it.forEach { alwaysRunTaskData ->
          stringBuilder.append(
            "Task ${alwaysRunTaskData.taskData.getTaskPath()} from ${alwaysRunTaskData.taskData.originPlugin} ")
            .appendln("runs on every build because ${alwaysRunTaskData.rerunReason.message}")
        }
      }
    }

    analyzersProxy.getNoncacheableTasks().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Non-cacheable tasks:")
        it.forEach { taskData ->
          stringBuilder.appendln(
            "Task ${taskData.getTaskPath()} from ${taskData.originPlugin} is not cacheable and will run even if its inputs are unchanged")
        }
      }
    }

    analyzersProxy.getTasksSharingOutput().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Configuration Issues:")
        it.forEach { entry ->
          stringBuilder.append("Tasks ")
          entry.taskList.forEachIndexed { index, taskData ->
            if (index != 0) {
              stringBuilder.append(", ")
            }
            stringBuilder.append("${taskData.getTaskPath()} from ${taskData.originPlugin}")
          }
          stringBuilder.appendln(
            " declare the same output ${entry.outputFilePath}")
        }
      }
    }

    if (stringBuilder.isNotEmpty()) {
      Logger.getInstance(this::class.java).warn("Build attribution analysis results:\n$stringBuilder")
    }
  }

}
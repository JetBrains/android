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

import com.android.annotations.concurrency.UiThread
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.analyzers.BuildEventsAnalyzersWrapper
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.ui.BuildAttributionTreeView
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.builder.BuildAttributionReportBuilder
import com.android.build.attribution.ui.filters.BuildAttributionOutputLinkFilter
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentImpl
import org.gradle.tooling.events.ProgressEvent
import java.io.File
import java.time.Duration

class BuildAttributionManagerImpl(
  private val project: Project,
  private val buildContentManager: BuildContentManager
) : BuildAttributionManager {
  private val taskContainer = TaskContainer()
  private val pluginContainer = PluginContainer()

  @get:VisibleForTesting
  val analyzersProxy = BuildEventsAnalyzersProxy(BuildAttributionWarningsFilter.getInstance(project), taskContainer, pluginContainer)
  private val analyzersWrapper = BuildEventsAnalyzersWrapper(analyzersProxy.getBuildEventsAnalyzers(),
                                                             analyzersProxy.getBuildAttributionReportAnalyzers())

  private var buildContent: Content? = null
  private var reportUiData: BuildAttributionReportUiData? = null

  override fun onBuildStart() {
    analyzersWrapper.onBuildStart()
  }

  override fun onBuildSuccess(attributionFilePath: String) {
    val buildFinishedTimestamp = System.currentTimeMillis()
    val attributionData = AndroidGradlePluginAttributionData.load(File(attributionFilePath))
    if (attributionData != null) {
      taskContainer.updateTasksData(attributionData)
    }
    analyzersWrapper.onBuildSuccess(attributionData)

    logBuildAttributionResults()

    reportUiData = BuildAttributionReportBuilder(analyzersProxy, buildFinishedTimestamp).build()
    ApplicationManager.getApplication().invokeLater { createUiTab() }
  }

  override fun onBuildFailure() {
    analyzersWrapper.onBuildFailure()
  }

  override fun statusChanged(event: ProgressEvent?) {
    if (event == null) return

    analyzersWrapper.receiveEvent(event)
  }

  @UiThread
  private fun createUiTab() {
    reportUiData?.let {
      val view = BuildAttributionTreeView(project, it)
      val content = buildContent
      if (content != null && content.isValid) {
        content.component = view.component
      }
      else {
        buildContent = ContentImpl(view.component, "Build Speed", true)
        buildContentManager.addContent(buildContent)
      }
      view.setInitialSelection()
    }
  }

  override fun openResultsTab() {
    ApplicationManager.getApplication().invokeLater {
      if (buildContent?.isValid != true) {
        createUiTab()
      }
      ApplicationManager.getApplication().invokeLater {
        buildContentManager.setSelectedContent(buildContent, true, true, false) {}
      }
    }
  }

  override fun buildOutputLine(): String = BuildAttributionOutputLinkFilter.INSIGHTS_AVAILABLE_LINE

  private fun logBuildAttributionResults() {
    val stringBuilder = StringBuilder()

    analyzersProxy.getNonIncrementalAnnotationProcessorsData().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Non incremental annotation processors:")
        it.forEach { processor -> stringBuilder.appendln(processor.className + " " + processor.compilationDuration) }
      }
    }

    analyzersProxy.getCriticalPathTasks().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Tasks critical path:")
        it.forEach { taskData ->
          val percentage = taskData.executionTime * 100 / analyzersProxy.getTotalBuildTimeMs()
          stringBuilder.append("Task ${taskData.getTaskPath()} from ${taskData.originPlugin}")
            .appendln(", time ${Duration.ofMillis(taskData.executionTime)} ($percentage%)")
        }
      }
    }

    analyzersProxy.getCriticalPathPlugins().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Plugins determining build duration:")
        it.forEach { pluginBuildData ->
          val percentage = pluginBuildData.buildDuration * 100 / analyzersProxy.getTotalBuildTimeMs()
          stringBuilder.append("${pluginBuildData.plugin}, time ${Duration.ofMillis(pluginBuildData.buildDuration)}")
            .appendln(" ($percentage%)")
        }
      }
    }

    fun printPluginConfigurationData(pluginConfigurationData: PluginConfigurationData, stringBuilder: StringBuilder, prefix: String) {
      stringBuilder.append(prefix + "${pluginConfigurationData.plugin} (${pluginConfigurationData.configurationDuration})")
      if (pluginConfigurationData.isSlowingConfiguration) {
        stringBuilder.append(" *SLOW*")
      }
      if (pluginConfigurationData.nestedPluginsConfigurationData.isEmpty()) {
        stringBuilder.appendln()
      }
      else {
        stringBuilder.appendln(" {")
        pluginConfigurationData.nestedPluginsConfigurationData.forEach { printPluginConfigurationData(it, stringBuilder, "$prefix  ") }
        stringBuilder.appendln("$prefix}")
      }
    }

    analyzersProxy.getProjectsConfigurationData().let {
      if (it.isNotEmpty()) {
        stringBuilder.appendln("Project configuration:")
        it.forEach { projectConfigurationData ->
          stringBuilder.appendln("project ${projectConfigurationData.project} (${projectConfigurationData.totalConfigurationTime}):")

          projectConfigurationData.pluginsConfigurationData.forEach { pluginConfigurationData ->
            printPluginConfigurationData(pluginConfigurationData, stringBuilder, " ")
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

    analyzersProxy.getNonCacheableTasks().let {
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
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
package com.android.build.attribution.ui.controllers

import com.android.annotations.concurrency.UiThread
import com.android.build.attribution.ui.BuildAttributionIssueReportingDialog
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.TaskIssueReportGenerator
import com.android.build.attribution.ui.data.TaskUiData
import com.android.tools.idea.actions.SubmitBugReportAction
import com.android.tools.idea.gradle.project.ProjectStructure
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

interface TaskIssueReporter {
  fun reportIssue(taskData: TaskUiData)
}

class TaskIssueReporterImpl(
  reportData: BuildAttributionReportUiData,
  private val project: Project,
  private val analytics: BuildAttributionUiAnalytics
) : TaskIssueReporter {

  private val generator = TaskIssueReportGenerator(
    reportData,
    { SubmitBugReportAction.getDescription(project) },
    { ProjectStructure.getInstance(project).androidPluginVersions.allVersions }
  )

  @UiThread
  override fun reportIssue(taskData: TaskUiData) {
    val task = object : Task.Modal(project, "Collecting Data", false) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = "Collecting Feedback Information"
        indicator.isIndeterminate = true
        val reportText = generator.generateReportText(taskData)
        ApplicationManager.getApplication().invokeLater {
          BuildAttributionIssueReportingDialog(project, analytics, taskData.pluginName, reportText).show()
        }
      }
    }
    task.queue()
  }
}

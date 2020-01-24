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
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.tools.idea.actions.SendFeedbackAction
import com.android.tools.idea.gradle.project.ProjectStructure
import com.intellij.openapi.project.Project

interface TaskIssueReporter {
  fun reportIssue(taskIssue: TaskIssueUiData)
}

class TaskIssueReporterImpl(
  reportData: BuildAttributionReportUiData,
  private val project: Project,
  private val analytics: BuildAttributionUiAnalytics
) : TaskIssueReporter {

  private val generator = TaskIssueReportGenerator(
    reportData,
    { SendFeedbackAction.getDescription(project) },
    { ProjectStructure.getInstance(project).androidPluginVersions.allVersions }
  )

  @UiThread
  override fun reportIssue(taskIssue: TaskIssueUiData) {
    BuildAttributionIssueReportingDialog(project, analytics, taskIssue.task.pluginName, generator.generateReportText(taskIssue)).show()
  }
}

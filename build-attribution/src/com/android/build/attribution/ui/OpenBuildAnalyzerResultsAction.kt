/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.attribution.ui

import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.text.DateFormatUtil

/**
 * Opens window with a list of previous Build Analyses results
 */
class OpenBuildAnalyzerResultsAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (!StudioFlags.BUILD_ANALYZER_HISTORY.get() || project == null) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      e.presentation.isEnabled = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val buildAnalyzerStorageManager = BuildAnalyzerStorageManager.getInstance(project)
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(
        buildAnalyzerStorageManager.getListOfHistoricBuildDescriptors().toList()
          .sortedByDescending { it.buildFinishedTimestamp })
      .setTitle("Build Analysis Results")
      .setItemChosenCallback { buildDescriptor ->
        BuildAttributionUiManager.getInstance(project)
          .showBuildAnalysisReportById(buildDescriptor.buildSessionID)
      }
      .setRenderer(
        SimpleListCellRenderer.create { label, buildDescriptor, _ ->
          val totalBuildTimeSec = durationStringHtml(buildDescriptor.totalBuildTimeMs)
          val briefId = buildDescriptor.buildSessionID.substringBefore('-')
          val formattedTime = DateFormatUtil.formatDateTime(buildDescriptor.buildFinishedTimestamp)
          label.text = """
            |<html>
              |<body>
                |Build duration: ${totalBuildTimeSec}
                |&emsp&emsp<font color="gray">$briefId</font>
                |<br>
                |$formattedTime
              |</body>
            |</html>
          """.trimMargin() //TODO make alignment right to briefId
        })
      .createPopup()
      .showInFocusCenter()
  }
}
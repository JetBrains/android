/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.tools.idea.diagnostics.report.DiagnosticsSummaryFileProvider.Companion.buildFileList
import com.android.tools.idea.diagnostics.report.FileInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.CreateDiagnosticReportDialog
import com.intellij.ide.actions.CollectZippedLogsAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

class CreateDiagnosticReportAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    if (!StudioFlags.ENABLE_NEW_COLLECT_LOGS_DIALOG.get()) {
      CollectZippedLogsAction().actionPerformed(e)
      return
    }

    val list: List<FileInfo> =
      try {
        buildFileList(e.project)
      }
      catch (ex: Exception) {
        val message = "Error creating diagnostic file list: " + ex.message
        Messages.showErrorDialog(e.project, message, "Diagnostics Summary Files")
        return
      }

    val dialog = CreateDiagnosticReportDialog(e.project, list)
    dialog.show()
  }
}

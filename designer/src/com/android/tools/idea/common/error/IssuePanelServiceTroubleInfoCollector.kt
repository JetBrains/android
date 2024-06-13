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
package com.android.tools.idea.common.error

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.troubleshooting.TroubleInfoCollector
import com.intellij.util.ui.UIUtil

/** A [TroubleInfoCollector] for [IssuePanelService] status. */
class IssuePanelServiceTroubleInfoCollector : TroubleInfoCollector {
  override fun collectInfo(project: Project): String {
    val issuePanelService = IssuePanelService.getInstance(project)
    val allIssues =
      UIUtil.invokeAndWaitIfNeeded(Computable { issuePanelService.getSharedPanelIssues() })
    val output = StringBuilder("IssuePanelService: nIssues=${allIssues.size}")
    val issuePanel = ProblemsView.getToolWindow(project)?.contentManager?.selectedContent
    val selectedIssue =
      UIUtil.invokeAndWaitIfNeeded(
        Computable {
          val selectedNode =
            DataManager.getInstance()
              .getDataContext(issuePanel?.component)
              .getData(PlatformCoreDataKeys.SELECTED_ITEM)
          (selectedNode as? IssueNode)?.issue
        }
      )
    allIssues.forEach {
      output.appendLine(
        """
      Issue: selected=${it == selectedIssue} sev=${it.severity}
      - ${it.summary}
      ${it.description.prependIndent("  |")}
    """
          .trimIndent()
          .prependIndent()
      )
    }

    return output.toString()
  }
}

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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants

class BuildAttributionIssueReportingDialog(
  project: Project,
  private val analytics: BuildAttributionUiAnalytics,
  private val pluginName: String,
  private val reportText: String
) : DialogWrapper(project) {

  init {
    title = "Plugin Issue Report"
    setCancelButtonText(CommonBundle.getCloseButtonText())
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val warningText = "<html><body>" +
                      "If you are comfortable sharing the information below, copy the report and send it to the developer of plugin " +
                      "${pluginName}, so they may troubleshoot the detected issues. " +
                      "Also include a description of what you were doing at the time." +
                      "</body></html>"

    val messageArea = JEditorPane("text/plain", reportText).apply {
      border = JBUI.Borders.empty(3)
      isEditable = false
      background = UIUtil.getComboBoxDisabledBackground()
    }
    return JBUI.Panels.simplePanel(10, 10).apply {
      addToTop(JLabel(warningText, warningIcon(), SwingConstants.LEFT))
      addToCenter(ScrollPaneFactory.createScrollPane(
        messageArea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      ))
      preferredSize = JBDimension(700, 600)
    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(CopyToClipboardAction(), getCancelAction())
  }

  override fun doCancelAction() {
    analytics.reportingWindowClosed()
    super.doCancelAction()
  }

  private inner class CopyToClipboardAction : AbstractAction(IdeBundle.message("button.copy")) {
    init {
      putValue(Action.SHORT_DESCRIPTION, IdeBundle.message("description.copy.text.to.clipboard"))
      putValue(FOCUSED_ACTION, true)
      putValue(DEFAULT_ACTION, true)
    }

    override fun actionPerformed(e: ActionEvent) {
      analytics.reportingWindowCopyButtonClicked()
      val s = StringUtil.convertLineSeparators(reportText)
      CopyPasteManager.getInstance().setContents(StringSelection(s))
    }
  }
}


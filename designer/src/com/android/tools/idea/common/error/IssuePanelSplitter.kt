/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import javax.swing.JComponent

/**
 * A [com.intellij.ui.JBSplitter] that display the [IssuePanel] from the provided [surface] on bottom and the provided [JComponent] on top.
 */
class IssuePanelSplitter(
  file: VirtualFile?,
  private val surface: DesignSurface<*>,
  content: JComponent) : OnePixelSplitter(true, 1f, 0.5f, 1f) {

  init {
    Disposer.register(surface) {
      if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get() && file != null) {
        IssuePanelService.getInstance(surface.project).unregisterFile(file)
      }
    }
    firstComponent = content
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      secondComponent = null
      if (file != null) {
        IssuePanelService.getInstance(surface.project).registerFile(file, surface.name)
      }
    }
    else {
      val issuePanel = surface.issuePanel
      issuePanel.addEventListener(createIssueEventListener(issuePanel))
      setHonorComponentsMinimumSize(true)
      secondComponent = issuePanel
    }
  }

  private fun updateSplitter(isExpanded: Boolean, height: Int) {
    val showDivider = isExpanded
    isShowDividerIcon = showDivider
    isShowDividerControls = showDivider
    setResizeEnabled(showDivider)

    proportion = if (!isExpanded) 1f
    else {
      val newProportion = 1 - height / parent.height.toFloat()
      Math.max(0.5f, newProportion)
    }
  }

  private fun createIssueEventListener(issuePanel: IssuePanel): IssuePanel.EventListener {
    return object : IssuePanel.EventListener {
      override fun onPanelExpanded(isExpanded: Boolean) {
        surface.analyticsManager.trackIssuePanel(!isExpanded)
        updateSplitter(isExpanded, issuePanel.suggestedHeight)
      }

      override fun onIssueExpanded(issue: Issue?, isExpanded: Boolean) { }
    }
  }
}

// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.uibuilder.error

import com.android.tools.idea.common.analytics.NlUsageTrackerManager
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.intellij.ui.OnePixelSplitter
import javax.swing.JComponent

/**
 * A [com.intellij.ui.JBSplitter] that display the [IssuePanel] from the provided [surface] on bottom
 * and the provided [JComponent] on top.
 */
class IssuePanelSplitter(
    val surface: DesignSurface,
    content: JComponent) : OnePixelSplitter(true, 1f, 0.5f, 1f) {

  init {
    val issuePanel = surface.issuePanel
    issuePanel.setMinimizeListener(createIssuePanelMinimizeListener(issuePanel))
    setHonorComponentsMinimumSize(true)
    firstComponent = content
    secondComponent = issuePanel
  }

  override fun setDragging(dragging: Boolean) {
    super.setDragging(dragging)

    // Do not resize surface's content while dragging the splitter
    surface.setSkipResizeContent(dragging)
  }

  private fun updateSplitter(isMinimized: Boolean, height: Int) {
    val showDivider = !isMinimized
    isShowDividerIcon = showDivider
    isShowDividerControls = showDivider
    setResizeEnabled(showDivider)

    proportion = if (isMinimized) 1f
    else {
      val newProportion = 1 - height / parent.height.toFloat()
      Math.max(0.5f, newProportion)
    }
  }

  private fun createIssuePanelMinimizeListener(issuePanel: IssuePanel) = IssuePanel.MinimizeListener { isMinimized ->
    NlUsageTrackerManager.getInstance(surface).logAction(
        if (isMinimized)
          LayoutEditorEvent.LayoutEditorEventType.MINIMIZE_ERROR_PANEL
        else
          LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL)
    updateSplitter(isMinimized, issuePanel.suggestedHeight)

    // Do not resize surface's content while opening the splitter
    surface.skipContentResizeOnce()
  }
}
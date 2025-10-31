/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.tools.profilers.memory

import com.android.tools.profilers.ProfilerDropDownComponent
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.NoneFilter
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * A dropdown for filtering memory captures by issue type (e.g. Leaks, Duplicates).
 */
internal class IssueTypeFilterMenu(selection: MemoryCaptureSelection) : BaseFilterMenu(
  selection,
  NoneFilter.displayName,
  "Filter by issue types",
  { filter, executor -> selection.setIssueTypeFilter(filter, executor) },
  CaptureSelectionAspect.CURRENT_ISSUE_TYPE_FILTER
) {
  override fun createComponent(dropDown: ProfilerDropDownComponent<CaptureObjectInstanceFilter?>): JPanel {
    val label = JLabel("Filter by:").apply {
      foreground = UIUtil.getLabelDisabledForeground()
      border = JBUI.Borders.empty(1, 12, 0, 0)
    }
    return JPanel(createToolbarLayout()).apply {
      val separator = JPanel().apply {
        preferredSize = Dimension(JBUI.scale(1), JBUI.scale(16))
        background = JBColor.border()
      }
      add(separator)
      add(label)
      add(dropDown)
    }
  }

  override fun getAvailableFilters(capture: CaptureObject?) = capture?.supportedIssueTypeFilters?.toList() ?: emptyList()
  override fun getCurrentFilter(): CaptureObjectInstanceFilter? = selection.selectedIssueTypeFilter
}
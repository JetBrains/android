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
import com.android.tools.profilers.memory.adapters.instancefilters.AllClassTypeFilter
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * A dropdown for filtering memory captures by class type (e.g. project, system, or all classes).
 */
internal class ClassTypeFilterMenu(selection: MemoryCaptureSelection) : BaseFilterMenu(
  selection,
  AllClassTypeFilter.displayName,
  "Filter by class types",
  { filter, executor -> selection.setClassTypeFilter(filter, executor) },
  CaptureSelectionAspect.CURRENT_CLASS_TYPE_FILTER
) {
  override fun createComponent(dropDown: ProfilerDropDownComponent<CaptureObjectInstanceFilter?>): JPanel {
    val label = JLabel("Class:").apply {
      foreground = UIUtil.getLabelDisabledForeground()
      border = JBUI.Borders.empty(1, 12, 0, 0)
    }
    return JPanel(createToolbarLayout()).apply {
      add(label)
      add(dropDown)
    }
  }

  override fun getAvailableFilters(capture: CaptureObject?) = capture?.supportedClassTypeFilters?.toList() ?: emptyList()
  override fun getCurrentFilter(): CaptureObjectInstanceFilter? = selection.selectedClassTypeFilter
}
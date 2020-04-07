/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Base view class for summary tab details.
 *
 * Includes a common section table. Subclasses can use [#addRowToCommonSection] to add a row to the common section. Or
 * add more components to [#component].
 *
 * @param <T>analysis tab model type, e.g. [CaptureNodeAnalysisSummaryTabModel]</T>
 */
open class SummaryDetailsViewBase<T : CpuAnalysisSummaryTabModel<*>>(parentView: JComponent, val tabModel: T) {
  val observer = AspectObserver()
  val component: JPanel
  private val commonSection: JPanel
  private val commonSectionLayout: TabularLayout = TabularLayout("*,*")
  private var commonSectionRows = 0

  init {
    commonSection = JPanel(commonSectionLayout).apply { isOpaque = false }
    component = JPanel(TabularLayout("*", "Fit")).apply {
      background = primaryContentBackground
      border = JBUI.Borders.empty(0, 12)
      add(commonSection, TabularLayout.Constraint(0, 0))
    }
  }

  /**
   * Add a row to the main section. The main section is the first section of the Summary Tab that looks like this:
   *
   * +---------+----------+
   * |Range    | 1:00-2:00|
   * |Duration |       1 m|
   * |Data Type|    Thread|
   * +---------+----------+
   */
  protected fun addRowToCommonSection(name: String, value: JComponent) {
    // Add blank row as padding.
    val blankRow = JPanel().apply { isOpaque = false }
    commonSectionLayout.setRowSizing(commonSectionRows * 2, "8px")
    commonSection.add(blankRow, TabularLayout.Constraint(commonSectionRows * 2, 0, 1, 2))

    // Add data row.
    commonSectionLayout.setRowSizing(commonSectionRows * 2 + 1, "Fit-")
    commonSection.add(JLabel(name), TabularLayout.Constraint(commonSectionRows * 2 + 1, 0))
    commonSection.add(value, TabularLayout.Constraint(commonSectionRows * 2 + 1, 1))

    // Keep number of rows for setting the tabular layout parameters.
    ++commonSectionRows
  }

  /**
   * @return string representation of the time range, relative to capture range.
   */
  fun formatTimeRangeAsString(range: Range): String {
    return String.format("%s - %s",
                         TimeFormatter.getSimplifiedClockString(range.min.toLong() - tabModel.captureRange.min.toLong()),
                         TimeFormatter.getSimplifiedClockString(range.max.toLong() - tabModel.captureRange.min.toLong()))
  }
}
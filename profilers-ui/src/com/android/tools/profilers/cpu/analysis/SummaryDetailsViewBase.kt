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
import com.android.tools.profilers.StudioProfilersView
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Base view class for summary tab details.
 * This class inherits `JPanel` instead of containing its instance so that its `observer` has the same lifetime
 * as the displayed JPanel.
 *
 * Includes a common section table. Subclasses can use [#addRowToCommonSection] to add a row to the common section. Or
 * add more components to [#component].
 *
 * @param <T>analysis tab model type, e.g. [CaptureNodeAnalysisSummaryTabModel]</T>
 */
open class SummaryDetailsViewBase<T : CpuAnalysisSummaryTabModel<*>>(val profilersView: StudioProfilersView, val tabModel: T) : JPanel() {
  val observer = AspectObserver()
  private val commonSection: JPanel

  init {
    commonSection = JPanel(TabularLayout("*,*", "Fit-").setVGap(COMMON_SECTION_ROW_PADDING_PX)).apply {
      isOpaque = false
    }
    layout = TabularLayout("*", "Fit").setVGap(SECTION_PADDING_PX)
    background = primaryContentBackground
    border = JBUI.Borders.empty(8, 12)
    add(commonSection, TabularLayout.Constraint(0, 0))
  }

  /**
   * Add a row to the common section. The common section is the first section of the Summary Tab that looks like this:
   *
   * +---------+----------+
   * |Range    | 1:00-2:00|
   * |Duration |       1 m|
   * |Data Type|    Thread|
   * +---------+----------+
   */
  protected fun addRowToCommonSection(name: String, value: JComponent) {
    val rows = commonSection.componentCount / 2
    commonSection.add(JLabel(name), TabularLayout.Constraint(rows, 0))
    commonSection.add(value, TabularLayout.Constraint(rows, 1))
  }

  /**
   * Add a section to the Summary Tab.
   */
  protected fun addSection(section: JComponent) {
    add(section, TabularLayout.Constraint(componentCount, 0))
  }

  /**
   * @return string representation of the time range, relative to capture range.
   */
  protected fun formatTimeRangeAsString(range: Range): String {
    return String.format("%s - %s",
                         TimeFormatter.getSemiSimplifiedClockString(range.min.toLong() - tabModel.captureRange.min.toLong()),
                         TimeFormatter.getSemiSimplifiedClockString(range.max.toLong() - tabModel.captureRange.min.toLong()))
  }

  companion object {
    const val SECTION_PADDING_PX = 24
    const val COMMON_SECTION_ROW_PADDING_PX = 8
  }
}
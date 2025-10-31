/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.ProfilerDropDownComponent
import com.android.tools.profilers.ProfilerFlows
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.Selection
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

class MemoryClassGrouping(private val selection: MemoryCaptureSelection) : AspectObserver() {
  private val groupingFlow: MutableStateFlow<Selection<ClassGrouping>> =
    ProfilerFlows.createMutableStateFlow(Selection(selection.classGrouping, availableGroupings))

  val component: JPanel

  init {
    selection.aspect.addDependency(this)
      .onChange(CaptureSelectionAspect.CLASS_GROUPING, ::groupingChanged)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, ::groupingChanged) // Refresh options when capture changes

    val dropDown = ProfilerDropDownComponent(
      ClassGrouping.ARRANGE_BY_CLASS.toString(),
      "Arrange by",
      null,
      groupingFlow,
      null,
      { grouping -> selection.classGrouping = grouping },
      { grouping -> grouping?.label ?: "" }
    )

    component = JPanel(createToolbarLayout()).apply {
      add(JLabel("Arrange by:").apply {
        border = JBUI.Borders.empty(1, 12, 0, 2)
        foreground = UIUtil.getLabelDisabledForeground()
      })
      add(dropDown)
      add(JPanel().apply {
        preferredSize = Dimension(JBUI.scale(1), JBUI.scale(16))
        background = JBColor.border()
      })
    }

    groupingChanged()
  }

  fun groupingChanged() {
    groupingFlow.value = Selection(selection.classGrouping, availableGroupings)
  }

  private val availableGroupings: List<ClassGrouping>
    get() {
      val capture: CaptureObject = selection.selectedCapture ?: // ARRANGE_BY_CLASS is generally always supported.
                                   return listOf(ClassGrouping.ARRANGE_BY_CLASS)
      return ClassGrouping.entries.filter { capture.isGroupingSupported(it) }
    }
}
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
package com.android.tools.profilers

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.instructions.HyperlinkInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode
import com.android.tools.profilers.cpu.CpuCaptureMetadata
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import javax.swing.JPanel

class EnergyProfilerMigrationService(val profilers: StudioProfilers) {

  private val HEADER = "Energy Profiler has moved."
  private val MIGRATING_FROM = "energy data"
  private val MIGRATING_TO = "System Trace"

  private val HEADER_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(24f)
  private val TEXT_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(16f)

  val isMigrationEnabled = profilers.ideServices.featureConfig.systemTracePowerProfilerDisplayMode != PowerProfilerDisplayMode.HIDE

  fun getMigrationPanel(
    rowIndex: Int,
    layout: TabularLayout,
    cursorContainer: ((Container, Cursor) -> Container)
  ): JPanel {
    val panel = JPanel(BorderLayout())
    panel.isOpaque = true
    panel.border = ProfilerLayout.MONITOR_BORDER
    panel.minimumSize = Dimension(0, JBUI.scale(50))
    panel.background = ProfilerColors.DEFAULT_BACKGROUND
    layout.setRowSizing(rowIndex, "100*")
    val instructionsPanel = getInstructionsPanel(panel = panel, cursorContainer = cursorContainer)
    panel.add(instructionsPanel)
    return panel
  }

  private fun getInstructionsPanel(panel: JPanel, cursorContainer: ((Container, Cursor) -> Container)): InstructionsPanel {
    val normalTextFontMetrics = panel.getFontMetrics(TEXT_FONT)
    val italicTextFontMetrics = panel.getFontMetrics(TEXT_FONT.asItalic())

    val instructionsPanelBuilder = InstructionsPanel.Builder(
      TextInstruction(panel.getFontMetrics(HEADER_FONT), HEADER),
      NewRowInstruction(0),
      TextInstruction(normalTextFontMetrics, "Use the "),
      HyperlinkInstruction(TEXT_FONT, MIGRATING_TO, ::openCpuProfilerWithSystemTraceEnabled),
      TextInstruction(normalTextFontMetrics, "to see ${MIGRATING_FROM}."),
      // Negative vertical margin value is used to collapse space between two stacked instructions.
      NewRowInstruction((-0.5 * TEXT_FONT.size).toInt()),
      TextInstruction(italicTextFontMetrics, "Available on Android Q and above")
    ).setColors(UIUtil.getInactiveTextColor(), null)

    instructionsPanelBuilder.setCursorSetter(cursorContainer)
    return instructionsPanelBuilder.build()
  }

  private fun openCpuProfilerWithSystemTraceEnabled() {
    val stage = CpuProfilerStage(profilers, CpuCaptureMetadata.CpuProfilerEntryPoint.ENERGY_DEPRECATION_LINK)
    // The 'updateProfilingConfigurations' method populates the default profiling configurations.
    // It is called on entering the CpuProfilerStage, but to have access to the configs now, we
    // will prematurely call it. The method has no side effects and thus can be called multiple times.
    stage.profilerConfigModel.updateProfilingConfigurations()
    val systemTraceConfig = stage.profilerConfigModel.defaultProfilingConfigurations.find {
      // Perfetto is the underlying technology/trace type of the system trace.
      it.traceType == ProfilingConfiguration.TraceType.PERFETTO
    }
    // If the system trace config is found it is pre-selected.
    systemTraceConfig?.let {
      stage.profilerConfigModel.profilingConfiguration = systemTraceConfig
    }
    // Whether or not the system trace config is pre-selected, we jump to the cpu profiler stage.
    profilers.stage = stage
  }
}
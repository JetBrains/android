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
package com.android.tools.profilers.cpu

import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StudioProfilersView
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

// TODO(b/298713032): Rename this class and rewrite the UI to be in Compose.
class CpuProfilerStageViewV2(profilersView: StudioProfilersView, stage: CpuProfilerStage) : StageView<CpuProfilerStage>(profilersView,
                                                                                                                        stage) {
  init {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.add(Box.createVerticalGlue())
    panel.background = ProfilerColors.DEFAULT_BACKGROUND

    val recordingLabel = JLabel()
    recordingLabel.horizontalAlignment = SwingConstants.CENTER
    recordingLabel.verticalAlignment = SwingConstants.TOP
    recordingLabel.alignmentX = Component.CENTER_ALIGNMENT
    recordingLabel.font = ProfilerFonts.H1_FONT
    recordingLabel.foreground = JBColor(0x000000, 0xFFFFFF)
    recordingLabel.text = "\uD83D\uDD34 Recording..."
    panel.add(recordingLabel)

    val stopRecordingButton = JButton("Stop")
    stopRecordingButton.horizontalAlignment = SwingConstants.CENTER
    stopRecordingButton.verticalAlignment = SwingConstants.CENTER
    stopRecordingButton.alignmentX = Component.CENTER_ALIGNMENT
    stopRecordingButton.addActionListener {
      stage.stop()
    }
    panel.add(stopRecordingButton)

    panel.add(Box.createVerticalGlue())

    component.add(panel, BorderLayout.CENTER)
  }

  override fun getToolbar() = JPanel()
}
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

import com.android.tools.profilers.tasks.ProfilerTaskTabs
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The top-level view of the home tab in the Profiler tool window.
 */
class StudioProfilersHomeView(project: Project, profilers: StudioProfilers, ideProfilerComponents: IdeProfilerComponents) {
  val panel: JPanel = JPanel()

  init {
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.add(Box.createVerticalGlue())
    panel.background = ProfilerColors.DEFAULT_BACKGROUND

    val title = JLabel()
    title.horizontalAlignment = SwingConstants.CENTER
    title.verticalAlignment = SwingConstants.TOP
    title.alignmentX = Component.CENTER_ALIGNMENT
    title.font = ProfilerFonts.H1_FONT
    title.foreground = JBColor(0x000000, 0xFFFFFF)
    title.text = "New Compose-based home page goes here"
    panel.add(title)

    val createTaskTabButton = JButton("Create task tab")
    createTaskTabButton.horizontalAlignment = SwingConstants.CENTER
    createTaskTabButton.verticalAlignment = SwingConstants.CENTER
    createTaskTabButton.alignmentX = Component.CENTER_ALIGNMENT
    createTaskTabButton.addActionListener {
      ProfilerTaskTabs.open(project = project, taskType = ProfilerTaskType.UNSPECIFIED)
    }
    panel.add(createTaskTabButton)

    panel.add(Box.createVerticalGlue())
  }
}
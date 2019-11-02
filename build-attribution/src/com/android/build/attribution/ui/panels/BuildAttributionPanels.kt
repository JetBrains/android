/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.panels

import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.percentageString
import com.android.utils.HtmlBuilder
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val CRITICAL_PATH_LINK = "https://developer.android.com/r/tools/build-attribution/critical-path"


fun pluginInfoPanel(pluginUiData: CriticalPathPluginUiData): JComponent =
  JBPanel<JBPanel<*>>(VerticalLayout(15)).apply {
    add(commonPluginInfo(pluginUiData))
  }

private fun commonPluginInfo(data: CriticalPathPluginUiData): JBLabel {
  val pluginText = HtmlBuilder()
    .openHtmlBody()
    .add("This plugin has ${data.criticalPathTasks.size} ${pluralize("task", data.criticalPathTasks.size)} on the critical path. ")
    .addLink("Learn more", CRITICAL_PATH_LINK)
    .newline()
    .add("Total duration ${data.criticalPathDuration.durationString()} / ${data.criticalPathDuration.percentageString()}")
    .closeHtmlBody()
  return JBLabel(pluginText.html).setCopyable(true)
}

fun pluginTasksListPanel(pluginData: CriticalPathPluginUiData): JComponent = JBPanel<JBPanel<*>>().apply {
  layout = VerticalLayout(5, SwingConstants.LEFT)
  add(commonPluginInfo(pluginData))
}

private fun commonTaskInfo(taskData: TaskUiData): JComponent {
  val text = HtmlBuilder()
    .openHtmlBody()
    .add("Module: ${taskData.module}")
    .newline()
    .add("Plugin: ${taskData.pluginName}")
    .newline()
    .add("Type: ${taskData.taskType}")
    .newline()
    .add("Duration: ${taskData.executionTime.durationString()} / ${taskData.executionTime.percentageString()}")
    .newline()
    .add("Executed incrementally: ${if (taskData.executedIncrementally) "Yes" else "No"}")
    .closeHtmlBody()
  return JBLabel(text.html).setCopyable(true).setAllowAutoWrapping(true)
}

fun taskInfoPanel(taskData: TaskUiData): JPanel {
  val infoPanel = JPanel(GridBagLayout())
  val taskInfo = commonTaskInfo(taskData)
  val reasonsToRunHeader = JBLabel("Reason task ran").withFont(JBUI.Fonts.label().asBold())
  val reasonsList = reasonsToRunList(taskData)

  val c = GridBagConstraints()
  c.weightx = 1.0
  c.gridx = 0
  c.gridy = 0
  c.anchor = GridBagConstraints.FIRST_LINE_START
  c.fill = GridBagConstraints.HORIZONTAL
  c.insets = JBUI.insetsBottom(8)
  infoPanel.add(taskInfo, c)

  c.gridy = 1
  c.insets = JBUI.insetsTop(8)
  infoPanel.add(reasonsToRunHeader, c)

  c.gridy = 3
  c.insets = JBUI.insetsTop(8)
  c.fill = GridBagConstraints.BOTH
  c.weighty = 1.0
  infoPanel.add(reasonsList, c)
  return infoPanel
}

private fun reasonsToRunList(taskData: TaskUiData) = JBLabel().apply {
  setCopyable(true)
  setAllowAutoWrapping(true)
  verticalTextPosition = SwingConstants.TOP
  text = createReasonsText(taskData.reasonsToRun)
}

private fun createReasonsText(reasons: List<String>): String = if (reasons.isEmpty()) {
  "No info"
}
else {
  reasons.joinToString(separator = "<br/>") { wrapPathToSpans(it).replace("\n", "<br>") }
}

/**
 * Wraps long path to spans to make it possible to auto-wrap to a new line
 */
private fun wrapPathToSpans(text: String): String = "<p>${text.replace("/", "<span>/</span>")}</p>"

fun verticalRuler(): JPanel = JBPanel<JBPanel<*>>()
  .withBackground(OnePixelDivider.BACKGROUND)
  .withPreferredWidth(1)
  .withMaximumWidth(1)
  .withMinimumWidth(1)

fun criticalPathHeader(prefix: String, duration: String): JComponent = JPanel().apply {
  layout = HorizontalLayout(10, SwingConstants.BOTTOM)
  add(headerLabel("${prefix} Determining Build Duration (${duration}) / Critical Path"))
  add(HyperlinkLabel("Learn More").apply { setHyperlinkTarget(CRITICAL_PATH_LINK) })
}

fun headerLabel(text: String): JLabel = JBLabel(text).withFont(JBUI.Fonts.label(13f).asBold())


/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.tools.adtui.RegexTextField
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.ddms.DevicePanel
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.LayoutManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.GroupLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.ToolTipManager

private const val QUICK_FILTER_HISTORY_PROPERTY_NAME = "logcatQuickFilterHistory"
private const val QUICK_FILTER_HISTORY_SIZE = 10

/**
 * A header for the Logcat panel.
 */
internal class LogcatHeaderPanel(
  project: Project,
  val logcatPresenter: LogcatPresenter,
  deviceContext: DeviceContext,
  packageNamesProvider: PackageNamesProvider
) : JPanel() {
  private val deviceComboBox: Component
  private val quickFilterTextField = RegexTextField(logcatPresenter, QUICK_FILTER_HISTORY_PROPERTY_NAME, QUICK_FILTER_HISTORY_SIZE)

  // TODO(aalbert): This is a temp UX. Will probably be changed to something that can select individual apps from the project as well.
  private val projectAppsCheckbox = object : JCheckBox(LogcatBundle.message("logcat.filter.project.apps")) {
    init {
      ToolTipManager.sharedInstance().registerComponent(this)
    }

    override fun getToolTipText(event: MouseEvent): String =
      packageNamesProvider.getPackageNames().joinToString("<br/>", "<html>", "</html>")
  }
  private val filterParser = LogcatFilterParser(project)

  init {
    // TODO(aalbert): DevicePanel uses the project as a disposable parent. This doesn't work well with multiple tabs/splitters where we
    //  have an instance per tab/split and would like to be disposed when the container closes.
    //  It's not yet clear if we will and up using DevicePanel or not, so will not make changes to it just yet.
    val devicePanel = DevicePanel(project, deviceContext)
    deviceComboBox = devicePanel.deviceComboBox

    quickFilterTextField.addOnChangeListener(object : RegexTextField.OnChangeListener {
      override fun onChange(component: RegexTextField) {
        logcatPresenter.applyFilter(filterParser.parse(quickFilterTextField.text))
      }
    })
    projectAppsCheckbox.addItemListener { logcatPresenter.setShowOnlyProjectApps(projectAppsCheckbox.isSelected) }

    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(event: ComponentEvent) {
        layout = if (width > JBUI.scale(500)) createWideLayout() else createNarrowLayout()
      }
    })
  }

  private fun createWideLayout(): LayoutManager {
    val layout = GroupLayout(this)
    val minWidth = ComboBox<String>().minimumSize.width
    val maxWidth = JBUI.scale(400)

    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    layout.setHorizontalGroup(
      layout.createSequentialGroup()
        .addComponent(deviceComboBox, minWidth, GroupLayout.DEFAULT_SIZE, maxWidth)
        .addComponent(quickFilterTextField, minWidth, GroupLayout.DEFAULT_SIZE, maxWidth)
        .addComponent(projectAppsCheckbox)
    )
    layout.setVerticalGroup(
      layout.createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(deviceComboBox)
        .addComponent(quickFilterTextField)
        .addComponent(projectAppsCheckbox)
    )
    return layout
  }

  private fun createNarrowLayout(): LayoutManager {
    val layout = GroupLayout(this)

    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    layout.setHorizontalGroup(
      layout.createParallelGroup()
        .addGroup(layout.createSequentialGroup().addComponent(deviceComboBox))
        .addGroup(layout.createSequentialGroup().addComponent(quickFilterTextField))
        .addGroup(layout.createSequentialGroup().addComponent(projectAppsCheckbox))
    )
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup().addComponent(deviceComboBox))
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER).addComponent(quickFilterTextField))
        .addGroup(layout.createParallelGroup().addComponent(projectAppsCheckbox))
    )
    return layout
  }
}

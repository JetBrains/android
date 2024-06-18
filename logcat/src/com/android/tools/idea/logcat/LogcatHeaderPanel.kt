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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.logcat.devices.DeviceComboBox
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem
import com.android.tools.idea.logcat.filters.FilterTextField
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.LayoutManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.GroupLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A header for the Logcat panel. */
internal class LogcatHeaderPanel(
  project: Project,
  val logcatPresenter: LogcatPresenter,
  private val filterParser: LogcatFilterParser,
  filter: String,
  filterMatchCase: Boolean,
  initialItem: DeviceComboItem?,
) : JPanel() {
  val deviceComboBox = DeviceComboBox(project, initialItem)
  private val filterTextField =
    FilterTextField(project, logcatPresenter, filterParser, filter, filterMatchCase)
  private val helpIcon: JLabel = JLabel(AllIcons.General.ContextHelp)

  init {
    filterTextField.font = Font.getFont(Font.MONOSPACED)

    addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
          layout = if (width > JBUI.scale(500)) createWideLayout() else createNarrowLayout()
        }
      }
    )
    layout = createWideLayout()

    helpIcon.let {
      toolTipText = LogcatBundle.message("logcat.help.tooltip")
      it.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            BrowserUtil.browse("https://d.android.com/r/studio-ui/logcat/help")
          }
        }
      )
    }

    val scope = AndroidCoroutineScope(logcatPresenter)
    scope.launch {
      @Suppress("OPT_IN_USAGE")
      filterTextField.trackFilterUpdates().debounce(100).collect {
        withContext(uiThread) {
          logcatPresenter.applyFilter(filterParser.parse(it.filter, it.matchCase))
        }
      }
    }
  }

  var filter: String
    get() = filterTextField.text
    set(value) {
      filterTextField.text = value
    }

  val filterMatchCase: Boolean
    get() = filterTextField.matchCase

  private fun createWideLayout(): LayoutManager {
    val layout = GroupLayout(this)

    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    layout.setHorizontalGroup(
      layout
        .createSequentialGroup()
        .addComponent(
          deviceComboBox,
          ComboBox<String>().minimumSize.width,
          GroupLayout.DEFAULT_SIZE,
          JBUI.scale(400),
        )
        .addComponent(
          filterTextField,
          JBUI.scale(350),
          GroupLayout.DEFAULT_SIZE,
          GroupLayout.DEFAULT_SIZE,
        )
        .addComponent(helpIcon)
    )
    layout.setVerticalGroup(
      layout
        .createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(deviceComboBox)
        .addComponent(filterTextField)
        .addComponent(helpIcon)
    )
    return layout
  }

  private fun createNarrowLayout(): LayoutManager {
    val layout = GroupLayout(this)
    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    layout.setHorizontalGroup(
      layout
        .createParallelGroup()
        .addGroup(layout.createSequentialGroup().addComponent(deviceComboBox))
        .addGroup(layout.createSequentialGroup().addComponent(filterTextField))
    )
    layout.setVerticalGroup(
      layout
        .createSequentialGroup()
        .addGroup(layout.createParallelGroup().addComponent(deviceComboBox))
        .addGroup(
          layout.createParallelGroup(GroupLayout.Alignment.CENTER).addComponent(filterTextField)
        )
    )
    return layout
  }
}

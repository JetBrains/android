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

import com.android.adblib.AdbSession
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.devices.DeviceComboBox
import com.android.tools.idea.logcat.devices.DeviceComboBoxDeviceTracker
import com.android.tools.idea.logcat.filters.FilterTextComponent
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.Flow
import java.awt.Font
import java.awt.LayoutManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.GroupLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * A header for the Logcat panel.
 */
internal class LogcatHeaderPanel(
  project: Project,
  val logcatPresenter: LogcatPresenter,
  private val filterParser: LogcatFilterParser,
  filter: String,
  initialDevice: Device?,
  adbSession: AdbSession,
) : JPanel() {
  private val deviceComboBox = DeviceComboBox(
    logcatPresenter,
    initialDevice,
    DeviceComboBoxDeviceTracker(project, initialDevice, adbSession))

  private val filterComponent: FilterTextComponent = FilterTextComponent.createComponent(project, logcatPresenter, filterParser, filter)

  private val helpIcon: JLabel = JLabel(AllIcons.General.ContextHelp)

  init {
    filterComponent.apply {
      font = Font.getFont(Font.MONOSPACED)
      addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          runInEdt {
            logcatPresenter.applyFilter(filterParser.parse(text))
          }
        }
      })
    }

    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(event: ComponentEvent) {
        layout = if (width > JBUI.scale(500)) createWideLayout() else createNarrowLayout()
      }
    })

    helpIcon.let {
      toolTipText = LogcatBundle.message("logcat.help.tooltip")
      it.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          BrowserUtil.browse("https://d.android.com/r/studio-ui/logcat/help")
        }
      })
    }
  }

  fun trackSelectedDevice(): Flow<Device> = deviceComboBox.trackSelectedDevice()

  var filter: String
    get() = filterComponent.text
    set(value) {
      filterComponent.text = value
    }

  fun getSelectedDevice(): Device? = deviceComboBox.selectedItem as? Device

  private fun createWideLayout(): LayoutManager {
    val layout = GroupLayout(this)
    val minWidth = ComboBox<String>().minimumSize.width
    val maxWidth = JBUI.scale(400)

    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    layout.setHorizontalGroup(
      layout.createSequentialGroup()
        .addComponent(deviceComboBox, minWidth, GroupLayout.DEFAULT_SIZE, maxWidth)
        .addComponent(filterComponent.component)
        .addComponent(helpIcon)
    )
    layout.setVerticalGroup(
      layout.createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(deviceComboBox)
        .addComponent(filterComponent.component)
        .addComponent(helpIcon)
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
        .addGroup(layout.createSequentialGroup().addComponent(filterComponent.component))
    )
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup().addComponent(deviceComboBox))
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER).addComponent(filterComponent.component))
    )
    return layout
  }
}

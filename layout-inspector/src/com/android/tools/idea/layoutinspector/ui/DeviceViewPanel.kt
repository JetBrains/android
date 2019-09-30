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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomLabelAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.profiler.proto.Common
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

private const val MAX_ZOOM = 300
private const val MIN_ZOOM = 30

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(
  val layoutInspector: LayoutInspector,
  val viewSettings: DeviceViewSettings
) : JPanel(BorderLayout()), Zoomable, DataProvider {

  private val client = layoutInspector.client

  override val scale
    get() = viewSettings.scaleFraction

  override val screenScalingFactor = 1f

  private val contentPanel = DeviceViewContentPanel(layoutInspector, viewSettings)
  private val scrollPane = JBScrollPane(contentPanel)

  init {
    scrollPane.border = JBUI.Borders.empty()

    client.registerProcessChanged { ApplicationManager.getApplication().invokeLater { ActionToolbarImpl.updateAllToolbarsImmediately() } }

    add(createToolbar(), BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)
  }

  override fun zoom(type: ZoomType): Boolean {
    val position = scrollPane.viewport.viewPosition.apply { translate(scrollPane.viewport.width / 2, scrollPane.viewport.height / 2) }
    position.x = (position.x / scale).toInt()
    position.y = (position.y / scale).toInt()
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> viewSettings.scalePercent = 50  // TODO
      ZoomType.ACTUAL -> viewSettings.scalePercent = 100
      ZoomType.IN -> viewSettings.scalePercent += 10
      ZoomType.OUT -> viewSettings.scalePercent -= 10
    }
    scrollPane.viewport.revalidate()

    position.x = (position.x * scale).toInt()
    position.y = (position.y * scale).toInt()
    position.translate(-scrollPane.viewport.width / 2, -scrollPane.viewport.height / 2)
    scrollPane.viewport.viewPosition = position
    return true
  }

  override fun canZoomIn() = viewSettings.scalePercent < MAX_ZOOM

  override fun canZoomOut() = viewSettings.scalePercent > MIN_ZOOM

  override fun canZoomToFit() = true

  override fun canZoomToActual() = viewSettings.scalePercent < 100 && canZoomIn() || viewSettings.scalePercent > 100 && canZoomOut()

  override fun getData(dataId: String): Any? {
    if (ZOOMABLE_KEY.`is`(dataId)) {
      return this
    }
    return null
  }

  private fun createToolbar(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)!!

    val leftPanel = AdtPrimaryPanel(BorderLayout())
    val leftGroup = DefaultActionGroup()
    leftGroup.add(SelectProcessAction(client))
    leftGroup.add(object : DropDownAction(null, "View options", StudioIcons.Common.VISIBILITY_INLINE) {
      init {
        add(object : ToggleAction("Show borders") {
          override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.isEnabled = client.isConnected
          }

          override fun isSelected(event: AnActionEvent): Boolean {
            return viewSettings.drawBorders
          }

          override fun setSelected(event: AnActionEvent, state: Boolean) {
            viewSettings.drawBorders = state
            repaint()
          }
        })
      }
    })
    leftGroup.add(PauseLayoutInspectorAction(client))
    leftPanel.add(ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true).component,
                  BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)

    val rightGroup = DefaultActionGroup()
    rightGroup.add(object : AnAction("reset") {
      override fun actionPerformed(event: AnActionEvent) {
        viewSettings.viewMode = viewSettings.viewMode.next
      }

      override fun update(event: AnActionEvent) {
        event.presentation.icon = viewSettings.viewMode.icon
      }
    })
    rightGroup.add(ZoomOutAction)
    rightGroup.add(ZoomLabelAction)
    rightGroup.add(ZoomInAction)
    rightGroup.add(ZoomToFitAction)
    val toolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorRight", rightGroup, true)
    toolbar.setTargetComponent(this)
    panel.add(toolbar.component, BorderLayout.EAST)
    return panel
  }

  private class SelectProcessAction(val client: InspectorClient) :
    DropDownAction("Select Process", "Select a process to connect to.", AllIcons.General.Add) {

    private var currentProcess = Common.Process.getDefaultInstance()

    override fun update(event: AnActionEvent) {
      if (currentProcess != client.selectedProcess) {
        val processName = client.selectedProcess.name.substringAfterLast('.')
        val actionName = if (client.selectedProcess == Common.Process.getDefaultInstance()) "Select Process" else processName
        currentProcess = client.selectedProcess
        event.presentation.text = actionName
      }
    }

    override fun updateActions(): Boolean {
      removeAll()

      // Rebuild the action tree.
      val processesMap = client.loadProcesses()
      if (processesMap.isEmpty()) {
        val noDeviceAction = object : AnAction("No devices detected") {
          override fun actionPerformed(event: AnActionEvent) {}
        }
        noDeviceAction.templatePresentation.isEnabled = false
        add(noDeviceAction)
      }
      else {
        for (stream in processesMap.keys) {
          val deviceAction = object : DropDownAction(buildDeviceName(stream.device), null, null) {
            override fun displayTextInToolbar() = true
          }
          val processes = processesMap[stream]
          if (processes == null || processes.isEmpty()) {
            val noProcessAction = object : AnAction("No debuggable processes detected") {
              override fun actionPerformed(event: AnActionEvent) {}
            }
            noProcessAction.templatePresentation.isEnabled = false
            deviceAction.add(noProcessAction)
          }
          else {
            val sortedProcessList = processes.sortedWith(compareBy({ it.name }, { it.pid }))
            for (process in sortedProcessList) {
              val processAction = object : ToggleAction("${process.name} (${process.pid})") {
                override fun isSelected(event: AnActionEvent): Boolean {
                  return process == client.selectedProcess && stream == client.selectedStream
                }

                override fun setSelected(event: AnActionEvent, state: Boolean) {
                  if (state) {
                    client.attach(stream, process)
                  }
                }
              }
              deviceAction.add(processAction)
            }
          }
          add(deviceAction)
        }
        add(object : AnAction("Stop inspector") {
          override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = client.isConnected
          }

          override fun actionPerformed(event: AnActionEvent) {
            client.disconnect()
          }
        })
      }
      return true
    }

    override fun displayTextInToolbar() = true

    private fun buildDeviceName(device: Common.Device): String {
      val deviceNameBuilder = StringBuilder()
      val manufacturer = device.manufacturer
      var model = device.model
      val serial = device.serial
      val suffix = String.format("-%s", serial)
      if (model.endsWith(suffix)) {
        model = model.substring(0, model.length - suffix.length)
      }
      if (!StringUtil.isEmpty(manufacturer)) {
        deviceNameBuilder.append(manufacturer)
        deviceNameBuilder.append(" ")
      }
      deviceNameBuilder.append(model)

      return deviceNameBuilder.toString()
    }
  }

  private class PauseLayoutInspectorAction(val client: InspectorClient) : CheckboxAction("Live updates") {

    override fun update(event: AnActionEvent) {
      super.update(event)
      event.presentation.isEnabled = client.isConnected
    }

    // Display as "Live updates ON" when disconnected to indicate the default value after the inspector is connected to the device.
    override fun isSelected(event: AnActionEvent): Boolean {
      return !client.isConnected || client.isCapturing
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (!client.isConnected) {
        return
      }
      val command = if (client.isCapturing) LayoutInspectorCommand.Type.STOP else LayoutInspectorCommand.Type.START
      client.execute(command)
    }
  }
}

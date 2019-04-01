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
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.profiler.proto.Common
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(private val layoutInspector: LayoutInspector) : JPanel(BorderLayout()), Zoomable, DataProvider {
  private val client = layoutInspector.client

  enum class ViewMode(val icon: Icon) {
    FIXED(StudioIcons.LayoutEditor.Extras.ROOT_INLINE),
    X_ONLY(StudioIcons.DeviceConfiguration.SCREEN_WIDTH),
    XY(StudioIcons.DeviceConfiguration.SMALLEST_SCREEN_SIZE);

    val next: ViewMode
      get() = enumValues<ViewMode>()[(this.ordinal + 1).rem(enumValues<ViewMode>().size)]
  }

  var viewMode = ViewMode.XY

  override var scale: Double = .5

  override val screenScalingFactor = 1f

  private var drawBorders = true

  private val showBordersCheckBox = object : CheckboxAction("Show borders") {
    override fun isSelected(e: AnActionEvent): Boolean {
      return drawBorders
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      drawBorders = state
      repaint()
    }
  }

  private val myProcessSelectionAction = SelectProcessAction(client)

  val contentPanel = DeviceViewContentPanel(layoutInspector, scale, viewMode)
  private val scrollPane = JBScrollPane(contentPanel)

  init {
    scrollPane.border = JBUI.Borders.empty()

    layoutInspector.modelChangeListeners.add(::modelChanged)

    add(createToolbar(), BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)
  }

  override fun zoom(type: ZoomType): Boolean {
    val position = scrollPane.viewport.viewPosition.apply { translate(scrollPane.viewport.width / 2, scrollPane.viewport.height / 2) }
    position.x = (position.x / scale).toInt()
    position.y = (position.y / scale).toInt()
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> scale = 0.5
      ZoomType.ACTUAL -> scale = 1.0
      ZoomType.IN -> scale += 0.1
      ZoomType.OUT -> scale -= 0.1
    }
    contentPanel.scale = scale
    scrollPane.viewport.revalidate()

    position.x = (position.x * scale).toInt()
    position.y = (position.y * scale).toInt()
    position.translate(-scrollPane.viewport.width / 2, -scrollPane.viewport.height / 2)
    scrollPane.viewport.viewPosition = position
    return true
  }

  override fun canZoomIn() = true

  override fun canZoomOut() = true

  override fun canZoomToFit() = true

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
    leftGroup.add(myProcessSelectionAction)
    leftGroup.add(showBordersCheckBox)
    leftPanel.add(ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true).component,
                  BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)

    val rightGroup = DefaultActionGroup()
    rightGroup.add(object : AnAction("reset") {
      override fun actionPerformed(e: AnActionEvent) {
        viewMode = viewMode.next
        contentPanel.viewMode = viewMode
      }

      override fun update(e: AnActionEvent) {
        e.presentation.icon = viewMode.icon
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

  @Suppress("UNUSED_PARAMETER")
  private fun modelChanged(old: InspectorModel, new: InspectorModel) {
    scrollPane.viewport.revalidate()
    repaint()
  }

  // TODO: Replace this with the process selector from the profiler
  private class SelectProcessAction(val client: InspectorClient) :
    DropDownAction("Select Process", "Select a process to connect to.", AllIcons.General.Add) {

    override fun updateActions(): Boolean {
      removeAll()

      // Rebuild the action tree.
      val processesMap = client.loadProcesses()
      if (processesMap.isEmpty()) {
        val noDeviceAction = object : AnAction("No devices detected") {
          override fun actionPerformed(e: AnActionEvent) {}
        }
        noDeviceAction.templatePresentation.isEnabled = false
        add(noDeviceAction)
      }
      else {
        for (stream in processesMap.keys) {
          val deviceAction = DropDownAction(buildDeviceName(stream.device), null, null)
          val processes = processesMap[stream]
          if (processes == null || processes.isEmpty()) {
            val noProcessAction = object : AnAction("No debuggable processes detected") {
              override fun actionPerformed(e: AnActionEvent) {}
            }
            noProcessAction.templatePresentation.isEnabled = false
            deviceAction.add(noProcessAction)
          }
          else {
            for (process in processes) {
              val processAction = object : AnAction("${process.name} (${process.pid})") {
                override fun actionPerformed(event: AnActionEvent) {
                  client.attach(stream, process)
                }
              }
              deviceAction.add(processAction)
            }
          }
          add(deviceAction)
        }
      }
      return true
    }

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
}
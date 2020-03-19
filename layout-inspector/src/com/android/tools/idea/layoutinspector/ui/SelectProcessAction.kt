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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import icons.StudioIcons
import org.jetbrains.annotations.TestOnly

val NO_PROCESS_ACTION = object : AnAction("No debuggable processes detected") {
  override fun actionPerformed(event: AnActionEvent) {}
}.apply { templatePresentation.isEnabled = false }

private val ICON = ColoredIconGenerator.generateColoredIcon(StudioIcons.Avd.DEVICE_PHONE, JBColor(0x6E6E6E, 0xAFB1B3))

class SelectProcessAction(val layoutInspector: LayoutInspector) :
  DropDownAction("Select Process", "Select a process to connect to.", ICON) {

  private var currentProcess = Common.Process.getDefaultInstance()
  private var project: Project? = null

  override fun update(event: AnActionEvent) {
    project = event.project

    if (currentProcess != layoutInspector.currentClient.selectedProcess) {
      val processName = layoutInspector.currentClient.selectedProcess.name.substringAfterLast('.')
      val actionName =
        if (layoutInspector.currentClient.selectedProcess == Common.Process.getDefaultInstance()) "Select Process" else processName
      currentProcess = layoutInspector.currentClient.selectedProcess
      event.presentation.text = actionName
    }
  }

  @TestOnly
  fun updateDropDownActions(context: DataContext) {
    updateActions(context)
  }

  override fun updateActions(context: DataContext): Boolean {
    removeAll()

    val serials = mutableSetOf<String>()

    // Rebuild the action tree.
    for (client in layoutInspector.allClients) {
      val processesMap = client.loadProcesses()
      for (stream in processesMap.keys) {
        val serial = stream.device.serial
        if (!serials.add(serial)) {
          continue
        }
        val deviceName = buildDeviceName(serial, stream.device.model, stream.device.manufacturer)
        add(DeviceAction(deviceName, processesMap, stream, client))
      }
    }
    if (childrenCount == 0) {
      val noDeviceAction = object : AnAction("No devices detected") {
        override fun actionPerformed(event: AnActionEvent) {}
      }
      noDeviceAction.templatePresentation.isEnabled = false
      add(noDeviceAction)
    }
    else {
      add(StopAction(layoutInspector::currentClient))
    }
    return true
  }

  override fun displayTextInToolbar() = true

  @VisibleForTesting
  class ConnectAction(val process: Common.Process, val stream: Common.Stream, val client: InspectorClient) :
    ToggleAction("${process.name} (${process.pid})") {
    override fun isSelected(event: AnActionEvent): Boolean {
      return process == client.selectedProcess && stream == client.selectedStream
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (state) {
        client.attach(stream, process)
      }
    }
  }

  private class StopAction(val client: () -> InspectorClient) : AnAction("Stop inspector") {
    override fun update(event: AnActionEvent) {
      event.presentation.isEnabled = client().isConnected
    }

    override fun actionPerformed(event: AnActionEvent) {
      client().disconnect()
    }
  }

  @VisibleForTesting
  class DeviceAction(
    deviceName: String, processesMap: Map<Common.Stream, List<Common.Process>>, stream: Common.Stream, client: InspectorClient
  ) : DropDownAction(deviceName, null, null) {
    override fun displayTextInToolbar() = true
    init {
      val processes = processesMap[stream]
      if (processes == null || processes.isEmpty()) {
        add(NO_PROCESS_ACTION)
      }
      else {
        val sortedProcessList = processes.sortedWith(compareBy({ it.name }, { it.pid }))
        for (process in sortedProcessList) {
          add(ConnectAction(process, stream, client))
        }
      }
    }
  }
}

fun buildDeviceName(serial: String?, model: String, manufacturer: String?): String {
  var displayModel = model
  val deviceNameBuilder = StringBuilder()
  val suffix = String.format("-%s", serial)
  if (displayModel.endsWith(suffix)) {
    displayModel = displayModel.substring(0, displayModel.length - suffix.length)
  }
  if (!StringUtil.isEmpty(manufacturer)) {
    deviceNameBuilder.append(manufacturer)
    deviceNameBuilder.append(" ")
  }
  deviceNameBuilder.append(displayModel)

  return deviceNameBuilder.toString()
}

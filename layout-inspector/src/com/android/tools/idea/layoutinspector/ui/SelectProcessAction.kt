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
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.facet.ProjectFacetManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import icons.StudioIcons
import io.grpc.StatusRuntimeException
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.Future

val NO_PROCESS_ACTION = object : AnAction("No debuggable processes detected") {
  override fun actionPerformed(event: AnActionEvent) {}
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = false
  }
}

private val ICON_COLOR = JBColor(0x6E6E6E, 0xAFB1B3)
private val ICON_PHONE = ColoredIconGenerator.generateColoredIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE, ICON_COLOR)
private val ICON_EMULATOR = ColoredIconGenerator.generateColoredIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, ICON_COLOR)

class SelectProcessAction(val layoutInspector: LayoutInspector) :
  DropDownAction("Select Process", "Select a process to connect to.", ICON_PHONE) {

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
      event.presentation.icon = layoutInspector.currentClient.selectedStream.device.toIcon()
    }
  }

  @VisibleForTesting
  public override fun updateActions(context: DataContext): Boolean {
    removeAll()

    val serials = mutableSetOf<String>()

    // Rebuild the action tree.
    for (client in layoutInspector.allClients) {
      for (stream in client.getStreams()) {
        val serial = stream.device.serial
        if (!serials.add(serial)) {
          continue
        }
        val deviceName = stream.device.toDisplayName()
        if (stream.device.featureLevel < 23) {
          add(object : AnAction("$deviceName (Unsupported for API < 23)") {
            override fun update(event: AnActionEvent) {
              event.presentation.isEnabled = false
            }
            override fun actionPerformed(e: AnActionEvent) {}
          })
        }
        else {
          add(DeviceAction(deviceName, stream, client, layoutInspector.layoutInspectorModel.project))
        }
      }
    }
    if (childrenCount == 0) {
      val noDeviceAction = object : AnAction("No devices detected") {
        override fun update(event: AnActionEvent) {
          event.presentation.isEnabled = false
        }
        override fun actionPerformed(event: AnActionEvent) {}
      }
      add(noDeviceAction)
    }
    else {
      add(StopAction(layoutInspector::currentClient))
    }
    return true
  }

  override fun displayTextInToolbar() = true

  @VisibleForTesting
  class ConnectAction(
    val process: Common.Process,
    val stream: Common.Stream,
    val client: InspectorClient,
    val project: Project
  ) : ToggleAction("${process.name} (${process.pid})") {
    override fun isSelected(event: AnActionEvent): Boolean {
      return process == client.selectedProcess && stream == client.selectedStream
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (state) {
        connect()
      }
    }

    @VisibleForTesting
    fun connect(): Future<*> = ApplicationManager.getApplication().executeOnPooledThread {
      try {
        client.attach(stream, process)
      }
      catch (statusException: StatusRuntimeException) {
        AndroidNotification.getInstance(project).showBalloon("Connection Failed",
          "Connection failed: " +
          if (statusException.status.description == null) statusException.status.code.toString()
          else statusException.status.code.toString() + ": " + statusException.status.description,
          NotificationType.WARNING)
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
    deviceName: String,
    stream: Common.Stream,
    client: InspectorClient,
    project: Project
  ) : DropDownAction(deviceName, null, stream.device.toIcon()) {
    override fun displayTextInToolbar() = true

    init {
      val allProcesses = client.getProcesses(stream)

      // TODO: determine if it's still necessary to look up package ids by the other methods used in AndroidProcessChooserDialog
      val facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID)
      val applicationIds = facets.mapNotNull { AndroidModel.get(it) }.flatMap { it.allApplicationIds }
      val processes = allProcesses.sortedWith(compareBy({ it.name }, { it.pid }))

      val (projectProcesses, otherProcesses) = processes.partition { it.name in applicationIds }
      for (process in projectProcesses) {
        add(ConnectAction(process, stream, client, project))
      }
      if (projectProcesses.isNotEmpty() && otherProcesses.isNotEmpty()) {
        add(Separator.getInstance())
      }
      for (process in otherProcesses) {
        add(ConnectAction(process, stream, client, project))
      }

      if (childrenCount == 0) {
        add(NO_PROCESS_ACTION)
      }
    }
  }
}

private fun Common.Device.toDisplayName(): String {
  var displayModel = model
  val deviceNameBuilder = StringBuilder()
  val suffix = String.format("-%s", serial)
  if (displayModel.endsWith(suffix)) {
    displayModel = displayModel.substring(0, displayModel.length - suffix.length)
  }
  if (!isEmulator && manufacturer.isNotBlank()) {
    deviceNameBuilder.append(manufacturer)
    deviceNameBuilder.append(" ")
  }
  deviceNameBuilder.append(displayModel.replace('_', ' '))

  return deviceNameBuilder.toString()
}

private fun Common.Device.toIcon() = if (isEmulator) ICON_EMULATOR else ICON_PHONE

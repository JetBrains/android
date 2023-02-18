/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.ui.JBColor
import icons.StudioIcons
import javax.swing.JComponent

val NO_PROCESS_ACTION =
  object : AnAction(AppInspectionBundle.message("action.no.debuggable.process")) {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = false
    }
    override fun actionPerformed(event: AnActionEvent) {}
  }

val NO_DEVICE_ACTION =
  object : AnAction(AppInspectionBundle.message("action.no.devices")) {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = false
    }
    override fun actionPerformed(event: AnActionEvent) {}
  }

private val ICON_COLOR = JBColor(0x6E6E6E, 0xAFB1B3)
val ICON_PHONE =
  ColoredIconGenerator.generateColoredIcon(
    StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE,
    ICON_COLOR
  )
val ICON_EMULATOR =
  ColoredIconGenerator.generateColoredIcon(
    StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE,
    ICON_COLOR
  )

/**
 * An action that presents a list of devices and processes, allowing the user to select a process,
 * along with a stop button (for stopping any active process)
 *
 * Once selected, the process automatically informs the passed in [ProcessesModel], which in turn
 * notifies other components that a new process is now active.
 *
 * @param supportsOffline If true, this means when a process is stopped, it remains in the list,
 * ```
 *     since the idea is that any downstream components will still work (perhaps in a read-only
 *     mode). Otherwise, stopped processes will be removed.
 *
 * @param createProcessLabel
 * ```
 * A callback that allows customizing the display label of the top-level
 * ```
 *     process text, particularly useful for supporting compact modes as necessary. The default
 *     option doesn't worry about consuming horizontal space.
 *
 * @param stopPresentation
 * ```
 * Optional overrides for the "stop" action that shows up in the list, in case
 * ```
 *     a particular component needs to show a uniquely customized message.
 *
 * @param onStopAction
 * ```
 * A callback triggered when the user presses the stop button.
 *
 * @param customDeviceAttribution A callback that allows customization of the device.
 *
 * @param customProcessAttribution A callback that allows customization of a process.
 */
class SelectProcessAction(
  private val model: ProcessesModel,
  private val supportsOffline: Boolean = true,
  private val createProcessLabel: (ProcessDescriptor) -> String =
    Companion::createDefaultProcessLabel,
  private val stopPresentation: StopPresentation = StopPresentation(),
  private val onStopAction: ((ProcessDescriptor) -> Unit)? = null,
  private val customDeviceAttribution: (DeviceDescriptor, AnActionEvent) -> Unit = { _, _ -> },
  private val customProcessAttribution: (ProcessDescriptor, AnActionEvent) -> Unit = { _, _ -> }
) :
  DropDownAction(
    AppInspectionBundle.message("action.select.process"),
    AppInspectionBundle.message("action.select.process.desc"),
    ICON_PHONE
  ) {

  companion object {
    fun createDefaultProcessLabel(process: ProcessDescriptor): String {
      return "${process.device.buildDeviceName()} > ${process.buildProcessName()}"
    }

    fun createCompactProcessLabel(process: ProcessDescriptor): String {
      return process.name.substringAfterLast('.')
    }
  }

  private var lastProcess: ProcessDescriptor? = null
  private var lastProcessCount = 0
  var button: JComponent? = null
    private set

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).also { button = it }
  }

  override fun update(event: AnActionEvent) {
    if (model.selectedProcess == lastProcess && model.processes.size == lastProcessCount) return

    val currentProcess = model.selectedProcess
    val content =
      currentProcess?.let {
        if (it.isRunning || supportsOffline) {
          createProcessLabel(it)
        } else {
          AppInspectionBundle.message("action.select.process")
        }
      }
        ?: if (model.processes.isEmpty()) {
          AppInspectionBundle.message("no.process.available")
        } else {
          AppInspectionBundle.message("no.process.selected")
        }

    event.presentation.icon = currentProcess?.device.toIcon()
    event.presentation.text = content

    lastProcess = currentProcess
    lastProcessCount = model.processes.size
  }

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()

    // Rebuild the action tree.
    model.devices.forEach { device -> add(DeviceAction(device)) }

    if (childrenCount == 0) {
      add(NO_DEVICE_ACTION)
    }

    // For consistency, always add a stop action, but only enable it if there's a current process
    // that can actually be stopped.
    val stopInspectionAction =
      object :
        AnAction(stopPresentation.text, stopPresentation.desc, StudioIcons.Shell.Toolbar.STOP) {
        override fun update(e: AnActionEvent) {
          e.presentation.isEnabled = (model.selectedProcess?.isRunning == true)
        }
        override fun actionPerformed(e: AnActionEvent) {
          onStopAction?.invoke(model.selectedProcess!!)
        }
      }
    add(stopInspectionAction)

    return true
  }

  override fun displayTextInToolbar() = true

  class StopPresentation(
    val text: String = AppInspectionBundle.message("action.stop.inspectors"),
    val desc: String = AppInspectionBundle.message("action.stop.inspectors.description")
  )

  private inner class ConnectAction(private val processDescriptor: ProcessDescriptor) :
    ToggleAction(processDescriptor.buildProcessName()) {
    override fun isSelected(event: AnActionEvent): Boolean {
      return processDescriptor == model.selectedProcess
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      model.selectedProcess = processDescriptor
    }

    override fun update(event: AnActionEvent) {
      super.update(event)
      customProcessAttribution(processDescriptor, event)
    }
  }

  private inner class DeviceAction(
    private val device: DeviceDescriptor,
  ) : DropDownAction(device.buildDeviceName(), null, device.toIcon()) {
    override fun displayTextInToolbar() = true

    init {
      val (preferredProcesses, otherProcesses) =
        model
          .processes
          .sortedBy { it.name }
          .filter { (it.isRunning || supportsOffline) && (it.device.serial == device.serial) }
          .partition { model.isProcessPreferred(it, includeDead = supportsOffline) }

      for (preferredProcess in preferredProcesses) {
        add(ConnectAction(preferredProcess))
      }
      if (preferredProcesses.isNotEmpty() && otherProcesses.isNotEmpty()) {
        add(Separator.getInstance())
      }
      for (otherProcess in otherProcesses) {
        add(ConnectAction(otherProcess))
      }
      if (childrenCount == 0) {
        add(NO_PROCESS_ACTION)
      }
    }

    override fun update(event: AnActionEvent) {
      super.update(event)
      customDeviceAttribution(device, event)
    }
  }
}

fun DeviceDescriptor.buildDeviceName(): String {
  var displayModel = model
  val deviceNameBuilder = StringBuilder()

  // Removes possible serial suffix
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

private fun ProcessDescriptor.buildProcessName() = "$name${if (isRunning) "" else " [DETACHED]"}"

private fun DeviceDescriptor?.toIcon() = if (this?.isEmulator == true) ICON_EMULATOR else ICON_PHONE

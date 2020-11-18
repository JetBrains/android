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
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.ui.JBColor
import icons.StudioIcons

val NO_PROCESS_ACTION = object : AnAction(AppInspectionBundle.message("action.no.debuggable.process")) {
  override fun actionPerformed(event: AnActionEvent) {}
}.apply { templatePresentation.isEnabled = false }

private val ICON_COLOR = JBColor(0x6E6E6E, 0xAFB1B3)
private val ICON_PHONE = ColoredIconGenerator.generateColoredIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE, ICON_COLOR)
private val ICON_EMULATOR = ColoredIconGenerator.generateColoredIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, ICON_COLOR)

class SelectProcessAction(private val model: AppInspectionProcessModel, private val onStopAction: ((ProcessDescriptor) -> Unit)? = null) :
  DropDownAction(AppInspectionBundle.message("action.select.process"), AppInspectionBundle.message("action.select.process.desc"),
                 ICON_PHONE) {

  private var lastProcess: ProcessDescriptor? = null
  private var lastProcessCount = 0
  override fun update(event: AnActionEvent) {
    if (model.selectedProcess == lastProcess && model.processes.size == lastProcessCount) return

    val currentProcess = model.selectedProcess
    val content = currentProcess?.let {
      "${it.buildDeviceName()} > ${it.buildProcessName()}"
    } ?: if (model.processes.isEmpty()) {
      AppInspectionBundle.message("no.process.available")
    } else {
      AppInspectionBundle.message("no.process.selected")
    }

    event.presentation.icon = currentProcess.toIcon()
    event.presentation.text = content

    lastProcess = currentProcess
    lastProcessCount = model.processes.size
  }

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()

    val serials = mutableSetOf<String>()

    // Rebuild the action tree.
    for (processDescriptor in model.processes) {
      val serial = processDescriptor.serial
      if (!serials.add(serial)) {
        continue
      }
      add(DeviceAction(processDescriptor, model))
    }
    if (childrenCount == 0) {
      val noDeviceAction = object : AnAction(AppInspectionBundle.message("action.no.devices")) {
        override fun actionPerformed(event: AnActionEvent) {}
      }
      noDeviceAction.templatePresentation.isEnabled = false
      add(noDeviceAction)
    }
    else if (model.selectedProcess?.isRunning == true) {
      // If selected process exists and is running (not detached), then add a Stop action.
      val stopInspectionAction = object : AnAction(AppInspectionBundle.message("action.stop.inspectors"),
                                                   AppInspectionBundle.message("action.stop.inspectors.description"),
                                                   StudioIcons.Shell.Toolbar.STOP) {
        override fun actionPerformed(e: AnActionEvent) {
          onStopAction?.invoke(model.selectedProcess!!)
        }
      }
      add(stopInspectionAction)
    }
    return true
  }

  override fun displayTextInToolbar() = true

  class ConnectAction(private val processDescriptor: ProcessDescriptor, private val model: AppInspectionProcessModel) :
    ToggleAction(processDescriptor.buildProcessName()) {
    override fun isSelected(event: AnActionEvent): Boolean {
      return processDescriptor == model.selectedProcess
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      model.setSelectedProcess(processDescriptor, isUserAction = true)
    }
  }

  private class DeviceAction(processDescriptor: ProcessDescriptor, private val model: AppInspectionProcessModel)
    : DropDownAction(processDescriptor.buildDeviceName(), null, processDescriptor.toIcon()) {
    override fun displayTextInToolbar() = true

    init {
      val (preferredProcesses, otherProcesses) = model.processes
        .filter { it.serial == processDescriptor.serial }
        .partition { model.isProcessPreferred(it, includeDead = true) }

      for (process in preferredProcesses) {
        add(ConnectAction(process, model))
      }
      if (preferredProcesses.isNotEmpty() && otherProcesses.isNotEmpty()) {
        add(Separator.getInstance())
      }
      for (process in otherProcesses) {
        add(ConnectAction(process, model))
      }
      if (childrenCount == 0) {
        add(NO_PROCESS_ACTION)
      }
    }
  }
}

private fun ProcessDescriptor.buildDeviceName(): String {
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

private fun ProcessDescriptor.buildProcessName() = "$processName${if (isRunning) "" else " [DEAD]"}"

private fun ProcessDescriptor?.toIcon() = if (this?.isEmulator == true) ICON_EMULATOR else ICON_PHONE

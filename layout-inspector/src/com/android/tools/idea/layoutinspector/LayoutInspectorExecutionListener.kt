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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.IDevice
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.run.AndroidRunConfiguration
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

/** Layout Inspector specific logic that runs when the user presses "Run" or "Debug" */
class LayoutInspectorExecutionListener : ExecutionListener {
  override fun processStarted(
    executorId: String,
    env: ExecutionEnvironment,
    handler: ProcessHandler
  ) {
    val project = env.project
    val configuration = env.runProfile as? AndroidRunConfiguration ?: return

    if (!configuration.INSPECTION_WITHOUT_ACTIVITY_RESTART) {
      return
    }

    val info = AndroidSessionInfo.from(handler) ?: return

    info.devices.forEach { device ->
      if (device.version.apiLevel >= 29) {
        enableDebugViewAttributes(project, handler, info.applicationId, device)
      }
    }
  }

  private fun enableDebugViewAttributes(
    project: Project,
    handler: ProcessHandler,
    packageName: String,
    device: IDevice
  ) {
    val descriptor =
      object : DeviceDescriptor {
        override val manufacturer: String = "" // unused
        override val model: String = device.model
        override val serial: String = device.serialNumber
        override val isEmulator: Boolean = device.isEmulator
        override val apiLevel: Int = device.version.apiLevel
        override val version: String = "" // unused
        override val codename: String = "" // unused
      }
    val process =
      object : ProcessDescriptor {
        override val device: DeviceDescriptor = descriptor
        override val abiCpuArch: String = ""
        override val name: String = packageName
        override val packageName: String = packageName
        override val isRunning: Boolean = true
        override val pid: Int = 0
        override val streamId: Long = 0L
      }
    val debugViewAttributes = DebugViewAttributes.getInstance()

    if (debugViewAttributes.set(project, process)) {
      handler.addProcessListener(
        object : ProcessAdapter() {
          override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
            if (!debugViewAttributes.usePerDeviceSettings()) {
              debugViewAttributes.clear(project, process)
            }
          }
        }
      )
    }
  }

  private val IDevice.model
    get() =
      when {
        isEmulator -> avdName.takeIf { !it.isNullOrBlank() }
        else -> getProperty(IDevice.PROP_DEVICE_MODEL)?.let { StringUtil.capitalizeWords(it, true) }
      } ?: "Unknown"
}

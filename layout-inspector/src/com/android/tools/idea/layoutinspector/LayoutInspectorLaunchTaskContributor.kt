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
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.run.AndroidLaunchTaskContributor
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
interface LayoutInspectorLaunchTask : LaunchTask

/**
 * Layout Inspector specific logic that runs when the user presses "Run" or "Debug"
 */
class LayoutInspectorLaunchTaskContributor : AndroidLaunchTaskContributor {
  override fun getTask(applicationId: String,
                       configuration: AndroidRunConfigurationBase,
                       device: IDevice,
                       executor: Executor) = object : LayoutInspectorLaunchTask {
    override fun getId() = LAYOUT_INSPECTOR_TOOL_WINDOW_ID
    override fun getDescription() = "Launching Layout Inspector"
    override fun getDuration() = LaunchTaskDurations.LAUNCH_ACTIVITY
    override fun run(launchContext: LaunchContext) {
      if (configuration.INSPECTION_WITHOUT_ACTIVITY_RESTART && launchContext.device.version.apiLevel >= 29) {
        enableDebugViewAttributes(configuration.project, applicationId, launchContext.env.executionId, device)
      }
    }

    private fun enableDebugViewAttributes(project: Project, packageName: String, executionId: Long, device: IDevice) {
      val disposable = Disposer.newDisposable()
      Disposer.register(project, disposable)
      val descriptor = object : DeviceDescriptor {
        override val manufacturer: String = "" // unused
        override val model: String = device.model
        override val serial: String = device.serialNumber
        override val isEmulator: Boolean = device.isEmulator
        override val apiLevel: Int = device.version.apiLevel
        override val version: String = "" // unused
        override val codename: String = "" // unused
      }
      val process = object : ProcessDescriptor {
        override val device: DeviceDescriptor = descriptor
        override val abiCpuArch: String = ""
        override val name: String = packageName
        override val packageName: String = packageName
        override val isRunning: Boolean = true
        override val pid: Int = 0
        override val streamId: Long = 0L
      }
      val debugViewAttributes = DebugViewAttributes.getInstance()

      project.messageBus.connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
        override fun processNotStarted(executorId: String, env: ExecutionEnvironment) {
          if (env.executionId != executionId) {
            return
          }
          Disposer.dispose(disposable)
        }

        override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          if (env.executionId != executionId) {
            return
          }
          if (debugViewAttributes.set(project, process)) {
            handler.addProcessListener(object : ProcessAdapter() {
              override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                if (!debugViewAttributes.usePerDeviceSettings()) {
                  debugViewAttributes.clear(project, process)
                }
              }
            })
          }
        }
      })
    }
  }

  private val IDevice.model get() = when {
    isEmulator -> avdName.takeIf { !it.isNullOrBlank() }
    else -> getProperty(IDevice.PROP_DEVICE_MODEL)?.let { StringUtil.capitalizeWords(it, true) }
  } ?: "Unknown"
}

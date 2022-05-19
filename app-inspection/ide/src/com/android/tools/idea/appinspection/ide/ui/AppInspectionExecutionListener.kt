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

import com.android.ddmlib.IDevice
//import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

/** App Inspection specific logic that runs when the user presses "Run" or "Debug" */
class AppInspectionExecutionListener : ExecutionListener {

  override fun processStarted(
    executorId: String,
    env: ExecutionEnvironment,
    handler: ProcessHandler
  ) {
    val project = env.project
    return
    /*
    val info = AndroidSessionInfo.from(handler) ?: return

    info.devices.forEach { device ->
      storeRecentProcess(project, device, info.applicationId, handler)
      displayStripeButton(project, device)
    }
     */
  }

  private fun displayStripeButton(project: Project, device: IDevice) {
    ApplicationManager.getApplication().invokeLater {
      val window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(APP_INSPECTION_ID)
      if (window != null && device.version.isGreaterOrEqualThan(26)) {
        // For discoverability, we always show the "app inspection" tool window button to users
        // if they are targeting a device that supports JVMTI, since that means it can support
        // inspectors. We'll always at least have the database inspector available, and more and
        // more inspectors should land and be applicable over time. Inspection won't connect
        // until the user actually clicks the button.
        window.isShowStripeButton = true
      }
    }
  }

  private fun storeRecentProcess(
    project: Project,
    device: IDevice,
    applicationId: String,
    handler: ProcessHandler
  ) {
    val recentProcess = RecentProcess(device.serialNumber, applicationId)
    RecentProcess.set(project, recentProcess)

    handler.addProcessListener(
      object : ProcessAdapter() {
        override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
          if (recentProcess === RecentProcess.get(project)) {
            RecentProcess.set(project, null)
          }
        }
      }
    )
  }
}

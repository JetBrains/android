/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.profilers.AndroidProfilerToolWindow.Companion.getDeviceDisplayName
import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory.Companion.getProfilerToolWindow
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.wm.ToolWindowManager

class ProfilerExecutionListener : ExecutionListener {

  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    val info = AndroidSessionInfo.from(handler) ?: return
    val project = env.project

    if (info.devices.size != 1) {
      return
    }

    // There are two scenarios here:
    // 1. If the profiler window is opened
    // 2. If the profiler window is closed, we cache the device+module info so the profilers can auto-start if the user opens the window
    // manually at a later time.
    runInEdt {
      val window = ToolWindowManager.getInstance(project).getToolWindow(AndroidProfilerToolWindowFactory.ID) ?: return@runInEdt
      window.isShowStripeButton = true
      val deviceName = getDeviceDisplayName(info.devices.first())
      val preferredProcessInfo = PreferredProcessInfo(deviceName, info.applicationId) { true }
      // If the window is currently not shown, either if the users click on Run/Debug or if they manually collapse/hide the window,
      // then we shouldn't start profiling the launched app.
      var profileStarted = false
      if (window.isVisible) {
        val profilerToolWindow = getProfilerToolWindow(project)
        if (profilerToolWindow != null) {
          profilerToolWindow.profile(preferredProcessInfo)
          profileStarted = true
        }
      }
      // Caching the device+process info in case auto-profiling should kick in at a later time.
      if (!profileStarted) {
        project.putUserData(AndroidProfilerToolWindow.LAST_RUN_APP_INFO, preferredProcessInfo)
      }
    }

    // When Studio detects that the process is terminated, remove the LAST_RUN_APP_INFO cache to prevent the profilers from waiting
    // to auto-profiling a process that has already been killed.
    handler.addProcessListener(object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        project.putUserData(AndroidProfilerToolWindow.LAST_RUN_APP_INFO, null)
      }
    })
  }
}
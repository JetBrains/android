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
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
interface AppInspectionLaunchTask : LaunchTask

/**
 * App Inspection specific logic that runs when the user presses "Run" or "Debug"
 */
class AppInspectionLaunchTaskContributor : AndroidLaunchTaskContributor {
  override fun getTask(applicationId: String,
                       configuration: AndroidRunConfigurationBase,
                       device: IDevice,
                       executor: Executor) = object : AppInspectionLaunchTask {
    private val project = configuration.project

    override fun getId() = APP_INSPECTION_ID
    override fun getDescription() = AppInspectionBundle.message("launch.app.inspection.tool.window")
    override fun getDuration() = LaunchTaskDurations.LAUNCH_ACTIVITY
    override fun run(launchContext: LaunchContext) {
      storeRecentProcess(launchContext.device, launchContext.env.executionId)
      displayStripeButton(launchContext.device)
    }

    private fun displayStripeButton(device: IDevice) {
      ApplicationManager.getApplication().invokeLater {
        val window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(id)
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

    private fun storeRecentProcess(device: IDevice, executionId: Long) {
      val disposable = Disposer.newDisposable()
      Disposer.register(project, disposable)

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

          val recentProcess = RecentProcess(device, applicationId)
          RecentProcess.set(project, recentProcess)
          handler.addProcessListener(object : ProcessAdapter() {
            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
              if (recentProcess === RecentProcess.get(project)) {
                RecentProcess.set(project, null)
              }
            }
          })
        }
      })
    }
  }
}

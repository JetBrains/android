/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.LaunchInfo
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.configuration.execution.DebugSessionStarter
import com.android.tools.idea.run.debug.showError
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class ConnectJavaDebuggerTask(val applicationIdProvider: ApplicationIdProvider,
                              val project: Project) : ConnectDebuggerTaskBase(applicationIdProvider, project) {
  private var pollTimeoutSeconds: Int = 15

  override fun getDescription() = "Connecting Debugger"

  override fun getDuration() = LaunchTaskDurations.CONNECT_DEBUGGER

  override fun setTimeoutSeconds(timeoutSeconds: Int) {
    pollTimeoutSeconds = timeoutSeconds
  }

  override fun getTimeoutSeconds(): Int {
    return pollTimeoutSeconds
  }

  override fun perform(launchInfo: LaunchInfo, device: IDevice, state: ProcessHandlerLaunchStatus, printer: ProcessHandlerConsolePrinter) {

    val logger = Logger.getInstance(ConnectJavaDebuggerTask::class.java)
    val processHandler = state.processHandler
    // Reuse the current ConsoleView to retain the UI state and not to lose test results.
    val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY) as? ConsoleView
    logger.info("Attaching Java debugger")

    val env = launchInfo.env

    var timeoutSeconds = pollTimeoutSeconds
    if (timeoutSeconds <= 0) {
      timeoutSeconds = Int.MAX_VALUE
    }

    DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      applicationIdProvider.packageName,
      env,
      AndroidJavaDebugger(),
      AndroidJavaDebugger().createState(),
      destroyRunningProcess = { it.forceStop(applicationIdProvider.packageName) },
      androidTestResultListener,
      timeoutSeconds.toLong())
      .onSuccess { session ->
        processHandler.detachProcess()
        session.showSessionTab()
      }
      .onError {
        if (it is ExecutionException) {
          showError(project, it, env.runProfile.name)
        }
        else {
          Logger.getInstance(this::class.java).error(it)
        }
      }
  }

  override fun launchDebugger(currentLaunchInfo: LaunchInfo,
                              client: Client,
                              state: ProcessHandlerLaunchStatus,
                              printer: ProcessHandlerConsolePrinter) {
    throw RuntimeException("perform method should be called")
  }
}
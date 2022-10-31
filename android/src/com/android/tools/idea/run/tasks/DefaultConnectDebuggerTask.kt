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

import com.android.ddmlib.IDevice
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.LaunchInfo
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.configuration.execution.DebugSessionStarter
import com.android.tools.idea.run.debug.showError
import com.android.tools.idea.run.editor.AndroidDebugger
import com.android.tools.idea.run.editor.AndroidDebuggerState
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.LinkedList

class DefaultConnectDebuggerTask<S : AndroidDebuggerState>(
  private val debugger: AndroidDebugger<S>,
  private val debuggerState: S,
  applicationIdProvider: ApplicationIdProvider,
  @JvmField val project: Project
) : ConnectDebuggerTask {
  private val LOG = Logger.getInstance(DefaultConnectDebuggerTask::class.java)

  private var myPollTimeoutSeconds = 15

  // The first entry in the list contains the main package name, and an optional second entry contains test package name.
  @JvmField
  val myApplicationIds: MutableList<String>

  init {
    myApplicationIds = LinkedList()
    try {
      val packageName = applicationIdProvider.packageName
      myApplicationIds.add(packageName)
    }
    catch (e: ApkProvisionException) {
      LOG.error(e)
    }
    try {
      val testPackageName = applicationIdProvider.testPackageName
      if (testPackageName != null) {
        myApplicationIds.add(testPackageName)
      }
    }
    catch (e: ApkProvisionException) {
      // not as severe as failing to obtain package id for main application
      LOG.warn("Unable to obtain test package name, will not connect debugger if tests don't instantiate main application")
    }
  }

  override fun setTimeoutSeconds(timeoutSeconds: Int) {
    myPollTimeoutSeconds = timeoutSeconds
  }

  override fun getTimeoutSeconds(): Int {
    return myPollTimeoutSeconds
  }

  override fun perform(
    launchInfo: LaunchInfo,
    device: IDevice,
    state: ProcessHandlerLaunchStatus,
    printer: ProcessHandlerConsolePrinter
  ) {
    val processHandler = state.processHandler
    // Reuse the current ConsoleView to retain the UI state and not to lose test results.
    val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY) as? ConsoleView
    LOG.info("Attaching ${debugger.id} debugger")

    val env = launchInfo.env

    var timeoutSeconds = myPollTimeoutSeconds
    if (timeoutSeconds <= 0) {
      timeoutSeconds = Int.MAX_VALUE
    }

    DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      myApplicationIds[0],
      env,
      debugger,
      debuggerState,
      destroyRunningProcess = { d -> myApplicationIds.forEach { d.forceStop(it) } },
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
}
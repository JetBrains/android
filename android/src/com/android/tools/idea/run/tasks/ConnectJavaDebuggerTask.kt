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
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.LaunchInfo
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.debug.attachDebugger
import com.android.tools.idea.run.debug.getDebugProcessStarter
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class ConnectJavaDebuggerTask(
  applicationIdProvider: ApplicationIdProvider,
  project: Project
) : ConnectDebuggerTaskBase(applicationIdProvider, project) {
  override fun launchDebugger(
    currentLaunchInfo: LaunchInfo,
    client: Client,
    launchStatus: ProcessHandlerLaunchStatus,
    printer: ProcessHandlerConsolePrinter
  ) {
    val logger = Logger.getInstance(ConnectJavaDebuggerTask::class.java)
    val processHandler = launchStatus.processHandler
    // Reuse the current ConsoleView to retain the UI state and not to lose test results.
    val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY) as? ConsoleView
    logger.info("Attaching Java debugger")

    val env = currentLaunchInfo.env
    val debugProcessStarter = getDebugProcessStarter(
      env.project,
      client,
      androidTestResultListener,
      { processHandler.detachProcess() },
      { device -> myApplicationIds.forEach { device.forceStop(it) } },
      false
    )

    attachDebugger(env.project, client, env) { debugProcessStarter }.onSuccess { session -> session.showSessionTab() }
  }
}
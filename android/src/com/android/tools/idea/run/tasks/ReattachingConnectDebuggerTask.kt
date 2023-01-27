/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.DebugSessionStarter
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.xdebugger.impl.XDebugSessionImpl


/**
 * [ConnectDebuggerTask] that need to keep reattaching the debugger.
 *
 * <p>Wires up adb listeners to automatically reconnect the debugger for each test. This is necessary when
 * using instrumentation runners that kill the instrumentation process between each test, disconnecting
 * the debugger. We listen for the start of a new test, waiting for a debugger, and reconnect.
 */
class ReattachingConnectDebuggerTask<S : AndroidDebuggerState>(
  private val androidDebugger: AndroidDebugger<S>,
  private val androidDebuggerState: S,
  private val masterAndroidProcessName: String,
  private var timeoutSeconds: Int) : ConnectDebuggerTask {

  override fun perform(
    device: IDevice,
    applicationId: String,
    environment: ExecutionEnvironment,
    progressIndicator: ProgressIndicator,
    console: ConsoleView
  ): XDebugSessionImpl {
    val logger = Logger.getInstance(ReattachingConnectDebuggerTask::class.java)
    // Reuse the current ConsoleView to retain the UI state and not to lose test results.
    logger.info("Attaching Debugger")

    return DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
      device,
      applicationId,
      masterAndroidProcessName,
      environment,
      androidDebugger,
      androidDebuggerState,
      destroyRunningProcess = { },
      progressIndicator,
      console,
      timeoutSeconds.toLong()
    )
  }
}
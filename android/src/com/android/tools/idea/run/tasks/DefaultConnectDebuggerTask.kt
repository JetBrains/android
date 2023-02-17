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
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.AndroidDebuggerContext
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.DebugSessionStarter
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.orchestrator.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting

class DefaultConnectDebuggerTask<S : AndroidDebuggerState>(
  private val debugger: AndroidDebugger<S>,
  private val debuggerState: S,
  @VisibleForTesting val timeoutSeconds: Int
) : ConnectDebuggerTask {
  private val LOG = Logger.getInstance(DefaultConnectDebuggerTask::class.java)

  override fun perform(
    device: IDevice,
    applicationId: String,
    environment: ExecutionEnvironment,
    progressIndicator: ProgressIndicator,
    console: ConsoleView
  ): XDebugSessionImpl {
    LOG.info("Attaching ${debugger.id} debugger")

    return DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      applicationId,
      environment,
      debugger,
      debuggerState,
      destroyRunningProcess = { d -> d.forceStop(applicationId) },
      progressIndicator,
      console,
      timeoutSeconds.toLong()
    )
  }
}

@JvmOverloads
@Throws(ExecutionException::class)
fun getBaseDebuggerTask(
  androidDebuggerContext: AndroidDebuggerContext,
  facet: AndroidFacet,
  executionEnvironment: ExecutionEnvironment,
  timeoutSeconds: Int = 15
): ConnectDebuggerTask {
  val logger = Logger.getInstance("getBaseDebuggerTask")
  val debugger = androidDebuggerContext.androidDebugger ?: throw ExecutionException("Unable to determine debugger to use for this launch")
  logger.info("Using debugger: " + debugger.id)

  val androidDebuggerState = androidDebuggerContext.getAndroidDebuggerState<AndroidDebuggerState>()
                             ?: throw ExecutionException("Unable to determine androidDebuggerState to use for this launch")

  return getBaseDebuggerTask(debugger, androidDebuggerState, executionEnvironment, facet, timeoutSeconds)
}

@JvmOverloads
fun <S : AndroidDebuggerState> getBaseDebuggerTask(
  debugger: AndroidDebugger<S>,
  androidDebuggerState: S,
  executionEnvironment: ExecutionEnvironment,
  facet: AndroidFacet,
  timeoutSeconds: Int = 15
): ConnectDebuggerTask {
  val executionType = AndroidModel.get(facet)?.testExecutionOption ?: TestExecutionOption.HOST

  return if (executionEnvironment.runProfile is AndroidTestRunConfiguration &&
             (TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR == executionType || TestExecutionOption.ANDROID_TEST_ORCHESTRATOR == executionType)) {
    ReattachingConnectDebuggerTask(
      debugger,
      androidDebuggerState,
      MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME[executionType]!!,
      timeoutSeconds
    )
  }
  else {
    DefaultConnectDebuggerTask(debugger, androidDebuggerState, timeoutSeconds)
  }
}
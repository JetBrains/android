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
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.LaunchInfo
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.configuration.execution.DebugSessionStarter
import com.android.tools.idea.run.debug.showError
import com.android.tools.idea.run.editor.AndroidDebugger
import com.android.tools.idea.run.editor.AndroidDebuggerContext
import com.android.tools.idea.run.editor.AndroidDebuggerState
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.orchestrator.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.util.LinkedList

class DefaultConnectDebuggerTask<S : AndroidDebuggerState>(
  private val debugger: AndroidDebugger<S>,
  private val debuggerState: S,
  applicationIdProvider: ApplicationIdProvider,
  @JvmField val project: Project,
  private val timeoutSeconds: Int
) : ConnectDebuggerTask {
  private val LOG = Logger.getInstance(DefaultConnectDebuggerTask::class.java)


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

  override fun setTimeoutSeconds(timeoutSeconds: Int) {}

  override fun getTimeoutSeconds(): Int {
    return timeoutSeconds
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

@JvmOverloads
fun getBaseDebuggerTask(
  androidDebuggerContext: AndroidDebuggerContext,
  facet: AndroidFacet,
  applicationIdProvider: ApplicationIdProvider,
  executionEnvironment: ExecutionEnvironment,
  timeoutSeconds: Int = 15
): ConnectDebuggerTask? {
  val logger = Logger.getInstance("getBaseDebuggerTask")
  val debugger = androidDebuggerContext.getAndroidDebugger()
  if (debugger == null) {
    logger.error("Unable to determine debugger to use for this launch")
    return null
  }
  logger.info("Using debugger: " + debugger.id)

  val androidDebuggerState = androidDebuggerContext.getAndroidDebuggerState<AndroidDebuggerState>()

  if (androidDebuggerState == null) {
    logger.error("Unable to determine androidDebuggerState to use for this launch")
    return null
  }

  return getBaseDebuggerTask(debugger, androidDebuggerState, executionEnvironment, facet, applicationIdProvider, timeoutSeconds)
}

@JvmOverloads
fun <S : AndroidDebuggerState> getBaseDebuggerTask(
  debugger: AndroidDebugger<S>,
  androidDebuggerState: S,
  executionEnvironment: ExecutionEnvironment,
  facet: AndroidFacet,
  applicationIdProvider: ApplicationIdProvider,
  timeoutSeconds: Int = 15
): ConnectDebuggerTask {
  val executionType = AndroidModel.get(facet)?.testExecutionOption ?: TestExecutionOption.HOST

  return if (executionEnvironment.runProfile is AndroidTestRunConfiguration &&
             (TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR == executionType || TestExecutionOption.ANDROID_TEST_ORCHESTRATOR == executionType)) {
    ReattachingConnectDebuggerTask(
      debugger,
      androidDebuggerState,
      applicationIdProvider,
      MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME[executionType]!!,
      timeoutSeconds
    )
  }
  else {
    DefaultConnectDebuggerTask(debugger, androidDebuggerState, applicationIdProvider, executionEnvironment.project, timeoutSeconds)
  }
}
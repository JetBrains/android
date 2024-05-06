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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.DebugSessionStarter
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.testartifacts.instrumented.orchestrator.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.xdebugger.impl.XDebugSessionImpl

abstract class AndroidTestRunConfigurationExecutorBase(val env: ExecutionEnvironment) : AndroidConfigurationExecutor {
  final override val configuration = env.runProfile as AndroidTestRunConfiguration
  private val applicationIdProvider = configuration.applicationIdProvider ?: throw RuntimeException(
    "Can't get ApplicationIdProvider for AndroidTestRunConfiguration"
  )
  protected val packageName get() = try {
    applicationIdProvider.packageName
  } catch (e: ApkProvisionException) {
    throw ExecutionException("Can't get application ID")
  }

  protected val testPackageName
    get() = try {
      applicationIdProvider.testPackageName ?: throw AndroidExecutionException(
        "EMPTY_TEST_PACKAGE_NAME",
        "Can't determine test package name"
      )
    } catch (e: ApkProvisionException) {
      throw ExecutionException("Can't get application ID")
  }
  protected val module = configuration.configurationModule.module!!
  protected val facet =
    module.androidFacet ?: throw RuntimeException("AndroidTestRunConfigurationExecutorBase shouldn't be invoked for module without facet")
  protected val LOG = Logger.getInstance(this::class.java)
  protected suspend fun startDebuggerSession(
    indicator: ProgressIndicator,
    device: IDevice,
    applicationContext: ApplicationProjectContext,
    console: AndroidTestSuiteView
  ): XDebugSessionImpl {
    val debugger = configuration.androidDebuggerContext.androidDebugger
      ?: throw ExecutionException("Unable to determine debugger to use for this launch")
    LOG.info("Using debugger: " + debugger.id)
    val debuggerState = configuration.androidDebuggerContext.getAndroidDebuggerState<AndroidDebuggerState>()
      ?: throw ExecutionException("Unable to determine androidDebuggerState to use for this launch")
    val executionType = AndroidModel.get(facet)?.testExecutionOption ?: TestExecutionOption.HOST
    indicator.text = "Connecting debugger"

    val packageNameForDebug = packageName
    val session =
      if (TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR == executionType || TestExecutionOption.ANDROID_TEST_ORCHESTRATOR == executionType) {
        val masterAndroidProcessName = MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME[executionType]!!
        DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
          device,
          applicationContext,
          masterAndroidProcessName,
          env,
          debugger,
          debuggerState,
          destroyRunningProcess = {
            it.forceStop(packageNameForDebug)
            it.forceStop(masterAndroidProcessName)
          },
          indicator,
          console,
          Long.MAX_VALUE
        )
      } else {
        DebugSessionStarter.attachDebuggerToStartedProcess(
          device,
          applicationContext,
          env,
          debugger,
          debuggerState,
          destroyRunningProcess = { d -> d.forceStop(packageNameForDebug) },
          indicator,
          console,
          Long.MAX_VALUE
        )
      }
    return session
  }
}
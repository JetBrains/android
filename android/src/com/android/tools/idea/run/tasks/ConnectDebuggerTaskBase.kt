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
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.LaunchInfo
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.debug.showError
import com.android.tools.idea.run.debug.waitForClientReadyForDebug
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.Uninterruptibles
import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import java.util.LinkedList
import java.util.concurrent.TimeUnit

abstract class ConnectDebuggerTaskBase protected constructor(
  applicationIdProvider: ApplicationIdProvider,
  @JvmField val myProject: Project
) : ConnectDebuggerTask {
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
      logger().error(e)
    }
    try {
      val testPackageName = applicationIdProvider.testPackageName
      if (testPackageName != null) {
        myApplicationIds.add(testPackageName)
      }
    }
    catch (e: ApkProvisionException) {
      // not as severe as failing to obtain package id for main application
      logger().warn("Unable to obtain test package name, will not connect debugger if tests don't instantiate main application")
    }
  }

  override fun getDescription(): String {
    return "Connecting Debugger"
  }

  override fun getDuration(): Int {
    return LaunchTaskDurations.CONNECT_DEBUGGER
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
    val client: Client
    client = try {
      getClient(device)
    }
    catch (e: ExecutionException) {
      state.processHandler.destroyProcess()
      showError(myProject, e, launchInfo.env.runProfile.name)
      return
    }
    UIUtil.invokeAndWaitIfNeeded(Runnable { launchDebugger(launchInfo, client, state, printer) })
  }

  @Throws(ExecutionException::class)
  protected open fun getClient(device: IDevice): Client {
    val client: Client
    var pollTimeoutSeconds = myPollTimeoutSeconds
    if (pollTimeoutSeconds <= 0) {
      pollTimeoutSeconds = Int.MAX_VALUE
    }
    client = waitForClientReadyForDebug(device, myApplicationIds, pollTimeoutSeconds.toLong())
    return client
  }

  @VisibleForTesting // Allow unit tests to avoid actually sleeping.
  protected fun sleep(sleepFor: Long, unit: TimeUnit) {
    Uninterruptibles.sleepUninterruptibly(sleepFor, unit)
  }

  abstract fun launchDebugger(
    currentLaunchInfo: LaunchInfo,
    client: Client,
    state: ProcessHandlerLaunchStatus,
    printer: ProcessHandlerConsolePrinter
  )

  companion object {
    private val POLL_TIMEUNIT = TimeUnit.SECONDS
    private fun logger(): Logger {
      return Logger.getInstance(ConnectDebuggerTaskBase::class.java)
    }
  }
}
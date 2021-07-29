/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.executors

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.wearpairing.await
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.KillableProcess
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class AndroidWearConfigurationExecutorBase(private val environment: ExecutionEnvironment) : RunProfileState {

  val configuration = environment.runProfile as AndroidWearConfiguration
  val project = configuration.project
  val facet = AndroidFacet.getInstance(configuration.configurationModule.module!!)!!

  override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
    val processHandler: ProcessHandler = object : ProcessHandler(), KillableProcess {
      override fun destroyProcessImpl() = notifyProcessTerminated(0)
      override fun detachProcessImpl() = notifyProcessDetached()
      override fun detachIsDefault() = true
      override fun getProcessInput() = null
      override fun canKillProcess() = true
      override fun killProcess() {}
    }

    val consolePrinterWithTime = ProcessHandlerConsolePrinterWithTime(processHandler)

    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    console.attachToProcess(processHandler)

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Launching ${configuration.name}") {
      override fun run(indicator: ProgressIndicator) {
        doExecute(indicator, consolePrinterWithTime, processHandler)
      }

      override fun onThrowable(error: Throwable) {
        if (error is ExecutionException) {
          error.message?.let { consolePrinterWithTime.stderr(it) }
        }
        processHandler.destroyProcess()
        super.onThrowable(error)
      }
    })

    return DefaultExecutionResult(console, processHandler)
  }

  @WorkerThread
  private fun doExecute(indicator: ProgressIndicator, consolePrinter: ProcessHandlerConsolePrinter, processHandler: ProcessHandler) {
    consolePrinter.stdout("Launching '${configuration.name}'")
    indicator.text = "Waiting for all target devices to come online"
    val devices = runBlocking {
      consolePrinter.stdout("Waiting for devices...")
      getDevices()
    }
    if (devices.isEmpty()) {
      throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))
    }
    devices.forEach {
      LaunchUtils.initiateDismissKeyguard(it)
      doOnDevice(DeviceWearConfigurationExecutionSession(it, environment, processHandler, consolePrinter, indicator))
    }
  }

  abstract fun doOnDevice(deviceWearConfigurationExecutionSession: DeviceWearConfigurationExecutionSession)

  private suspend fun getDevices(): List<IDevice> {
    val provider = DeviceAndSnapshotComboBoxTargetProvider()
    val deployTarget = if (provider.requiresRuntimePrompt(project)) provider.showPrompt(facet) else provider.getDeployTarget(project)
    val deviceFutureList = deployTarget?.getDevices(facet)?.get() ?: return emptyList()
    return deviceFutureList.map { it.await() }
  }
}

private class ProcessHandlerConsolePrinterWithTime(processHandler: ProcessHandler) : ProcessHandlerConsolePrinter(processHandler) {
  private val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss: ", Locale.US)
  private fun getTimeString() = dateFormat.format(Date())
  override fun stdout(text: String) = super.stdout("${getTimeString()} $text")
  override fun stderr(text: String) = super.stderr("${getTimeString()} $text")
}
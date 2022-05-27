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
package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.Complication
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.UNSET_WATCH_FACE
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration.ChosenSlot
import com.android.tools.idea.run.configuration.getComplicationSourceTypes
import com.android.tools.idea.run.configuration.parseRawComplicationTypes
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

private const val COMPLICATION_MIN_DEBUG_SURFACE_VERSION = 2
private const val COMPLICATION_RECOMMENDED_DEBUG_SURFACE_VERSION = 3

class AndroidComplicationConfigurationExecutor(environment: ExecutionEnvironment) : AndroidConfigurationExecutorBase(environment) {
  override val configuration = environment.runProfile as AndroidComplicationConfiguration

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): Promise<RunContentDescriptor> {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }
    val console = createConsole()
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    val applicationInstaller = getApplicationInstaller(console)
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val watchFaceInfo = "${configuration.watchFaceInfo.appId} ${configuration.watchFaceInfo.watchFaceFQName}"
    val complicationComponentName = AppComponent.getFQEscapedName(appId, configuration.componentName!!)
    val processHandler = AndroidProcessHandler(project, appId, getStopComplicationCallback(complicationComponentName, console, isDebug))
    devices.forEach { device ->
      terminatePreviousAppInstance(device)
      processHandler.addTargetDevice(device)
      val version = device.getWearDebugSurfaceVersion()
      if (version < COMPLICATION_MIN_DEBUG_SURFACE_VERSION) {
        throw SurfaceVersionException(COMPLICATION_MIN_DEBUG_SURFACE_VERSION, version, device.isEmulator)
      }
      if (version < COMPLICATION_RECOMMENDED_DEBUG_SURFACE_VERSION) {
        console.printError(AndroidBundle.message("android.run.configuration.debug.surface.warn"))
      }
      indicator?.checkCanceled()
      val app = applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.installFlags)
      val provider = project.getProjectSystem().getApkProvider(configuration)
      if (provider == null) {
        Logger.getInstance(this::class.java).warn("Apk could not be retrieved.")
      } else {
        configuration.verifyProviderTypes(parseRawComplicationTypes(getComplicationSourceTypes(provider.getApks(device))))
      }
      indicator?.checkCanceled()
      installWatchApp(device, console)

      if (isDebug) {
        val promise = AsyncPromise<RunContentDescriptor>()
        executeOnPooledThread {
          startDebugSession(devices.single(), console)
            .onError(promise::setError)
            .then { it.runContentDescriptor }.processed(promise)
        }
        configuration.chosenSlots.forEach { slot ->
          setComplicationOnWatchFace(app, configuration, watchFaceInfo, slot, mode, indicator)
        }
        showWatchFace(device, console)
        return promise
      }
      configuration.chosenSlots.forEach { slot -> setComplicationOnWatchFace(app, configuration, watchFaceInfo, slot, mode, indicator) }
      showWatchFace(device, console)
    }
    ProgressManager.checkCanceled()
    return createRunContentDescriptor(processHandler, console, environment)
  }

  internal fun setComplicationOnWatchFace(app : App, configuration: AndroidComplicationConfiguration, watchFaceInfo: String,
                                          slot: ChosenSlot, mode: AppComponent.Mode, indicator: ProgressIndicator?) {
    if (slot.type == null) {
      throw ExecutionException("Slot type is not specified for slot(id: ${slot.id}).")
    }
    val receiver = RecordOutputReceiver { indicator?.isCanceled == true }
    try {
      app.activateComponent(
        configuration.componentType,
        configuration.componentName!!,
        "$watchFaceInfo ${slot.id} ${slot.type}",
        mode,
        receiver)
    }
    catch (ex: DeployerException) {
      throw ExecutionException("Error while launching complication, message: ${receiver.getOutput().ifEmpty { ex.details }}", ex)
    }
  }

  internal fun getComplicationSourceTypes(apks: Collection<ApkInfo>): List<String>{
    return try {
      getComplicationSourceTypes(apks, configuration.componentName!!)
    } catch (exception: Exception) {
      Logger.getInstance(this::class.java).warn(exception)
      emptyList()
    }
  }

  private fun installWatchApp(device: IDevice, console: ConsoleView): App {
    val watchFaceInfo = configuration.watchFaceInfo
    return getApplicationInstaller(console).installAppOnDevice(device, watchFaceInfo.appId, listOf(watchFaceInfo.apk), "")
  }

  private fun startDebugSession(
    device: IDevice,
    console: ConsoleView
  ): Promise<XDebugSessionImpl> {
    checkAndroidVersionForWearDebugging(device.version, console)
    val complicationComponentName = AppComponent.getFQEscapedName(appId, configuration.componentName!!)
    return DebugSessionStarter(environment).attachDebuggerToClient(device,
                                                                   getStopComplicationCallback(complicationComponentName, console, true),
                                                                   console)
  }
}

private fun getStopComplicationCallback(complicationComponentName: String,
                                        console: ConsoleView,
                                        isDebug: Boolean): (IDevice) -> Unit = { device: IDevice ->
  val removeReceiver = CommandResultReceiver()
  val removeComplicationCommand = Complication.ShellCommand.REMOVE_ALL_INSTANCES_FROM_CURRENT_WF + complicationComponentName
  device.executeShellCommand(removeComplicationCommand, console, removeReceiver)

  val unsetReceiver = CommandResultReceiver()
  device.executeShellCommand(UNSET_WATCH_FACE, console, unsetReceiver)
  if (removeReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE || unsetReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printError("Warning: Complication was not stopped.")
  }
  if (isDebug) {
    stopDebugApp(device)
  }
}
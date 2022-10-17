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
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.UNSET_WATCH_FACE
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.configuration.AppRunSettings
import com.android.tools.idea.run.configuration.WearBaseClasses
import com.android.tools.idea.run.configuration.WearSurfaceLaunchOptions
import com.android.tools.idea.run.editor.DeployTarget
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.progress.ProgressIndicatorProvider
import org.jetbrains.android.util.AndroidBundle

private const val WATCH_FACE_MIN_DEBUG_SURFACE_VERSION = 2

open class AndroidWatchFaceConfigurationExecutor(environment: ExecutionEnvironment,
                                                 deployTarget: DeployTarget,
                                                 appRunSettings: AppRunSettings,
                                                 applicationIdProvider: ApplicationIdProvider,
                                                 apkProvider: ApkProvider) : AndroidWearConfigurationExecutor(environment, deployTarget,
                                                                                                              appRunSettings,
                                                                                                              applicationIdProvider,
                                                                                                              apkProvider) {
  private val watchFaceLaunchOptions = appRunSettings.componentLaunchOptions as WatchFaceLaunchOptions
  override fun getStopCallback(console: ConsoleView, isDebug: Boolean) = getStopWatchFaceCallback(console, isDebug)

  @WorkerThread
  override fun launch(device: IDevice, app: App, console: ConsoleView, isDebug: Boolean) {
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val version = device.getWearDebugSurfaceVersion()
    if (version < WATCH_FACE_MIN_DEBUG_SURFACE_VERSION) {
      throw SurfaceVersionException(WATCH_FACE_MIN_DEBUG_SURFACE_VERSION, version, device.isEmulator)
    }
    setWatchFace(app, mode)
    showWatchFace(device, console)
  }

  private fun setWatchFace(app: App, mode: AppComponent.Mode) {
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
      checkCanceled()
      text = "Launching the watch face"
    }
    val outputReceiver = RecordOutputReceiver { indicator?.isCanceled == true }
    try {
      app.activateComponent(watchFaceLaunchOptions.componentType, watchFaceLaunchOptions.componentName!!, mode, outputReceiver)
    }
    catch (ex: DeployerException) {
      throw ExecutionException("Error while launching watch face, message: ${outputReceiver.getOutput().ifEmpty { ex.details }}", ex)
    }
  }
}

class WatchFaceLaunchOptions : WearSurfaceLaunchOptions {
  override val componentType = ComponentType.WATCH_FACE
  override val userVisibleComponentTypeName: String = AndroidBundle.message("android.run.configuration.watchface")
  override val componentBaseClassesFqNames = WearBaseClasses.WATCH_FACES
  override var componentName: String? = null

  fun clone() : WatchFaceLaunchOptions {
    val clone = WatchFaceLaunchOptions()
    clone.componentName = componentName
    return clone
  }
}

private fun getStopWatchFaceCallback(console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit = { device: IDevice ->
  val receiver = CommandResultReceiver()
  device.executeShellCommand(UNSET_WATCH_FACE, console, receiver)
  if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printError("Warning: Watch face was not stopped.")
  }
  if (isDebug) {
    stopDebugApp(device)
  }
}
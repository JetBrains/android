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
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.UNSET_WATCH_FACE
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.WearSurfaceLaunchOptions
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration.ChosenSlot
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo
import com.android.tools.idea.run.configuration.DefaultComplicationWatchFaceInfo
import com.android.tools.idea.run.configuration.WearBaseClasses
import com.android.tools.idea.run.configuration.getComplicationSourceTypes
import com.android.tools.idea.run.configuration.parseRawComplicationTypes
import com.intellij.compiler.options.CompileStepBeforeRun.MakeBeforeRunTask
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.android.util.AndroidBundle
import java.io.File

private const val COMPLICATION_MIN_DEBUG_SURFACE_VERSION = 2
private const val COMPLICATION_RECOMMENDED_DEBUG_SURFACE_VERSION = 3

class AndroidComplicationConfigurationExecutor(
  environment: ExecutionEnvironment,
  deviceFutures: DeviceFutures,
  appRunSettings: AppRunSettings,
  apkProvider: ApkProvider,
  applicationContext: ApplicationProjectContext,
  deployer: ApplicationDeployer
) : AndroidWearConfigurationExecutor(
  environment,
  deviceFutures,
  appRunSettings,
  apkProvider,
  applicationContext,
  deployer
) {
  private val complicationLaunchOptions = appRunSettings.componentLaunchOptions as ComplicationLaunchOptions

  @WorkerThread
  override fun launch(device: IDevice, app: App, console: ConsoleView, isDebug: Boolean, indicator: ProgressIndicator) {
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN

    val version = device.getWearDebugSurfaceVersion(indicator)
    if (version < COMPLICATION_MIN_DEBUG_SURFACE_VERSION) {
      throw SurfaceVersionException(COMPLICATION_MIN_DEBUG_SURFACE_VERSION, version, device.isEmulator)
    }
    if (version < COMPLICATION_RECOMMENDED_DEBUG_SURFACE_VERSION) {
      console.printlnError(AndroidBundle.message("android.run.configuration.debug.surface.warn"))
    }
    ProgressManager.checkCanceled()

    complicationLaunchOptions.verifyProviderTypes(parseRawComplicationTypes(getComplicationSourceTypes(apkProvider.getApks(device))))

    ProgressManager.checkCanceled()

    installWatchApp(device, console, indicator)

    complicationLaunchOptions.chosenSlots.forEach { slot ->
      setComplicationOnWatchFace(app, slot, mode, indicator, device)
    }
    showWatchFace(device, console, indicator)
  }

  private fun setComplicationOnWatchFace(app: App, slot: ChosenSlot, mode: AppComponent.Mode, indicator: ProgressIndicator?, device: IDevice) {
    if (slot.type == null) {
      throw ExecutionException("Slot type is not specified for slot(id: ${slot.id}).")
    }
    val receiver = RecordOutputReceiver { indicator?.isCanceled == true }

    val watchFaceInfo =
      "${complicationLaunchOptions.watchFaceInfo.appId} ${complicationLaunchOptions.watchFaceInfo.watchFaceFQName}"
    try {
      getActivator(app).activate(
        complicationLaunchOptions.componentType,
        complicationLaunchOptions.componentName!!,
        "$watchFaceInfo ${slot.id} ${slot.type}",
        mode,
        receiver,
        device)
    }
    catch (ex: DeployerException) {
      throw ExecutionException("Error while launching complication, message: ${receiver.getOutput().ifEmpty { ex.details }}", ex)
    }
  }

  internal fun getComplicationSourceTypes(apks: Collection<ApkInfo>): List<String>{
    return try {
      getComplicationSourceTypes(apks, complicationLaunchOptions.componentName!!)
    } catch (exception: Exception) {
      Logger.getInstance(this::class.java).warn(exception)
      emptyList()
    }
  }

  private fun installWatchApp(device: IDevice, console: ConsoleView, indicator: ProgressIndicator): App {
    val watchFaceInfo = complicationLaunchOptions.watchFaceInfo

    val apkInfo = ApkInfo(File(watchFaceInfo.apk), watchFaceInfo.appId)
    console.println("Installing test WatchFace...")
    val containsMakeBeforeRun = configuration.beforeRunTasks.any { it.isEnabled }

    return applicationDeployer.fullDeploy(device, apkInfo, appRunSettings.deployOptions, containsMakeBeforeRun,  indicator).app
  }

  override fun getStopCallback(console: ConsoleView, applicationId: String, isDebug: Boolean): (IDevice) -> Unit {
    val complicationComponentName = AppComponent.getFQEscapedName(applicationId, complicationLaunchOptions.componentName!!)
    return getStopComplicationCallback(complicationComponentName, console, isDebug)
  }
}

class ComplicationLaunchOptions : WearSurfaceLaunchOptions {
  override val componentType = ComponentType.COMPLICATION
  override val userVisibleComponentTypeName: String = AndroidBundle.message("android.run.configuration.complication")
  override val componentBaseClassesFqNames = WearBaseClasses.COMPLICATIONS
  override var componentName: String? = null
  var chosenSlots: List<ChosenSlot> = listOf()

  @Transient
  @JvmField
  var watchFaceInfo: ComplicationWatchFaceInfo = DefaultComplicationWatchFaceInfo

  internal fun verifyProviderTypes(supportedTypes: List<Complication.ComplicationType>) {
    if (supportedTypes.isEmpty()) {
      throw RuntimeConfigurationWarning(AndroidBundle.message("no.provider.type.error"))
    }
    for (slot in chosenSlots) {
      val slotType = slot.type ?: throw RuntimeConfigurationWarning(
        AndroidBundle.message("provider.type.empty", watchFaceInfo.complicationSlots.find { it.slotId == slot.id }!!.name))
      if (!supportedTypes.contains(slotType)) {
        throw RuntimeConfigurationWarning(AndroidBundle.message("provider.type.mismatch.error", slotType))
      }
    }
  }

  fun clone() : ComplicationLaunchOptions {
    val clone = ComplicationLaunchOptions()
    clone.componentName = componentName
    clone.chosenSlots = chosenSlots.map { it.copy() }
    return clone
  }
}

private fun getStopComplicationCallback(complicationComponentName: String,
                                        console: ConsoleView,
                                        isDebug: Boolean): (IDevice) -> Unit = { device: IDevice ->
  val removeReceiver = CommandResultReceiver()
  val removeComplicationCommand = Complication.ShellCommand.REMOVE_ALL_INSTANCES_FROM_CURRENT_WF + complicationComponentName
  device.executeShellCommand(removeComplicationCommand, console, removeReceiver, indicator = null)

  val unsetReceiver = CommandResultReceiver()
  device.executeShellCommand(UNSET_WATCH_FACE, console, unsetReceiver, indicator = null)
  if (removeReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE || unsetReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printlnError("Warning: Complication was not stopped.")
  }
  if (isDebug) {
    stopDebugApp(device)
  }
}
/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.MultiReceiver
import com.android.tools.deployer.common.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.deployer.model.component.WearWidget.REQUIRED_PROTOLAYOUT_RENDERER_MIN_VERSION
import com.android.tools.deployer.model.component.WearWidget.ShellCommand.GET_PROTOLAYOUT_RENDERER_VERSION
import com.android.tools.deployer.model.component.WearWidget.ShellCommand.SHOW_WEAR_WIDGET_COMMAND
import com.android.tools.deployer.model.component.WearWidget.ShellCommand.UNSET_WEAR_WIDGET
import com.android.tools.deployer.model.component.WearWidget.isProtolayoutVersionAtLeast
import com.android.tools.deployer.modelv1.component.CommandResultReceiverV1
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.WearSurfaceLaunchOptions
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.WearBaseClasses
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import java.util.concurrent.TimeUnit
import org.jetbrains.android.util.AndroidBundle

private val WEAR_WIDGET_REQUIREMENTS =
  """
  Wear Widgets require version $REQUIRED_PROTOLAYOUT_RENDERER_MIN_VERSION or higher of the com.google.android.wearable.protolayout.renderer APK on the target device.<br/>
  Use the Wear OS 7 emulator image or use a physical Wear OS device that receives automatic updates from the Google Play Store, or a developer device signed in to the Google Play Store.
  """
    .trimIndent()

private const val WIDGET_MIN_DEBUG_SURFACE_VERSION = 2
private const val WIDGET_RECOMMENDED_DEBUG_SURFACE_VERSION = 3

class AndroidWearWidgetConfigurationExecutor(
  environment: ExecutionEnvironment,
  deviceFutures: DeviceFutures,
  appRunSettings: AppRunSettings,
  apkProvider: ApkProvider,
  applicationContext: ApplicationProjectContext,
  deployer: ApplicationDeployer,
) : AndroidWearConfigurationExecutor(environment, deviceFutures, appRunSettings, apkProvider, applicationContext, deployer) {
  private val wearWidgetLaunchOptions = appRunSettings.componentLaunchOptions as WearWidgetLaunchOptions

  override fun getStopCallback(console: ConsoleView, applicationId: String, isDebug: Boolean): (IDevice) -> Unit {
    val widgetName = AppComponent.getFQEscapedName(applicationId, wearWidgetLaunchOptions.componentName!!)
    return getStopWidgetCallback(widgetName, console, isDebug)
  }

  @WorkerThread
  override fun launch(device: IDevice, app: App, console: ConsoleView, isDebug: Boolean, indicator: ProgressIndicator) {
    ProgressManager.checkCanceled()
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN

    // Wear Widgets require a protolayout renderer of version 1.6.1 or higher
    // cf: https://developer.android.com/training/wearables/widgets/get_started#runtime-requirements
    val rendererVersion =
      device.getProtolayoutRendererVersion()
        ?: throw ExecutionException("<html>Protolayout renderer was not found on the device.<br/>$WEAR_WIDGET_REQUIREMENTS</html>")
    if (!isProtolayoutVersionAtLeast(rendererVersion, REQUIRED_PROTOLAYOUT_RENDERER_MIN_VERSION)) {
      throw ExecutionException(
        "<html>The Protolayout renderer version on the device ($rendererVersion) is too low.<br/>$WEAR_WIDGET_REQUIREMENTS</html>"
      )
    }

    val version = device.getWearDebugSurfaceVersion(indicator)
    if (version < WIDGET_MIN_DEBUG_SURFACE_VERSION) {
      throw SurfaceVersionException(WIDGET_MIN_DEBUG_SURFACE_VERSION, version, device.isEmulator)
    }
    if (version < WIDGET_RECOMMENDED_DEBUG_SURFACE_VERSION) {
      console.printlnError(AndroidBundle.message("android.run.configuration.debug.surface.warn"))
    }

    val widgetIndex = setWearWidget(app, mode, indicator, console, device)
    val showWidgetCommand = SHOW_WEAR_WIDGET_COMMAND + widgetIndex
    val showWidgetReceiver = CommandResultReceiverV1()
    device.executeShellCommand(showWidgetCommand, console, showWidgetReceiver, indicator = indicator)
    verifyResponse(showWidgetReceiver, console)
  }

  private fun setWearWidget(app: App, mode: AppComponent.Mode, indicator: ProgressIndicator?, console: ConsoleView, device: IDevice): Int {
    val outputReceiver = RecordOutputReceiver { indicator?.isCanceled == true }
    val consoleReceiver = ConsoleOutputReceiver({ indicator?.isCanceled == true }, console)
    val indexReceiver = AddWidgetCommandResultReceiver { indicator?.isCanceled == true }
    val receiver = MultiReceiver(outputReceiver, consoleReceiver, indexReceiver)
    try {
      getActivator(app).activate(wearWidgetLaunchOptions.componentType, wearWidgetLaunchOptions.componentName!!, mode, receiver, device)
    } catch (ex: DeployerException) {
      throw ExecutionException("Error while setting the widget, message: ${outputReceiver.getOutput().ifEmpty { ex.details }}", ex)
    }

    if (indexReceiver.index == null) {
      throw ExecutionException("Widget index was not found.")
    }
    return indexReceiver.index!!
  }

  private fun verifyResponse(receiver: CommandResultReceiverV1, console: ConsoleView) {
    if (receiver.resultCode != CommandResultReceiverV1.SUCCESS_CODE) {
      console.printlnError("Warning: Launch was successful, but you may need to bring up the widget manually.")
    }
  }
}

private class AddWidgetCommandResultReceiver(private val isCancelledCheck: () -> Boolean) : MultiLineReceiver() {
  private val indexPattern = "Index=\\[(\\d+)]".toRegex()
  var index: Int? = null

  override fun isCancelled(): Boolean = isCancelledCheck()

  override fun processNewLines(lines: Array<String>) =
    lines.forEach { line -> extractPattern(line, indexPattern)?.let { index = it.toInt() } }
}

class WearWidgetLaunchOptions : WearSurfaceLaunchOptions {
  override val componentType = ComponentType.WEAR_WIDGET
  override var componentName: String? = null
  override val userVisibleComponentTypeName: String = AndroidBundle.message("android.run.configuration.wearwidget")
  override val componentBaseClassesFqNames = WearBaseClasses.WIDGETS

  fun clone(): WearWidgetLaunchOptions {
    val clone = WearWidgetLaunchOptions()
    clone.componentName = componentName
    return clone
  }
}

private fun getStopWidgetCallback(widgetName: String, console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit = { device: IDevice ->
  val receiver = CommandResultReceiverV1()
  val removeWidgetCommand = UNSET_WEAR_WIDGET + widgetName
  device.executeShellCommand(removeWidgetCommand, console, receiver, indicator = null)
  if (receiver.resultCode != CommandResultReceiverV1.SUCCESS_CODE) {
    console.printlnError("Warning: Widget was not stopped.")
  }
  if (isDebug) {
    stopDebugApp(device)
  }
}

@VisibleForTesting
internal fun IDevice.getProtolayoutRendererVersion(): String? {
  val receiver = CollectingOutputReceiver()
  try {
    executeShellCommand(GET_PROTOLAYOUT_RENDERER_VERSION, receiver, 5L, TimeUnit.SECONDS)
  } catch (e: Exception) {
    throw ExecutionException("Error while checking protolayout renderer version: ${e.message}", e)
  }
  val output = receiver.output
  val matchResult = "versionName=(\\S+)".toRegex().find(output)
  return matchResult?.groupValues?.get(1)
}

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
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.MultiReceiver
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.deployer.model.component.Tile
import com.android.tools.deployer.model.component.Tile.ShellCommand.SHOW_TILE_COMMAND
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.WearSurfaceLaunchOptions
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.configuration.WearBaseClasses
import com.android.tools.idea.run.editor.DeployTarget
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.android.util.AndroidBundle
import java.time.Duration

private const val TILE_MIN_DEBUG_SURFACE_VERSION = 2
private const val TILE_RECOMMENDED_DEBUG_SURFACE_VERSION = 3

open class AndroidTileConfigurationExecutor(environment: ExecutionEnvironment,
                                            deployTarget: DeployTarget,
                                            appRunSettings: AppRunSettings,
                                            applicationIdProvider: ApplicationIdProvider,
                                            apkProvider: ApkProvider) : AndroidWearConfigurationExecutor(environment, deployTarget,
                                                                                                         appRunSettings,
                                                                                                         applicationIdProvider,
                                                                                                         apkProvider) {
  private val tileLaunchOptions = appRunSettings.componentLaunchOptions as TileLaunchOptions
  override fun getStopCallback(console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit {
    val tileName = AppComponent.getFQEscapedName(appId, tileLaunchOptions.componentName!!)
    return getStopTileCallback(tileName, console, isDebug)
  }

  @WorkerThread
  override fun launch(device: IDevice, app: App, console: ConsoleView, isDebug: Boolean, indicator: ProgressIndicator) {
    ProgressManager.checkCanceled()
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN

    val version = device.getWearDebugSurfaceVersion(indicator)
    if (version < TILE_MIN_DEBUG_SURFACE_VERSION) {
      throw SurfaceVersionException(TILE_MIN_DEBUG_SURFACE_VERSION, version, device.isEmulator)
    }
    if (version < TILE_RECOMMENDED_DEBUG_SURFACE_VERSION) {
      console.printlnError(AndroidBundle.message("android.run.configuration.debug.surface.warn"))
    }

    // TODO(b/226550406): Only add this sleep for older versions where the race condition exists.
    Thread.sleep(Duration.ofSeconds(2).toMillis())
    val tileIndex = setWatchTile(app, mode, indicator, console)
    val showTileCommand = SHOW_TILE_COMMAND + tileIndex!!
    val showTileReceiver = CommandResultReceiver()
    device.executeShellCommand(showTileCommand, console, showTileReceiver, indicator = indicator)
    verifyResponse(showTileReceiver, console)
  }

  private fun setWatchTile(app: App, mode: AppComponent.Mode, indicator: ProgressIndicator?, console: ConsoleView): Int? {
    val outputReceiver = RecordOutputReceiver { indicator?.isCanceled == true }
    val consoleReceiver = ConsoleOutputReceiver({ indicator?.isCanceled == true }, console)
    val indexReceiver = AddTileCommandResultReceiver { indicator?.isCanceled == true }
    val receiver = MultiReceiver(outputReceiver, consoleReceiver, indexReceiver)
    try {
      app.activateComponent(tileLaunchOptions.componentType, tileLaunchOptions.componentName!!, mode, receiver)
    }
    catch (ex: DeployerException) {
      throw ExecutionException("Error while setting the tile, message: ${outputReceiver.getOutput().ifEmpty { ex.details }}", ex)
    }

    if (indexReceiver.index == null) {
      throw ExecutionException("Tile index was not found.")
    }
    return indexReceiver.index!!
  }

  private fun verifyResponse(receiver: CommandResultReceiver, console: ConsoleView) {
    if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
      console.printlnError("Warning: Launch was successful, but you may need to bring up the tile manually.")
    }
  }
}

private class AddTileCommandResultReceiver(private val isCancelledCheck: () -> Boolean) : MultiLineReceiver() {
  private val indexPattern = "Index=\\[(\\d+)]".toRegex()
  var index: Int? = null

  override fun isCancelled(): Boolean = isCancelledCheck()

  override fun processNewLines(lines: Array<String>) = lines.forEach { line ->
    extractPattern(line, indexPattern)?.let { index = it.toInt() }
  }
}

class TileLaunchOptions : WearSurfaceLaunchOptions {
  override val componentType = ComponentType.TILE
  override var componentName: String? = null
  override val userVisibleComponentTypeName: String = AndroidBundle.message("android.run.configuration.tile")
  override val componentBaseClassesFqNames = WearBaseClasses.TILES

  fun clone() : TileLaunchOptions {
    val clone = TileLaunchOptions()
    clone.componentName = componentName
    return clone
  }
}

private fun getStopTileCallback(tileName: String, console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit = { device: IDevice ->
  val receiver = CommandResultReceiver()
  val removeTileCommand = Tile.ShellCommand.UNSET_TILE + tileName
  device.executeShellCommand(removeTileCommand, console, receiver, indicator = null)
  if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printlnError("Warning: Tile was not stopped.")
  }
  if (isDebug) {
    stopDebugApp(device)
  }
}

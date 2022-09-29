/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.benchmark

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.adblib.shellCommand
import com.android.adblib.tools.UninstallResult
import com.android.adblib.tools.install
import com.android.adblib.tools.uninstall
import com.android.adblib.utils.TextShellV2Collector
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

private const val MIRRORING_BENCHMARKER_SOURCE_PATH = "tools/adt/idea/emulator/mirroring-benchmarker"
private const val APP_PKG = "com.android.tools.screensharing.benchmark"
private const val ACTIVITY = "InputEventRenderingActivity"
private const val NO_ANIMATIONS = 65536 // Intent.FLAG_ACTIVITY_NO_ANIMATION
private const val START_COMMAND = "am start -n $APP_PKG/.$ACTIVITY -f $NO_ANIMATIONS"

/** Object that handles installation, launching, and uninstallation of the Mirroring Benchmarker APK. */
interface MirroringBenchmarkerAppInstaller {
  /** Installs the mirroring benchmarker APK, returning `true` iff installation succeeds. */
  suspend fun installBenchmarkingApp() : Boolean
  /** Launches the mirroring benchmarker APK, returning `true` iff launching succeeds. */
  suspend fun launchBenchmarkingApp() : Boolean
  /** Uninstalls the mirroring benchmarker APK, returning `true` iff the operation succeeds. */
  suspend fun uninstallBenchmarkingApp() : Boolean

  companion object {
    operator fun invoke(
      project: Project,
      deviceSerialNumber: String,
      adb: AdbDeviceServices = AdbLibService.getSession(project).deviceServices,
    ) : MirroringBenchmarkerAppInstaller = MirroringBenchmarkerAppInstallerImpl(project, deviceSerialNumber, adb)
  }
}

/** Implementation of [MirroringBenchmarkerAppInstaller]. */
internal class MirroringBenchmarkerAppInstallerImpl(
  private val project: Project,
  deviceSerialNumber: String,
  private val adb: AdbDeviceServices
) : MirroringBenchmarkerAppInstaller {
  private val logger = thisLogger()
  private val deviceSelector = DeviceSelector.fromSerialNumber(deviceSerialNumber)

  override suspend fun installBenchmarkingApp(): Boolean {
    logger.debug("Attempting to install benchmarking app")
    val apkFile: Path
    if (StudioPathManager.isRunningFromSources()) {
      // Development environment.
      val projectDir = project.guessProjectDir()?.toNioPath()
      if (projectDir != null && projectDir.endsWith(MIRRORING_BENCHMARKER_SOURCE_PATH)) {
        // Development environment for the screen sharing agent.
        // Use the agent built by running "Build > Make Project" in Studio.
        logger.debug("App project open, building and installing from here.")
        val facet = project.allModules().firstNotNullOfOrNull { AndroidFacet.getInstance(it) }
        val buildVariant = facet?.properties?.SELECTED_BUILD_VARIANT ?: "debug"
        val apkName = if (buildVariant == "debug") "app-debug.apk" else "app-release-unsigned.apk"
        apkFile = projectDir.resolve("app/build/outputs/apk/$buildVariant/$apkName")
      }
      else {
        // TODO(b/250874751): Implement this use case
        // Development environment for Studio.
        logger.warn("Development environment not supported!")
        return false
      }
    }
    else {
      // TODO(b/250874751): Implement this use case
      // Installed Studio.
      logger.warn("Installed Studio not supported!")
      return false
    }
    adb.install(deviceSelector, listOf(apkFile))
    return true
  }

  @OptIn(ExperimentalTime::class)
  override suspend fun launchBenchmarkingApp(): Boolean {
    logger.debug("Launching benchmarking app")
    val output = adb.shellCommand(deviceSelector, START_COMMAND).withCollector(TextShellV2Collector()).execute().first()
    // TODO(b/250874751): The app should be able to signal when it is ready somehow.
    delay(500.milliseconds)  // Give the app a chance to actually get ready for input.
    return output.exitCode == 0
  }

  override suspend fun uninstallBenchmarkingApp(): Boolean = adb.uninstall(deviceSelector, APP_PKG).status == UninstallResult.Status.SUCCESS
}

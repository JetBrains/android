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
import com.android.adblib.TextShellV2Collector
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import kotlinx.coroutines.flow.first
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.nio.file.Path
import java.util.Base64
import kotlin.time.Duration.Companion.hours

private const val APK_NAME = "mirroring-benchmarker.apk"
private const val PROJECT_NAME = "mirroring-benchmarker"
private const val RELATIVE_PATH = "common/$PROJECT_NAME/$APK_NAME"
private const val SOURCE_PATH = "tools/adt/idea/emulator/$PROJECT_NAME"
private const val PREBUILT_PATH = "prebuilts/tools/$RELATIVE_PATH"
private const val BASE_PREBUILTS_URL = "https://android.googlesource.com/platform/prebuilts/tools/+/refs/heads/mirror-goog-studio-main/"
private const val APK_URL = "$BASE_PREBUILTS_URL$RELATIVE_PATH?format=TEXT"  // Base-64 encoded
private const val APP_PKG = "com.android.tools.screensharing.benchmark"
private const val ACTIVITY = "InputEventRenderingActivity"
private const val NO_ANIMATIONS = 65536 // Intent.FLAG_ACTIVITY_NO_ANIMATION
private const val START_COMMAND = "am start -n $APP_PKG/.$ACTIVITY -f $NO_ANIMATIONS"

/** Object that handles installation, launching, and uninstallation of the Mirroring Benchmarker APK. */
interface MirroringBenchmarkerAppInstaller {
  /** Installs the mirroring benchmarker APK, returning `true` iff installation succeeds. */
  suspend fun installBenchmarkingApp(indicator: ProgressIndicator?) : Boolean
  /** Launches the mirroring benchmarker APK, returning `true` iff launching succeeds. */
  suspend fun launchBenchmarkingApp(indicator: ProgressIndicator?) : Boolean
  /** Uninstalls the mirroring benchmarker APK, returning `true` iff the operation succeeds. */
  suspend fun uninstallBenchmarkingApp() : Boolean

  companion object {
    operator fun invoke(
      project: Project,
      deviceSerialNumber: String,
      adb: AdbWrapper = AdbWrapper.around(AdbLibService.getSession(project).deviceServices),
    ) : MirroringBenchmarkerAppInstaller = MirroringBenchmarkerAppInstallerImpl(project, deviceSerialNumber, adb)
  }

  /** Wrapper to make testing interactions with ADB possible. Without this, testing is very heavy-weight. */
  interface AdbWrapper {
    suspend fun install(serialNumber: String, path: Path) : Boolean
    suspend fun shellCommand(serialNumber: String, command: String) : Boolean
    suspend fun uninstall(serialNumber: String) : Boolean

    companion object {
      fun around(adb: AdbDeviceServices) : AdbWrapper = object: AdbWrapper {
        override suspend fun install(serialNumber: String, path: Path): Boolean {
          return try {
            adb.install(DeviceSelector.fromSerialNumber(serialNumber), listOf(path))
            true
          }
          catch (e: Exception) {
            false
          }
        }
        override suspend fun shellCommand(serialNumber: String, command: String): Boolean =
          adb.shellCommand(DeviceSelector.fromSerialNumber(serialNumber), command).withCollector(TextShellV2Collector())
            .execute().first().exitCode == 0
        override suspend fun uninstall(serialNumber: String): Boolean =
          adb.uninstall(DeviceSelector.fromSerialNumber(serialNumber), APP_PKG).status == UninstallResult.Status.SUCCESS
      }
    }
  }
}

/** Implementation of [MirroringBenchmarkerAppInstaller]. */
internal class MirroringBenchmarkerAppInstallerImpl(
  private val project: Project,
  private val deviceSerialNumber: String,
  private val adb: MirroringBenchmarkerAppInstaller.AdbWrapper
) : MirroringBenchmarkerAppInstaller {
  private val logger = thisLogger()

  override suspend fun installBenchmarkingApp(indicator: ProgressIndicator?): Boolean {
    val msg = "Installing benchmarking app"
    logger.debug(msg)
    indicator?.isIndeterminate = true
    indicator?.text = msg
    val apkFile: Path
    if (StudioPathManager.isRunningFromSources()) {
      // Development environment.
      val projectDir = project.guessProjectDir()?.toNioPath()
      apkFile = if (projectDir != null && projectDir.endsWith(SOURCE_PATH)) {
        // Development environment for the screen sharing agent.
        // Use the agent built by running "Build > Make Project" in Studio.
        logger.debug("App project open, building and installing from here.")
        val facet = project.allModules().firstNotNullOfOrNull { AndroidFacet.getInstance(it) }
        val buildVariant = facet?.properties?.SELECTED_BUILD_VARIANT ?: "debug"
        val apkName = if (buildVariant == "debug") "app-debug.apk" else "app-release-unsigned.apk"
        projectDir.resolve("app/build/outputs/apk/$buildVariant/$apkName")
      }
      else {
        // Development environment for Studio.
        StudioPathManager.resolvePathFromSourcesRoot(PREBUILT_PATH)
      }
    }
    else {
      // Installed Studio.
      try {
        apkFile = UrlFileCache.getInstance(project).get(APK_URL, 12.hours.inWholeMilliseconds, indicator) { Base64.getDecoder().wrap(it) }
      }
      catch (e: Exception) {
        logger.error(e)
        return false
      }
    }
    indicator?.isIndeterminate = true
    indicator?.text = msg
    return adb.install(deviceSerialNumber, apkFile)
  }

  override suspend fun launchBenchmarkingApp(indicator: ProgressIndicator?): Boolean {
    val msg = "Launching benchmarking app"
    logger.debug(msg)
    indicator?.isIndeterminate = true
    indicator?.text = msg
    return adb.shellCommand(deviceSerialNumber, START_COMMAND)
  }

  override suspend fun uninstallBenchmarkingApp(): Boolean = adb.uninstall(deviceSerialNumber)
}

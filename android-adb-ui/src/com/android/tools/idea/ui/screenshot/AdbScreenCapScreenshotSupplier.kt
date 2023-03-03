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

package com.android.tools.idea.ui.screenshot

import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.shellAsText
import com.android.adblib.shellCommand
import com.android.adblib.utils.ByteArrayShellCollector
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.android.tools.idea.ui.screenshot.ScreenshotAction.ScreenshotOptions
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private val commandTimeout = INFINITE_DURATION

/**
 * A [ScreenshotSupplier] that uses `adb shell screencap`
 */
internal class AdbScreenCapScreenshotSupplier(
  project: Project,
  private val serialNumber: String,
  private val screenshotOptions: ScreenshotOptions,
) : ScreenshotSupplier, Disposable {
  private val coroutineScope = AndroidCoroutineScope(this)
  private val adbLibService = AdbLibService.getInstance(project)
  private val deviceDisplayInfoRegex = Regex("\\s(DisplayDeviceInfo\\W.*)")

  @WorkerThread
  override fun captureScreenshot(): ScreenshotImage {
    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    val screenshotJob = coroutineScope.async {
      adbLibService.session.deviceServices.shellCommand(deviceSelector, "screencap -p")
        .withCollector(ByteArrayShellCollector())
        .withCommandTimeout(commandTimeout)
        .executeAsSingleOutput { it }
    }

    val dumpsysJob = coroutineScope.async {
      //TODO: Check for `stderr` and `exitCode` to report errors
      adbLibService.session.deviceServices.shellAsText(deviceSelector, "dumpsys display", commandTimeout = commandTimeout).stdout
    }

    val pmJob = coroutineScope.async {
      //TODO: Check for `stderr` and `exitCode` to report errors
      adbLibService.session.deviceServices.shellAsText(deviceSelector, "pm list features", commandTimeout = commandTimeout).stdout
    }

    return runBlocking {
      val dumpsysOutput = dumpsysJob.await()
      val displayInfo = extractDeviceDisplayInfo(dumpsysOutput)
      val pmOutput = pmJob.await()
      val isTv = pmOutput.contains("feature:android.software.leanback")
      val screenshotBytes = screenshotJob.await()

      @Suppress("BlockingMethodInNonBlockingContext") // Reading from memory is not blocking.
      val image = ImageIO.read(ByteArrayInputStream(screenshotBytes.stdout))
                  ?: throw RuntimeException(AndroidAdbUiBundle.message("screenshot.error.decode"))
      screenshotOptions.createScreenshotImage(image, displayInfo, isTv)
    }
  }

  /**
   * Returns the first line starting with "DisplayDeviceInfo".
   */
  private fun extractDeviceDisplayInfo(dumpsysOutput: String): String =
    deviceDisplayInfoRegex.find(dumpsysOutput)?.groupValues?.get(1) ?: ""

  override fun dispose() {}
}

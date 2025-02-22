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

import com.android.ProgressManagerAdapter
import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.shellAsText
import com.android.adblib.shellCommand
import com.android.adblib.utils.ByteArrayShellCollector
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private val commandTimeout = INFINITE_DURATION

/**
 * A [ScreenshotSupplier] that uses `adb shell screencap`
 */
class AdbScreenCapScreenshotSupplier(
  project: Project,
  private val serialNumber: String,
  private val screenshotOptions: ScreenshotOptions,
) : ScreenshotSupplier {

  private val coroutineScope = createCoroutineScope()
  private val adbLibService = AdbLibService.getInstance(project)
  private val deviceDisplayInfoRegex = Regex("\\s(DisplayDeviceInfo\\W.* state ON,.*)")

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

    return runBlocking {
      val dumpsysOutput = dumpsysJob.await()
      ProgressManagerAdapter.checkCanceled()
      val displayInfo = extractDeviceDisplayInfo(dumpsysOutput)
      ProgressManagerAdapter.checkCanceled()
      val screenshotBytes = screenshotJob.await()
      ProgressManagerAdapter.checkCanceled()

      @Suppress("BlockingMethodInNonBlockingContext") // Reading from memory is not blocking.
      val image = ImageIO.read(ByteArrayInputStream(screenshotBytes.stdout))
                  ?: throw RuntimeException(AndroidAdbUiBundle.message("screenshot.error.decode"))
      screenshotOptions.createScreenshotImage(image, displayInfo)
    }
  }

  /**
   * Returns the first line starting with "DisplayDeviceInfo".
   */
  private fun extractDeviceDisplayInfo(dumpsysOutput: String): String =
    deviceDisplayInfoRegex.find(dumpsysOutput)?.groupValues?.get(1) ?: ""

  override fun dispose() {}
}

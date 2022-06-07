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
import com.android.adblib.utils.ByteArrayShellCollector
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.AndroidAdbBundle
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private val COMMAND_TIMEOUT = INFINITE_DURATION

/**
 * A [ScreenshotSupplier] that uses `adb shell screencap`
 */
internal class AdbScreenCapScreenshotSupplier(
  project: Project,
  private val serialNumber: String,
  private val sdk: Int,
) : ScreenshotSupplier, Disposable {
  private val coroutineScope = AndroidCoroutineScope(this)
  private val adbLibService = AdbLibService.getInstance(project)

  @Slow
  override fun captureScreenshot(): ScreenshotImage {
    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    val screenshotJob = coroutineScope.async {
      adbLibService.session.deviceServices.shell(
        deviceSelector,
        "screencap -p",
        ByteArrayShellCollector(sdk < 24),
        commandTimeout = COMMAND_TIMEOUT,
      ).first()
    }

    val dumpsysJob = coroutineScope.async {
      adbLibService.session.deviceServices.shellAsText(deviceSelector, "dumpsys display", commandTimeout = COMMAND_TIMEOUT)
    }

    return runBlocking {
      val roundScreen = dumpsysJob.await().contains("FLAG_ROUND")
      val screenshotBytes = screenshotJob.await()
      //val size = if (device.sdk < 24) screenshotBytes.removeCarriageReturn() else screenshotBytes.size

      @Suppress("BlockingMethodInNonBlockingContext") // reading from memory is not blocking
      val image = ImageIO.read(ByteArrayInputStream(screenshotBytes))
                  ?: throw java.lang.IllegalStateException(AndroidAdbBundle.message("screenshot.error.decode"))
      ScreenshotImage(image, 0, roundScreen)
    }
  }

  override fun dispose() {}
}

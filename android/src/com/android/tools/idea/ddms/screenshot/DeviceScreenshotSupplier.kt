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
package com.android.tools.idea.ddms.screenshot

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import org.jetbrains.android.util.AndroidBundle
import java.io.IOException
import java.util.concurrent.TimeUnit

@Deprecated(message = "Use com.android.tools.idea.ui.screenshot.AdbScreenCapScreenshotSupplier")
class DeviceScreenshotSupplier(private val device: IDevice) : ScreenshotSupplier {
  @Slow
  override fun captureScreenshot(): ScreenshotImage {
    return try {
      val rawImage = device.getScreenshot(10, TimeUnit.SECONDS)
      if (rawImage.bpp != 16 && rawImage.bpp != 32) {
        throw RuntimeException(AndroidBundle.message("android.ddms.screenshot.task.error.invalid.bpp", rawImage.bpp))
      }
      val receiver = CollectingOutputReceiver()
      device.executeShellCommand("dumpsys display", receiver)
      val roundScreen = receiver.output.contains("FLAG_ROUND")
      DeviceScreenshotImage(rawImage.asBufferedImage(), 0, roundScreen)
    } catch (e: TimeoutException) {
      throw RuntimeException(e)
    } catch (e: AdbCommandRejectedException) {
      throw RuntimeException(e)
    } catch (e: IOException) {
      throw RuntimeException(e)
    } catch (e: ShellCommandUnresponsiveException) {
      throw RuntimeException(e)
    }
  }
}
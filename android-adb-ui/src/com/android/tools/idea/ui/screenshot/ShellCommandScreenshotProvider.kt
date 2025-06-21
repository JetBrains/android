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
import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.shellAsText
import com.android.adblib.tools.screenCapAsBufferedImage
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.ui.DisplayInfoProvider
import com.android.tools.idea.ui.util.getPhysicalDisplayIdFromDumpsysOutput
import com.google.common.base.Throwables.throwIfUnchecked
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.awt.Dimension

private val commandTimeout = INFINITE_DURATION

/** A [ScreenshotProvider] that uses `adb shell screencap` */
class ShellCommandScreenshotProvider(
  project: Project,
  private val serialNumber: String,
  private val deviceType: DeviceType,
  private val deviceName: String,
  private val displayId: Int,
  private val displayInfoProvider: DisplayInfoProvider? = null,
) : ScreenshotProvider {

  private val coroutineScope = createCoroutineScope()
  private val adbLibService = AdbLibService.getInstance(project)

  /** This simplified constructor is intended exclusively for use in TestRecorderScreenshotTask. */
  constructor(project: Project, serialNumber: String) : this(project, serialNumber, DeviceType.HANDHELD, "Device", PRIMARY_DISPLAY_ID)

  override suspend fun captureScreenshot(): ScreenshotImage {
    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)

    val dumpsysJob = coroutineScope.async {
      //TODO: Check for `stderr` and `exitCode` to report errors
      adbLibService.session.deviceServices.shellAsText(deviceSelector, "dumpsys display", commandTimeout = commandTimeout).stdout
    }

    val screenshotJob = coroutineScope.async {
      val physicalDisplayId = when (displayId) {
        PRIMARY_DISPLAY_ID -> null
        else -> getPhysicalDisplayIdFromDumpsysOutput(dumpsysJob.await(), displayId)
      }
      adbLibService.session.deviceServices.screenCapAsBufferedImage(deviceSelector, physicalDisplayId)
    }

    return runBlocking {
      try {
        val dumpsysOutput = dumpsysJob.await()
        ProgressManagerAdapter.checkCanceled()
        val displayInfo = DumpsysDisplayDeviceInfoParser.getActiveDisplays(dumpsysOutput).find { it.logicalId == displayId }
        ProgressManagerAdapter.checkCanceled()
        val image = screenshotJob.await()
        ProgressManagerAdapter.checkCanceled()
        val screenshotRotation = displayInfoProvider?.getScreenshotRotation(displayId) ?: 0
        val rotatedImage = ImageUtils.rotateByQuadrants(image, screenshotRotation)
        val orientation = displayInfoProvider?.getDisplayOrientation(displayId) ?: displayInfo?.orientationQuadrants ?: 0
        val displaySize = displayInfoProvider?.getDisplaySize(displayId) ?: displayInfo?.size ?: Dimension()
        val displayDensity = displayInfo?.density ?: 0
        val isRoundDisplay = displayInfo?.isRound ?: false
        ScreenshotImage(rotatedImage, orientation, deviceType, deviceName, displayId, displaySize, displayDensity, isRoundDisplay)
      }
      catch (e: Throwable) {
        throwIfUnchecked(e)
        throw RuntimeException(e)
      }
    }
  }

  override fun dispose() {}
}

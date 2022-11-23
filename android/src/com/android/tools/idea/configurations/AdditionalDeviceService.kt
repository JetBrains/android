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
package com.android.tools.idea.configurations

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.configurations.AdditionalDeviceService.Companion.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.idea.configurations.AdditionalDeviceService.Companion.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.idea.configurations.AdditionalDeviceService.Companion.DEVICE_CLASS_PHONE_ID
import com.android.tools.idea.configurations.AdditionalDeviceService.Companion.DEVICE_CLASS_TABLET_ID
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import kotlin.math.sqrt

/**
 * Provide the additional [Device]s which exist and be used in Android Studio only.
 * These devices can also be found in the return list of [ConfigurationManager.getDevices].
 */
class AdditionalDeviceService: DumbAware {

  private val windowDevices: List<Device> by lazy { createWindowDevices() }

  companion object {
    @JvmStatic
    fun getInstance(): AdditionalDeviceService? {
      return ApplicationManager.getApplication().getService(AdditionalDeviceService::class.java)
    }

    const val DEVICE_CLASS_PHONE_ID = "_device_class_phone"
    const val DEVICE_CLASS_FOLDABLE_ID = "_device_class_foldable"
    const val DEVICE_CLASS_TABLET_ID = "_device_class_tablet"
    const val DEVICE_CLASS_DESKTOP_ID = "_device_class_desktop"
  }

  fun getWindowSizeDevices(): List<Device> = windowDevices
}


data class WindowSizeData(val id: String, val name: String, val widthDp: Double, val heightDp: Double, val density: Density,
                          val defaultOrientation: ScreenOrientation) {
  val widthPx: Int = widthDp.toPx(density)
  val heightPx: Int = heightDp.toPx(density)
}

/**
 * The device definitions used by Android Studio only
 */
val PREDEFINED_WINDOW_SIZES_DEFINITIONS: List<WindowSizeData> = if (StudioFlags.NELE_DP_SIZED_PREVIEW.get()) {
  listOf(
    WindowSizeData(DEVICE_CLASS_PHONE_ID, "Phone", 411.0, 891.0, Density.DPI_420, ScreenOrientation.PORTRAIT),
    WindowSizeData(DEVICE_CLASS_FOLDABLE_ID, "Foldable", 673.0, 841.0, Density.DPI_420, ScreenOrientation.PORTRAIT),
    WindowSizeData(DEVICE_CLASS_TABLET_ID, "Tablet", 1280.0, 800.0, Density.HIGH, ScreenOrientation.LANDSCAPE),
    WindowSizeData(DEVICE_CLASS_DESKTOP_ID, "Desktop", 1920.0, 1080.0, Density.MEDIUM, ScreenOrientation.LANDSCAPE))
} else {
  listOf(
    WindowSizeData(DEVICE_CLASS_PHONE_ID, "Phone", 411.0, 891.0, Density.DPI_420, ScreenOrientation.PORTRAIT),
    WindowSizeData(DEVICE_CLASS_FOLDABLE_ID, "Foldable", 673.5, 841.0, Density.XXHIGH, ScreenOrientation.PORTRAIT),
    WindowSizeData(DEVICE_CLASS_TABLET_ID, "Tablet", 1280.0, 800.0, Density.XXHIGH, ScreenOrientation.LANDSCAPE),
    WindowSizeData(DEVICE_CLASS_DESKTOP_ID, "Desktop", 1920.0, 1080.0, Density.XXHIGH, ScreenOrientation.LANDSCAPE))
}

private fun createWindowDevices(): List<Device> =
  PREDEFINED_WINDOW_SIZES_DEFINITIONS.map { windowSizeDef ->
    val deviceHardware = Hardware().apply {
      screen = Screen().apply {
        xDimension = windowSizeDef.widthPx
        yDimension = windowSizeDef.heightPx
        pixelDensity = windowSizeDef.density

        xdpi = pixelDensity.dpiValue.toDouble()
        ydpi = pixelDensity.dpiValue.toDouble()

        val widthDp = windowSizeDef.widthDp
        val heightDp = windowSizeDef.heightDp
        diagonalLength = sqrt(widthDp * widthDp + heightDp * heightDp) / 160
        size = ScreenSize.getScreenSize(diagonalLength)
        ratio = AvdScreenData.getScreenRatio(xDimension, yDimension)
        screenRound = ScreenRound.NOTROUND
        chin = 0
      }
    }

    Device.Builder().apply {
      setTagId("")
      setId(windowSizeDef.id)
      setName(windowSizeDef.name)
      setManufacturer("")
      addSoftware(Software())
      addState(State().apply {
        isDefaultState = windowSizeDef.defaultOrientation == ScreenOrientation.PORTRAIT

        hardware = deviceHardware
        name = "portrait"
        orientation = ScreenOrientation.PORTRAIT
      })
      addState(State().apply {
        isDefaultState = windowSizeDef.defaultOrientation == ScreenOrientation.LANDSCAPE

        hardware = deviceHardware
        name = "landscape"
        orientation = ScreenOrientation.LANDSCAPE
      })
    }.build()
  }

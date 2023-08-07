/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.preview.config

import com.android.resources.ScreenOrientation
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID

/** Default device configuration for Phones */
val ReferencePhoneConfig: DeviceConfig by
  lazy(LazyThreadSafetyMode.NONE) {
    PREDEFINED_WINDOW_SIZES_DEFINITIONS.first { it.id == DEVICE_CLASS_PHONE_ID }
      .toDeviceConfigWithDpDimensions()
  }

/** Default device configuration for Foldables */
val ReferenceFoldableConfig: DeviceConfig by
  lazy(LazyThreadSafetyMode.NONE) {
    PREDEFINED_WINDOW_SIZES_DEFINITIONS.first { it.id == DEVICE_CLASS_FOLDABLE_ID }
      .toDeviceConfigWithDpDimensions()
  }

/** Default device configuration for Tablets */
val ReferenceTabletConfig: DeviceConfig by
  lazy(LazyThreadSafetyMode.NONE) {
    PREDEFINED_WINDOW_SIZES_DEFINITIONS.first { it.id == DEVICE_CLASS_TABLET_ID }
      .toDeviceConfigWithDpDimensions()
  }

/** Default device configuration for Desktops */
val ReferenceDesktopConfig: DeviceConfig by
  lazy(LazyThreadSafetyMode.NONE) {
    PREDEFINED_WINDOW_SIZES_DEFINITIONS.first { it.id == DEVICE_CLASS_DESKTOP_ID }
      .toDeviceConfigWithDpDimensions()
  }

private fun WindowSizeData.toDeviceConfigWithDpDimensions() =
  DeviceConfig(
    width = widthDp.toFloat(),
    height = heightDp.toFloat(),
    dimUnit = DimUnit.dp,
    dpi = density.dpiValue,
    shape = Shape.Normal,
    orientation =
      when (defaultOrientation) {
        ScreenOrientation.LANDSCAPE -> Orientation.landscape
        else -> Orientation.portrait
      }
  )

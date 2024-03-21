/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import com.android.sdklib.deviceprovisioner.Resolution
import com.google.common.collect.Range

val TestDeviceSource.mediumPhone
  get() =
    device(
      manufacturer = "Generic",
      name = "Medium Phone",
      apiRange = Range.closed(24, 34),
      resolution = Resolution(1080, 2400),
      displayDensity = 420,
      isVirtual = true,
      isRemote = false,
    )

val TestDeviceSource.remotePixel5
  get() =
    device(
      manufacturer = "Google",
      name = "Pixel 5",
      apiRange = Range.closed(30, 30),
      resolution = Resolution(1080, 2340),
      displayDensity = 440,
      isVirtual = false,
      isRemote = true,
    )

val TestDeviceSource.pixelFold
  get() =
    device(
      manufacturer = "Google",
      name = "Pixel Fold",
      apiRange = Range.closed(33, 33),
      resolution = Resolution(2208, 1840),
      displayDensity = 420,
      isVirtual = true,
      isRemote = false,
    )

val TestDeviceSource.pixelTablet
  get() =
    device(
      manufacturer = "Google",
      name = "Pixel Tablet",
      apiRange = Range.closed(33, 33),
      resolution = Resolution(2560, 1600),
      displayDensity = 320,
      isVirtual = true,
      isRemote = false,
    )

val TestDeviceSource.wearLargeRound
  get() =
    device(
      manufacturer = "Google",
      name = "Wear OS Large Round",
      apiRange = Range.closed(20, 34),
      resolution = Resolution(454, 454),
      displayDensity = 320,
      isVirtual = true,
      isRemote = false,
    )

val TestDeviceSource.galaxyS22
  get() =
    device(
      manufacturer = "Samsung",
      name = "Galaxy S22",
      apiRange = Range.closed(33, 33),
      resolution = Resolution(1440, 3088),
      displayDensity = 600,
      isVirtual = false,
      isRemote = true,
    )

val TestDeviceSource.automotive
  get() =
    device(
      manufacturer = "Google",
      name = "Automotive (1024p landscape)",
      apiRange = Range.closed(28, 34),
      resolution = Resolution(1024, 768),
      displayDensity = 160,
      isVirtual = true,
      isRemote = false,
    )

val TestDeviceSource.tv4k
  get() =
    device(
      manufacturer = "Google",
      name = "Television (4K)",
      apiRange = Range.closed(31, 34),
      resolution = Resolution(3840, 2160),
      displayDensity = 640,
      isVirtual = true,
      isRemote = false,
    )

val TestDeviceSource.allTestDevices
  get() =
    listOf(
      automotive,
      galaxyS22,
      mediumPhone,
      pixelFold,
      pixelTablet,
      remotePixel5,
      tv4k,
      wearLargeRound,
    )

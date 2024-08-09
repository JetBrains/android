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
import com.android.sdklib.devices.Abi
import kotlin.time.Duration.Companion.minutes

internal object TestDevices {
  val mediumPhone =
    TestDevice(
      apiLevels = androidVersionRange(24, 34),
      manufacturer = "Generic",
      name = "Medium Phone",
      resolution = Resolution(1080, 2400),
      displayDensity = 420,
      displayDiagonalLength = 6.4,
      isVirtual = true,
      isRemote = false,
      abis = listOf(Abi.ARM64_V8A),
      isAlreadyPresent = false,
      formFactor = FormFactors.PHONE,
    )

  val remotePixel5 =
    TestDevice(
      apiLevels = androidVersionRange(30, 30),
      manufacturer = "Google",
      name = "Pixel 5",
      resolution = Resolution(1080, 2340),
      displayDensity = 440,
      displayDiagonalLength = 6.0,
      isVirtual = false,
      isRemote = true,
      abis = listOf(Abi.ARM64_V8A),
      isAlreadyPresent = false,
      availabilityEstimate = 5.minutes,
      formFactor = FormFactors.PHONE,
    )

  val pixelFold =
    TestDevice(
      apiLevels = androidVersionRange(33, 34),
      manufacturer = "Google",
      name = "Pixel Fold",
      resolution = Resolution(2208, 1840),
      displayDensity = 420,
      displayDiagonalLength = 7.58,
      isVirtual = true,
      isRemote = false,
      abis = listOf(Abi.ARM64_V8A, Abi.RISCV64),
      isAlreadyPresent = false,
      formFactor = FormFactors.PHONE,
    )

  val pixelTablet =
    TestDevice(
      apiLevels = androidVersionRange(33, 33),
      manufacturer = "Google",
      name = "Pixel Tablet",
      resolution = Resolution(2560, 1600),
      displayDensity = 320,
      displayDiagonalLength = 10.95,
      isVirtual = true,
      isRemote = false,
      abis = listOf(Abi.ARM64_V8A),
      isAlreadyPresent = false,
      formFactor = FormFactors.TABLET,
    )

  val wearLargeRound =
    TestDevice(
      apiLevels = androidVersionRange(20, 34),
      manufacturer = "Google",
      name = "Wear OS Large Round",
      resolution = Resolution(454, 454),
      displayDensity = 320,
      displayDiagonalLength = 1.39,
      isRound = true,
      isVirtual = true,
      isRemote = false,
      abis = listOf(Abi.ARM64_V8A),
      isAlreadyPresent = false,
      formFactor = FormFactors.WEAR,
    )

  val galaxyS22 =
    TestDevice(
      apiLevels = androidVersionRange(33, 33),
      manufacturer = "Samsung",
      name = "Galaxy S22",
      resolution = Resolution(1440, 3088),
      displayDensity = 600,
      isVirtual = false,
      isRemote = true,
      abis = listOf(Abi.ARM64_V8A),
      isAlreadyPresent = false,
      formFactor = FormFactors.PHONE,
    )

  val automotive =
    TestDevice(
      apiLevels = androidVersionRange(28, 34),
      manufacturer = "Google",
      name = "Automotive (1024p landscape)",
      resolution = Resolution(1024, 768),
      displayDensity = 160,
      isVirtual = true,
      isRemote = false,
      abis = listOf(Abi.ARM64_V8A),
      isAlreadyPresent = false,
      formFactor = FormFactors.AUTO,
    )

  val tv4k =
    TestDevice(
      apiLevels = androidVersionRange(31, 34),
      manufacturer = "Google",
      name = "Television (4K)",
      resolution = Resolution(3840, 2160),
      displayDensity = 640,
      displayDiagonalLength = 55.0,
      isVirtual = true,
      isRemote = false,
      abis = listOf(Abi.ARM64_V8A),
      isAlreadyPresent = false,
      formFactor = FormFactors.TV,
    )

  val allTestDevices =
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
}

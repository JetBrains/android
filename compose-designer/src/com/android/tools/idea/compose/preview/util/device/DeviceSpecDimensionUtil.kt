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
package com.android.tools.idea.compose.preview.util.device

import kotlin.math.pow
import kotlin.math.roundToInt

private const val SUPPORTED_DECIMALS = 1

fun convertToDeviceSpecDimension(floatNumber: Float): Number {
  val decimalsMultiplier: Float = 10f.pow(SUPPORTED_DECIMALS)
  val significantDecimalsMultiplier: Float = decimalsMultiplier.times(10)

  // If the decimal part is not significant (< .05 or >= .95) return the rounded integer, otherwise
  // round to 1 decimal
  val roundedNumber = floatNumber.roundToInt()
  val decimalDiff = (floatNumber - roundedNumber).times(significantDecimalsMultiplier).roundToInt()
  if (decimalDiff >= -5 && decimalDiff < 5) { // Better to compare the decimals as integers
    return roundedNumber
  }
  return floatNumber.times(decimalsMultiplier).roundToInt().div(decimalsMultiplier)
}

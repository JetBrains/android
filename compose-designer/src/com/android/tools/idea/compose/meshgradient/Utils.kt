/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.meshgradient

import androidx.compose.ui.graphics.Color
import java.math.RoundingMode

fun formatFloat(number: Float): String {
  return number.toBigDecimal().setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

fun Color.toHexStringNoHash(includeAlpha: Boolean = false): String {
  return if (includeAlpha) {
    String.format("%02X%02X%02X%02X", (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
  } else {
    String.format("%02X%02X%02X", (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
  }
}

fun String.toColor(): Color {
  var processedColorString = this.trim()

  if (processedColorString.startsWith("#")) {
    processedColorString = processedColorString.substring(1)
  }

  if (processedColorString.length != 6 && processedColorString.length != 8) {
    throw IllegalArgumentException("Invalid color string: $this")
  }

  val colorLong = processedColorString.toLongOrNull(16) ?: throw IllegalArgumentException("Invalid color string: $this")

  val alpha =
    if (processedColorString.length == 8) {
      (colorLong shr 24 and 0xFF).toFloat() / 255f
    } else {
      1.0f
    }
  val red = (colorLong shr 16 and 0xFF).toFloat() / 255f
  val green = (colorLong shr 8 and 0xFF).toFloat() / 255f
  val blue = (colorLong and 0xFF).toFloat() / 255f

  return Color(red, green, blue, alpha)
}

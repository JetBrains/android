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
package com.android.tools.adtui.compose

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.times

/**
 * Computes a new line height by scaling the current line height based on the ratio between
 * [newSize] and the current [TextStyle.fontSize].
 *
 * If either [newSize] or the current font size is unspecified, or if the current font size is zero,
 * the original line height is returned.
 */
fun TextStyle.computeLineHeightScaledForSize(newSize: TextUnit): TextUnit {
  if (!newSize.isSpecified || !fontSize.isSpecified || fontSize.value == 0f) return lineHeight
  return (newSize / fontSize) * lineHeight
}

/**
 * Divides one [TextUnit] by another, provided they have the same unit type (Sp or Em).
 *
 * @throws IllegalArgumentException if the units are not compatible or are unspecified.
 */
private operator fun TextUnit.div(other: TextUnit): Float {
  require(isSpecified && other.isSpecified) { "Cannot divide unspecified TextUnit" }

  return when {
    isSp && other.isSp -> value / other.value
    isEm && other.isEm -> value / other.value
    else -> throw IllegalArgumentException("Cannot divide $type by ${other.type}")
  }
}

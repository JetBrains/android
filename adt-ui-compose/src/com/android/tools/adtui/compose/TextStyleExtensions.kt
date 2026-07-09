/*
 * Copyright (C) 2025 The Android Open Source Project
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * Line-height multiplier that reproduces the line height of the (removed) Jewel
 * `TextStyle.copyWithSize` / `Typography.DefaultLineHeightMultiplier`.
 */
private const val DEFAULT_LINE_HEIGHT_MULTIPLIER = 1.3f

/**
 * Returns a copy of this [TextStyle] with the given [fontSize] and a line height derived from it
 * (`fontSize * 1.3`).
 *
 * This is a replacement for the removed Jewel `TextStyle.copyWithSize` extension, preserving its
 * line-height behavior so text keeps the same vertical rhythm when only the font size changes.
 */
fun TextStyle.copyWithSize(
  fontSize: TextUnit,
  fontWeight: FontWeight? = this.fontWeight,
  letterSpacing: TextUnit = this.letterSpacing,
): TextStyle =
  copy(
    fontSize = fontSize,
    lineHeight = fontSize * DEFAULT_LINE_HEIGHT_MULTIPLIER,
    fontWeight = fontWeight,
    letterSpacing = letterSpacing,
  )

/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.common

import com.intellij.util.ui.JBUI

/**
 * Represents the distance between two points in swing space
 * Corresponds to the [SwingCoordinate] attribute
 */
inline class SwingLength(val value: Float) {
  operator fun plus(rhs: SwingLength) = SwingLength(value + rhs.value)
  operator fun minus(rhs: SwingLength) = SwingLength(value - rhs.value)
  operator fun times(rhs: Int) = SwingLength(value * rhs)
  fun toInt() = value.toInt()
  fun toDouble() = value.toDouble()
}

fun scaledSwingLength(value: Float) = SwingLength(JBUI.scale(value))
operator fun Int.times(rhs: SwingLength) = rhs * this
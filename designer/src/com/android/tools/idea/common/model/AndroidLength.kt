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
package com.android.tools.idea.common.model

import com.intellij.ui.scale.JBUIScale

/**
 * Represents the distance between two points in android space Corresponds to the
 * [AndroidCoordinate] attribute
 */
@JvmInline
value class AndroidLength(val value: Float) {
  operator fun plus(rhs: AndroidLength) = AndroidLength(value + rhs.value)

  operator fun minus(rhs: AndroidLength) = AndroidLength(value - rhs.value)

  operator fun times(rhs: Int) = AndroidLength(value * rhs)

  operator fun times(rhs: Float) = AndroidLength(value * rhs)

  operator fun div(rhs: AndroidLength) = value / rhs.value

  fun toInt() = value.toInt()
}

fun scaledAndroidLength(value: Float) = AndroidLength(JBUIScale.scale(value))

operator fun Int.times(rhs: AndroidLength) = rhs * this

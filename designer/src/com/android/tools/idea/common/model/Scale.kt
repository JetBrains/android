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

import com.android.tools.adtui.common.SwingLength
import java.awt.Dimension

/**
 * Represents the scale factor of the design surface and the conversion factor
 * between [SwingLength] and [AndroidLength]

 * AndroidLength * Scale = SwingLength
 * SwingLength / Scale = AndroidLength
 */

inline class Scale(val value: Double) {
  operator fun times(length: AndroidLength): SwingLength = SwingLength(value.toFloat() * length.value)
}

operator fun AndroidLength.times(rhs: Scale): SwingLength = rhs * this
operator fun SwingLength.div(rhs: Scale): AndroidLength = AndroidLength(rhs.value.toFloat() / value)
fun Dimension.scaleBy(scale: Double): Dimension {
  setSize((scale * width).toInt(), (scale * height).toInt())
  return this
}

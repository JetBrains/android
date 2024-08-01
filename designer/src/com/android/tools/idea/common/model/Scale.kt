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
 * Represents the scale factor of the design surface and the conversion factor between [SwingLength]
 * and [AndroidLength]
 *
 * AndroidLength * Scale = SwingLength SwingLength / Scale = AndroidLength
 */
@JvmInline
value class Scale(val value: Double) {
  operator fun times(length: AndroidLength): SwingLength =
    SwingLength(value.toFloat() * length.value)
}

operator fun AndroidLength.times(rhs: Scale): SwingLength = rhs * this

operator fun SwingLength.div(rhs: Scale): AndroidLength = AndroidLength(rhs.value.toFloat() / value)

/**
 * Apply a scale to the receiver. Notice: this function has lateral effects, the receiver would also
 * change its size.
 *
 * @return the new scaled dimension applied to the receiver.
 */
fun Dimension.scaleBy(scale: Double): Dimension {
  setSize((scale * width).toInt(), (scale * height).toInt())
  return this
}

/**
 * Returns a dimension with an applied scale factor. Notice: this function doesn't have lateral
 * effects, it doesn't change the Dimension
 *
 * @return the new scaled dimension.
 */
fun Dimension.scaleOf(scale: Double): Dimension {
  return Dimension((scale * width).toInt(), (scale * height).toInt())
}

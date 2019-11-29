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

import java.awt.geom.RoundRectangle2D

inline class SwingRoundRectangle(val value: RoundRectangle2D.Float) {
  constructor(rectangle: SwingRectangle, arcwidth: SwingLength, archeight: SwingLength)
    : this(RoundRectangle2D.Float(
    rectangle.x.value, rectangle.y.value, rectangle.width.value, rectangle.height.value, arcwidth.value, archeight.value))

  val x: SwingX
    get() = SwingX(value.x)

  val y: SwingY
    get() = SwingY(value.y)

  val width: SwingLength
    get() = SwingLength(value.width)

  val height: SwingLength
    get() = SwingLength(value.height)
}

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

import java.awt.geom.Rectangle2D

/**
 * Represents a rectangle in swing space
 * Defined by [SwingX], [SwingY], and length and width of [SwingLength]
 */
inline class SwingRectangle(override val value: Rectangle2D.Float) : SwingShape {
  constructor(x: SwingX, y: SwingY, width: SwingLength, height: SwingLength)
    : this(Rectangle2D.Float(x.value, y.value, width.value, height.value))

  val x: SwingX
    get() = SwingX(value.x)

  val y: SwingY
    get() = SwingY(value.y)

  val width: SwingLength
    get() = SwingLength(value.width)

  val height: SwingLength
    get() = SwingLength(value.height)

  val center: SwingPoint
    get() = SwingPoint(SwingX(value.centerX.toFloat()), SwingY(value.centerY.toFloat()))

  fun growRectangle(growX: SwingLength, growY: SwingLength) =
    SwingRectangle(this.x - growX, this.y - growY, this.width + 2 * growX, this.height + 2 * growY)

  fun contains(p: SwingPoint) =
    p.x > this.x && p.x < this.x + this.width && p.y > this.y && p.y < this.y + this.height

  fun intersects(r: SwingRectangle) = value.intersects(r.value)
}

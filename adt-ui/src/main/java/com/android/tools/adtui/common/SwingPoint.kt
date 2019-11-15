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

import java.awt.geom.Point2D

private const val SEPARATOR = 'x'

/**
 * Represents a point position in swing space
 * defined by [SwingX] and [SwingY] coordinates
 */
inline class SwingPoint(val value: Point2D.Float) {
  constructor(x: SwingX, y: SwingY) : this(Point2D.Float(x.value, y.value))

  val x: SwingX
    get() = SwingX(value.x)

  val y: SwingY
    get() = SwingY(value.y)

  override fun toString() = "${this.value.x}$SEPARATOR${this.value.y}"
}

fun distance(a: SwingPoint, b: SwingPoint): SwingLength =
  hypotenuse(a.x - b.x, a.y - b.y)

fun String.toSwingPoint(): SwingPoint {
  val (x, y) = this.split(SEPARATOR)
  return SwingPoint(x.toSwingX(), y.toSwingY())
}
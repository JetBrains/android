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

import java.awt.geom.Path2D

inline class SwingPath(val value: Path2D.Float) {
  constructor() : this(Path2D.Float())

  fun moveTo(point: SwingPoint) {
    moveTo(point.x, point.y)
  }

  fun moveTo(x: SwingX, y: SwingY) {
    value.moveTo(x.value, y.value)
  }

  fun curveTo(point1: SwingPoint, point2: SwingPoint, point3: SwingPoint) {
    value.curveTo(point1.x.value, point1.y.value,
                  point2.x.value, point2.y.value,
                  point3.x.value, point3.y.value)

  }

  fun lineTo(x: SwingX, y: SwingY) {
    value.lineTo(x.value, y.value)
  }

  fun closePath() {
    value.closePath()
  }
}
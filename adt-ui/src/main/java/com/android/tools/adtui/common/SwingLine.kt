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

import java.awt.geom.Line2D
/**
 * Represents a line in swing space running between the points
 * defined by x1, y1 and x2, y2
 */
inline class SwingLine(override val value: Line2D.Float) : SwingShape {
  constructor(x1: SwingX, y1: SwingY, x2: SwingX, y2: SwingY) : this(Line2D.Float(x1.value, y1.value, x2.value, y2.value))

  val x1: SwingX
    get() = SwingX(value.x1)

  val y1: SwingY
    get() = SwingY(value.y1)

  val x2: SwingX
    get() = SwingX(value.x2)

  val y2: SwingY
    get() = SwingY(value.y2)
}

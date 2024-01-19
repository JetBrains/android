/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.scene

import java.awt.Point

class LerpPoint : LerpValue<Point> {
  constructor(start: Point, end: Point, duration: Int) : super(start, end, duration)

  override fun interpolate(fraction: Float): Point {
    return Point(
      start.x + ((end.x - start.x) * fraction).toInt(),
      start.y + ((end.y - start.y) * fraction).toInt(),
    )
  }
}

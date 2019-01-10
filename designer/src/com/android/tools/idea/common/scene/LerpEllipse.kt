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
package com.android.tools.idea.common.scene

import java.awt.geom.Ellipse2D

class LerpEllipse(start: Ellipse2D.Float, end: Ellipse2D.Float, duration: Int)
  : LerpValue<Ellipse2D.Float>(start, end, duration) {
  override fun interpolate(fraction: Float): Ellipse2D.Float {
    return Ellipse2D.Float(interpolate(start.x, end.x, fraction),
                           interpolate(start.y, end.y, fraction),
                           interpolate(start.width, end.width, fraction),
                           interpolate(start.height, end.height, fraction))
  }

  companion object {
    private fun interpolate(start: Float, end: Float, fraction: Float): Float {
      return start + (end - start) * fraction
    }
  }
}
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
package com.android.tools.idea.naveditor.scene.draw

import org.mockito.ArgumentMatcher
import java.awt.Shape
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D

class ShapeArgumentMatcher(private val expected: Shape) : ArgumentMatcher<Shape> {
  override fun matches(argument: Shape?): Boolean {
    return when (argument) {
      is Line2D.Float -> matchLine(argument)
      is RoundRectangle2D.Float -> matchRoundRectangle(argument)
      else -> expected == argument
    }
  }

  private fun matchLine(line: Line2D.Float): Boolean {
    return (expected as? Line2D.Float)?.let {
      it.x1 == line.x1 && it.x2 == line.x2 && it.y1 == line.y1 && it.y2 == line.y2
    } ?: false
  }

  private fun matchRoundRectangle(rectangle: RoundRectangle2D.Float): Boolean {
    return (expected as? RoundRectangle2D.Float)?.let {
      it.x == rectangle.x && it.y == rectangle.y
      && it.width == rectangle.width && it.height == rectangle.height
      && it.arcwidth == rectangle.arcwidth && it.archeight == rectangle.archeight
    } ?: false
  }
}
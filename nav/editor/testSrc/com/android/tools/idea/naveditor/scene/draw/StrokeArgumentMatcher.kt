/*
 * Copyright (C) 2020 The Android Open Source Project
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
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.Stroke
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D

class StrokeArgumentMatcher(private val expected: Stroke) : ArgumentMatcher<Stroke> {
  override fun matches(argument: Stroke?): Boolean {
    return when (argument) {
      is BasicStroke -> matchBasicStroke(argument)
      else -> expected == argument
    }
  }

  private fun matchBasicStroke(stroke: BasicStroke) = (expected as? BasicStroke)?.lineWidth == stroke.lineWidth
}
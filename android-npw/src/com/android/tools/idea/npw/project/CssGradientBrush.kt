/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.project

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.tan

/*
 * This is an exact copy of com.android.studio.ml.bot.ui.compose.components.CssGradientBrush. We should probably
 * only have this in one place, but we need to think about dependency structure.
 */

private const val angleEpsilon = .001

/**
 * Draws a gradient like CSS would. For an in-depth explanation of how this works, refer to
 * [this article](https://blog.sebastiano.dev/say-hi-like-youre-ai-gradient-text-in-compose-for-desktop/).
 */
class CssGradientBrush(
  private val angleDegrees: Double,
  private var colors: List<Color>,
  private var stops: List<Float>? = null,
  private var scaleX: Float = 1f,
  private var scaleY: Float = 1f,
  private var offset: Offset = Offset.Zero,
) : ShaderBrush() {

  init {
    val colorStops = stops
    check(colorStops == null || colors.size == colorStops.size) {
      "The number of stops and colors must match"
    }
    check(colors.isNotEmpty()) { "Specify at least one color and stop" }
  }

  override fun createShader(size: Size): Shader {
    val normalizedAngle = angleDegrees % 360.0
    val adjustedSize = Size(size.width * scaleX, size.height * scaleY)

    // Handle base cases (vertical and horizontal gradient) separately
    return when {
      abs(normalizedAngle % 180.0) < angleEpsilon -> {
        val leftToRight = abs(normalizedAngle) < 90.0
        createHorizontalGradient(adjustedSize, leftToRight, offset)
      }
      abs(abs(normalizedAngle) - 90.0) < angleEpsilon -> {
        val startsFromTop = normalizedAngle >= 0.0
        createVerticalGradient(adjustedSize, startsFromTop, offset)
      }
      else -> createLinearGradient(adjustedSize, normalizedAngle, offset)
    }
  }

  private fun createHorizontalGradient(size: Size, leftToRight: Boolean, offset: Offset): Shader {
    val startX = if (leftToRight) 0f else size.width
    val endX = if (leftToRight) size.width else 0f

    val offsetX = size.width * offset.x
    val offsetY = size.height * offset.y

    return LinearGradientShader(
      from = Offset(startX + offsetX, size.height / 2 + offsetY),
      to = Offset(endX + offsetX, size.height / 2 + offsetY),
      colors = colors,
      colorStops = stops,
    )
  }

  private fun createVerticalGradient(size: Size, topToBottom: Boolean, offset: Offset): Shader {
    val startY = if (topToBottom) 0f else size.height
    val endY = if (topToBottom) size.height else 0f

    val offsetX = size.width * offset.x
    val offsetY = size.height * offset.y

    return LinearGradientShader(
      from = Offset(size.width / 2 + offsetX, startY + offsetY),
      to = Offset(size.width / 2 + offsetX, endY + offsetY),
      colors = colors,
      colorStops = stops,
    )
  }

  private fun createLinearGradient(size: Size, angleDegrees: Double, offset: Offset): Shader {
    // Calculate the angle in radians
    val angleRadians = Math.toRadians(angleDegrees)

    // Determine the closest corners to the intersection points
    val normalizedAngle = (angleDegrees + 180) % 360.0
    val (startCorner, endCorner) =
      when {
        normalizedAngle < 90.0 -> Offset(size.width, size.height) to Offset(0f, 0f)
        normalizedAngle < 180.0 -> Offset(0f, size.height) to Offset(size.width, 0f)
        normalizedAngle < 270.0 -> Offset(0f, 0f) to Offset(size.width, size.height)
        else -> Offset(size.width, 0f) to Offset(0f, size.height)
      }

    val offsetX = size.width * offset.x
    val offsetY = size.height * offset.y
    val finalOffset = Offset(offsetX, offsetY)

    val gradientStart = calculateProjection(size.center, angleRadians, startCorner) + finalOffset
    val gradientEnd = calculateProjection(size.center, angleRadians, endCorner) + finalOffset

    // We need to reverse gradient end and start points to get the intended effect
    return LinearGradientShader(
      from = gradientStart,
      to = gradientEnd,
      colors = colors,
      colorStops = stops,
    )
  }

  private fun calculateProjection(
    linePoint: Offset,
    angleRadians: Double,
    pointToProject: Offset,
  ): Offset {
    // Calculate slope from angle
    val m = tan(angleRadians)

    // Calculate y-intercept (b) using the point-slope form
    val b = linePoint.y - m * linePoint.x

    // Slope of the perpendicular line
    val m_perp = -1.0 / m

    // Equation of the perpendicular line passing through pointToProject
    // y - y1 = m_perp (x - x1)
    // y = m_perp * (x - x1) + y1

    // Solve for intersection point (xp, yp)
    val xp = (b - pointToProject.x / m - pointToProject.y) / (-1.0 / m - m)
    val yp = m * xp + b

    return Offset(xp.toFloat(), yp.toFloat())
  }
}

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
package com.android.tools.idea.uibuilder.visual.colorblindmode

import java.util.function.Function
import junit.framework.TestCase
import kotlin.math.abs
import kotlin.math.pow

/** Tests interpolation with the uniform [ColorLut] */
class ColorLutTest : TestCase() {

  private val uniform = buildUniformColorLut(DIM)
  private val removeGammaCLut: DoubleArray = buildGammaCLut(Function { (it / 255.0).pow(GAMMA) })

  fun testUniform() {
    val threshhold = 4
    val uniform1 = buildUniformColorLut(DIM)
    val uniform2 = buildColorLut(DIM, ColorBlindMode.NONE, removeGammaCLut)

    for (i in uniform1.lut.indices) {
      val color1 = uniform1.lut[i]
      val color2 = uniform2.lut[i]

      assertTrue(
        "Red comparison between ${getColorString(color1)} vs ${getColorString(color2)}",
        Math.abs(r(color1) - r(color2)) <= threshhold,
      )
      assertTrue(
        "Green comparison between ${getColorString(color1)} vs ${getColorString(color2)}",
        Math.abs(g(color1) - g(color2)) <= threshhold,
      )
      assertTrue(
        "Blue comparison between ${getColorString(color1)} vs ${getColorString(color2)}",
        Math.abs(b(color1) - b(color2)) <= threshhold,
      )
    }
  }

  /**
   * Interpolate the 16 x 16 x 16 Color Lookup Table and look for the next red.
   *
   * It should be within the range of 1. It should almost always return 1 higher for red.
   */
  fun testNextRed() {
    for (index in 0..255) {
      val next = uniform.nextRed(index)
      assertTrue("@$index, $next", next - index <= 1)
    }
  }

  /** As our 3D table is layed out in uniform, we expect each next index to increase by 16. */
  fun testNextGreen() {
    for (index in 0..255) {
      val next = uniform.nextGreen(index)
      assertTrue("@$index, $next", next - index == DIM || next - index == 0)
    }
  }

  fun testNextBlue() {
    for (index in 0..255) {
      val next = uniform.nextBlue(index)
      assertTrue("@$index, $next", next - index == DIM * DIM || next - index == 0)
    }
  }

  fun testInterpolateAllColor() {
    for (color in 0 until 0xFFFFFF) {
      val out = uniform.interpolate(color)

      assertTrue("Red input : $color, output : $out", Math.abs(r(color) - r(out)) <= 1)
      assertTrue("Green input : $color, output : $out", Math.abs(g(color) - g(out)) <= 1)
      assertTrue("Blue input : $color, output : $out", Math.abs(b(color) - b(out)) <= 1)
    }
  }

  fun testGamma() {
    for (color in 0 until 0xFFFFFF) {
      val rgb = RGB(r(color).toDouble(), g(color).toDouble(), b(color).toDouble())
      val gammaRemoved = removeGamma(rgb, removeGammaCLut)
      val back = applyGamma(gammaRemoved)

      assertTrue(abs(rgb.r - back.r) <= 0.001)
      assertTrue(abs(rgb.g - back.g) <= 0.001)
      assertTrue(abs(rgb.b - back.b) <= 0.001)
    }
  }
}

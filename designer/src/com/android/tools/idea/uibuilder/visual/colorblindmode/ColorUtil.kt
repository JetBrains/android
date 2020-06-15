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

import kotlin.math.pow
import java.util.function.Function

/**
 * All the numbers, math and explanation on how things work is documented in:
 * go/cbm_simulator
 */

/**
 * Build color lookup table.
 */
fun buildColorLut(dim: Int, mode: ColorBlindMode, removeGammaCLut: DoubleArray): ColorLut {
  val ret = IntArray(dim * dim * dim)

  val lms2lms = getMat3D(mode)

  for (i in ret.indices) {
    val ri = i % dim
    val gi = (i / dim) % dim
    val bi = i / (dim * dim)

    // For non-uniform ColorLut, this value would change basically.
    val red = ri * 255 / (dim - 1)
    val green = gi * 255 / (dim - 1)
    val blue = bi * 255 / (dim - 1)

    val converted = convertSingleColor(combine(red, green, blue), lms2lms, removeGammaCLut)
    ret[i] = converted
  }

  return ColorLut(ret, dim)
}

/**
 * Get the color blind simulating matrix (that transforms from lms to lms).
 */
private fun getMat3D(mode: ColorBlindMode): Mat3D {
  return when(mode) {
    ColorBlindMode.NONE -> IDENTITY_MATRIX
    ColorBlindMode.PROTANOPES -> buildLms2Lmsp()
    ColorBlindMode.PROTANOMALY -> buildLms2Lmsp(MUTATED_FACTOR)
    ColorBlindMode.DEUTERANOPES -> buildLms2Lmsd()
    ColorBlindMode.DEUTERANOMALY -> buildLms2Lmsd(MUTATED_FACTOR)
    ColorBlindMode.TRITANOPES -> buildLms2Lmst()
  }
}

/**
 * Convert single px color to color blind simulation (based on the param [lms2lms] matrix)
 *
 * @param color - input color in RRGGBB where each ch [0, 255]
 * @param lms2lms - conversion matrix. One of [buildLms2Lmsd], [buildLms2Lmsp] or [buildLms2Lmst]
 * @param removeGammaCLut - gamma removal color lookup table from [buildGammaCLut].
 */
fun convertSingleColor(color: Int, lms2lms: Mat3D, removeGammaCLut: DoubleArray): Int {
  var color = RGB(r(color).toDouble(), g(color).toDouble(), b(color).toDouble())

  // 0 - 1 space conversion
  color = removeGamma(color, removeGammaCLut)
  color = RGB_TO_LMS * color // lms

  // Apply colour blind mode. (lms to lms)
  color = lms2lms * color // lms

  color = LMS_TO_RGB * color // rgb
  return applyGamma(color).color()
}

/**
 * Build color lookup table for the gamma removal or apply.
 */
fun buildGammaCLut(function: Function<Double, Double>): DoubleArray {
  val toReturn = DoubleArray(256)
  for (i in 0 .. 255) {
    toReturn[i] = function.apply(i.toDouble())
  }
  return toReturn
}

/**
 * Remove gamma from RGB space
 * @param color - RGB namespace color
 * @param gamma - [0,1]
 */
fun removeGamma(color: RGB, removeGammaCLut: DoubleArray): RGB {
  val r = removeGammaCLut[color.r.toInt()]
  val g = removeGammaCLut[color.g.toInt()]
  val b = removeGammaCLut[color.b.toInt()]
  return RGB(r, g, b)
}

/**
 * Apply gamma back to RGB space. (i.e. gamma correct
 * @param color - RGB namespace color (but in [0,1] space)
 * @param gamma - [0,1]
 */
fun applyGamma(color: RGB, gamma: Double = GAMMA): RGB {
  val rg = 255.0 * color.r.pow(1.0 / gamma)
  val gg = 255.0 * color.g.pow(1.0 / gamma)
  val bg = 255.0 * color.b.pow(1.0 / gamma)
  return RGB(rg, gg, bg)
}


/**
 * Creates a uniform [ColorLut]. Index represents 3D Map, with R,G,B represented
 * through its index.
 *
 * E.g.) with [dim] == 4
 * 0 - r: 0, g: 0, b: 0
 * 1 - r: 85, g: 0, b: 0
 * 2 - r: 170, g: 0, b: 0
 * 3 - r: 255, g: 0, b: 0
 *
 * 4 - r: 0, g: 85, b: 0
 * 5 - r: 85, g: 85, b: 0
 * 6 - r: 170, g: 85, b: 0
 * 7 - r: 255, g: 85, b: 0
 *
 * 8 - r: 0, g: 170, b: 0
 *
 * ...
 * 63 - r: 255, g: 255, b: 255
 *
 * Red grows the fastest rate (let's call this velocity 1). It repeats [dim]^2
 * Green grows at 1 * [dim]. Thus it repeats [dim]
 * Blue grows at 1 * [dim]^2. Thus it never repeats
 *
 * Think of it as a cycle.
 */
fun buildUniformColorLut(dim: Int): ColorLut {
  val ret = IntArray(dim * dim * dim)
  for (i in ret.indices) {
    // Calculate R,G,B that uniformly grows.
    val ri = i % dim
    val gi = (i / dim) % dim
    val bi = i / (dim * dim)

    // For non-uniform ColorLut, this value would change basically.
    val red = ri * 255 / (dim - 1)
    val green = gi * 255 / (dim - 1)
    val blue = bi * 255 / (dim - 1)

    val color = red shl 16 or (green shl 8) or blue
    ret[i] = color
  }
  return ColorLut(ret, dim)
}
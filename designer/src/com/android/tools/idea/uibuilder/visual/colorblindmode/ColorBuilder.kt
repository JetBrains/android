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


/**
 * All the numbers, math and explanation on how things work is documented in:
 * go/cbm_simulator
 */

private const val GAMMA = 2.2

/**
 * Build color lookup table.
 */
internal fun buildColorLut(dim: Int, mode: ColorBlindMode): ColorLut {
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

    val converted = convertSingleColor(combine(red, green, blue), lms2lms)
    ret[i] = converted
  }

  return ColorLut(ret, dim)
}

internal fun getMat3D(mode: ColorBlindMode): Mat3D {
  return when(mode) {
    ColorBlindMode.PROTANOPES -> buildLms2Lmsp()
    ColorBlindMode.PROTANOMALY -> buildLms2Lmsp(MUTATED_FACTOR)
    ColorBlindMode.DEUTERANOPES -> buildLms2Lmsd()
    ColorBlindMode.DEUTERANOMALY -> buildLms2Lmsd(MUTATED_FACTOR)
    //ColorBlindMode.TRITANOPES -> TODO: Get the math right.
  }
}

private val RGB_TO_LMS: Mat3D = Mat3D(
  17.8824, 43.5161, 4.1194,
  3.4557, 27.1554, 3.8671,
  0.03, 0.1843, 1.4671)

private val LMS_TO_RGB: Mat3D = Mat3D(
  0.0809, -0.1305, 0.1167,
  -0.0102, 0.054, -0.1136,
  -0.0004, -0.0041, 0.6935)

internal fun buildLms2Lmsp(factor: Double = 0.0): Mat3D {
  return Mat3D(
    0.0 + factor, 2.0234 * (1.0 - factor), -2.5258 * (1.0 - factor),
    0.0, 1.0, 0.0,
    0.0, 0.0, 1.0)
}

internal fun buildLms2Lmsd(factor: Double = 0.0): Mat3D {
  return Mat3D(
    1.0, 0.0, 0.0,
    0.494207 * (1.0 - factor), 0.0 + factor, 1.24827 * (1.0 - factor),
    0.0, 0.0, 1.0)
}

internal fun buildLms2Lmst(factor: Double = 0.0): Mat3D {
  return Mat3D(
    1.0, 0.0, 0.0,
    0.0, 1.0, 0.0,
    -0.8674 * (1.0 - factor), 1.8672 * (1.0 - factor), 0.0 + factor)
}

internal fun convertSingleColor(color: Int, lms2lms: Mat3D): Int {
  var color = RGB(r(color).toDouble(), g(color).toDouble(), b(color).toDouble())

  // 0 - 1 space conversion
  color = applyGamma(color, GAMMA)
  color = RGB_TO_LMS * color // lms

  // Apply protanopes
  color = lms2lms * color // lms

  color = LMS_TO_RGB * color // rgb
  return gammaCorrect(color, GAMMA).color()
}

/**
 * Apply gamma to RGB space
 * @param color - RGB namespace color
 * @param gamma - [0,1]
 */
internal fun applyGamma(color: RGB, gamma: Double): RGB {

  val r = Math.pow(color.r / 255.0, gamma)
  val g = Math.pow(color.g / 255.0, gamma)
  val b = Math.pow(color.b / 255.0, gamma)

  return RGB(r, g, b)
}

/**
 * Apply gamma to RGB space
 * @param color - RGB namespace color
 * @param gamma - [0,1]
 */
internal fun gammaCorrect(color: RGB, gamma: Double): RGB {

  var rg = 255.0 * Math.pow(color.r, 1.0/gamma)
  var gg = 255.0 * Math.pow(color.g, 1.0/gamma)
  var bg = 255.0 * Math.pow(color.b, 1.0/gamma)

  return RGB(rg, gg, bg)
}


// TODO: NOT used for now. Needed?
fun reduceColorDomain4Protanopes(color: RGB): RGB {
  val x = 0.992052
  val y = 0.003974

  val r = (x * color.r + y)
  val g = (x * color.g + y)
  val b = (x * color.b + y)

  return RGB(r, g, b)
}

fun reduceColorDomain4Deuteranopes(color: RGB): RGB {
  val x = 0.957237
  val y = 0.0213814

  val r = (x * color.r + y)
  val g = (x * color.g + y)
  val b = (x * color.b + y)

  return RGB(r, g, b)
}

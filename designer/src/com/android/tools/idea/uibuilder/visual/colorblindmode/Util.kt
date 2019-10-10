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


import kotlin.math.roundToInt

/**
 * All the numbers, math and explaination on how things work is documented in:
 * go/cbm_simulator
 */

internal fun interpolate(x: Double, x0: Double, x1: Double, y0: Int, y1: Int): Int {
  return ((y0 * (x1 - x) + y1 * (x - x0)) / (x1 - x0)).toInt()
}

internal fun r(c: Int): Int {
  return c shr 16 and 0xFF
}

internal fun g(c: Int): Int {
  return c shr 8 and 0xFF
}

internal fun b(c: Int): Int {
  return c and 0xFF
}

fun printColor(color: Int): String {
  return """${r(color)},${g(color)},${b(color)}"""
}

internal fun combine(r: Int, g: Int, b: Int): Int {
  return r shl 16 or (g shl 8) or b
}

//data class Double3(val one: Double, val two: Double, val three: Double)
class LAB(val l: Double, val a: Double, val b: Double)
class XYZ(val x: Double, val y: Double, val z: Double)

class RGB(val r: Double, val g: Double, val b: Double) {
  fun color(): Int {
    val r: Int = if (r.isNaN()) 0 else r.roundToInt()
    val g: Int = if (g.isNaN()) 0 else g.roundToInt()
    val b: Int = if (b.isNaN()) 0 else b.roundToInt()

    return r shl 16 or (g shl 8) or b
  }

  override fun toString(): String {
    return "r: $r, g: $g, b: $b"
  }
}

class Mat3D(val aa: Double, val ab: Double, val ac: Double,
            val ba: Double, val bb: Double, val bc: Double,
            val ca: Double, val cb: Double, val cc: Double) {

  operator fun times(input: RGB): RGB {
    return RGB(
      aa * input.r + ab * input.g + ac * input.b,
      ba * input.r + bb * input.g + bc * input.b,
      ca * input.r + cb * input.g + cc * input.b)
  }
}

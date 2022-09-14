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

fun interpolate(x: Double, x0: Double, x1: Double, y0: Int, y1: Int): Int {
  return ((y0 * (x1 - x) + y1 * (x - x0)) / (x1 - x0)).toInt()
}

fun a(c: Int): Int {
  return c shr 24 and 0xFF
}

fun r(c: Int): Int {
  return c shr 16 and 0xFF
}

fun g(c: Int): Int {
  return c shr 8 and 0xFF
}

fun b(c: Int): Int {
  return c and 0xFF
}

private fun twoDigit(value: Int): String {
  return String.format("%02X", value)
}

fun getColorString(color: Int): String {
  return """${twoDigit(r(color))}${twoDigit(g(color))}${twoDigit(b(color))}"""
}

fun combine(r: Int, g: Int, b: Int): Int {
  return r shl 16 or (g shl 8) or b
}

fun combine(r: Double, g: Double, b: Double): Int {
  return r.toInt() shl 16 or (g.toInt() shl 8) or b.toInt()
}

class LMS(val l: Double, val m: Double, val s: Double): Double3D(l, m, s)
class LAB(val l: Double, val a: Double, val b: Double): Double3D(l, a, b)
class XYZ(val x: Double, val y: Double, private val z: Double): Double3D(x, y, z)
class RGB(val r: Double, val g: Double, val b: Double): Double3D(r, g, b)

open class Double3D(
  val first: Double,
  val second: Double,
  val third: Double) {
  fun color(): Int {
    val r: Int = if (first.isNaN()) 0 else first.roundToInt()
    val g: Int = if (second.isNaN()) 0 else second.roundToInt()
    val b: Int = if (third.isNaN()) 0 else third.roundToInt()

    return r shl 16 or (g shl 8) or b
  }

  override fun toString(): String {
    return "$first, $second, $third"
  }

  override fun equals(other: Any?): Boolean {
    return toString() == other.toString()
  }

  /**
   * Col * Row  or Row * Col scenario
   */
  operator fun times(other: Double3D): Double {
    return first * other.first + second * other.second + third * other.third
  }
}

/**
 * Represents matrix.
 *
 * [aa, ab, ac]
 * [ba, bb, bc]
 * [ca, cb, cc]
 */
class Mat3D(private val aa: Double, private val ab: Double, private val ac: Double,
            private val ba: Double, private val bb: Double, private val bc: Double,
            private val ca: Double, private val cb: Double, private val cc: Double) {

  /**
   * in order:
   * [row1.first, row1.second, row1.third]
   * [row2.first, row2.second, row2.third]
   * [row3.first, row3.second, row3.third]
   */
  internal constructor(row1: Double3D, row2: Double3D, row3: Double3D) : this (
    aa = row1.first, ab = row1.second, ac = row1.third,
    ba = row2.first, bb = row2.second, bc = row2.third,
    ca = row3.first, cb = row3.second, cc = row3.third
  )

  companion object {
    private const val COMPARE_THRESHOLD = 0.001
  }

  // Not very optimal so don't use it too often.
  // More for the convenience.
  private val row1: Double3D get() = Double3D(aa, ab, ac)
  private val row2: Double3D get() = Double3D(ba, bb, bc)
  private val row3: Double3D get() = Double3D(ca, cb, cc)
  private val col1: Double3D get() = Double3D(aa, ba, ca)
  private val col2: Double3D get() = Double3D(ab, bb, cb)
  private val col3: Double3D get() = Double3D(ac, bc, cc)

  operator fun times(input: RGB): RGB {
    return RGB(
      row1 * input,
      row2 * input,
      row3 * input)
  }

  /**
   * This is not optimized so don't rely on this heavily.
   * Only used for testing as of right now.
   */
  operator fun times(other: Mat3D): Mat3D {
    return Mat3D(
      row1 * other.col1, row1 * other.col2, row1 * other.col3,
      row2 * other.col1, row2 * other.col2, row2 * other.col3,
      row3 * other.col1, row3 * other.col2, row3 * other.col3)
  }

  override fun toString(): String {
    return """
            $aa, $ab, $ac
            $ba, $bb, $bc
            $ca, $cb, $cc
        """.trimIndent()
  }

  fun toWolframAlphaString(): String {
    return """
            {{$aa, $ab, $ac}, {$ba, $bb, $bc}, {$ca, $cb, $cc}}
        """.trimIndent()
  }
  override fun equals(other: Any?): Boolean {
    return toString() == other.toString()
  }

  fun close(other: Mat3D): Boolean {
    return close(aa, other.aa) && close(ab, other.ab)  && close(ac, other.ac) &&
           close(ba, other.ba) && close(bb, other.bb)  && close(bc, other.bc) &&
           close(ca, other.ca) && close(cb, other.cb)  && close(cc, other.cc)
  }

  private fun close(one: Double, two: Double, threshold: Double = COMPARE_THRESHOLD): Boolean {
    return Math.abs(one - two) < threshold
  }
}

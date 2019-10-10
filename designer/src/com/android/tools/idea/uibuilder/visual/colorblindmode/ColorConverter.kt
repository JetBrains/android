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

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.lang.StringBuilder

/**
 * All the numbers, math and explanation on how things work is documented in:
 * go/cbm_simulator
 */

private const val DIM = 16
private const val DEBUG = false
internal const val MUTATED_FACTOR = 0.25

enum class ColorBlindMode(val displayName: String) {
  PROTANOPES("Protanopes"), // Missing L
  PROTANOMALY("Protanomaly"), // Mutated L
  DEUTERANOPES("Deuteranopes"), // Missing M
  DEUTERANOMALY("Deuteranomaly"), // Mutated M
  // TRITANOPES("Tritanopes"), // Missing S TODO: MAP NOT COMPLETE
  // TRITANOMALY, // Mutated S TODO: MAP NOT COMPLETE
}

fun buildClut(mode: ColorBlindMode): ColorLut {
  return buildColorLut(DIM, mode)
}

/**
 * Pre condition : BufferedImage either [BufferedImage.TYPE_3BYTE_BGR] or [BufferedImage.TYPE_INT_ARGB]
 */
fun convert(startImage: BufferedImage, postImage: BufferedImage, mode: ColorBlindMode, lut: ColorLut? = null) {
  ColorConverterLogger.start("CLUT build ${mode.name}")
  val lut = lut ?: buildColorLut(DIM, mode)

  ColorConverterLogger.end("CLUT build ${mode.name}")

  ColorConverterLogger.start("Apply ${mode.name}")
  val inData = (startImage.raster.dataBuffer as DataBufferInt).data
  val outData = (postImage.raster.dataBuffer as DataBufferInt).data

  for (i in inData.indices) {
    val inputColor = inData[i]
    outData[i] = lut.interpolate(inputColor)
  }
  ColorConverterLogger.end("Apply ${mode.name}")

  if (DEBUG) {
    println(ColorConverterLogger.dumpAndClearLog())
  }
}

fun convert(color: Int, mode: ColorBlindMode): Int {
  return convertSingleColor(color, getMat3D(mode))
}

class ColorLut(val lut: IntArray, val dim: Int) {

  private val dimdim = dim * dim

  /**
   * Given current index, it looks for the next appropriate RED index.
   * If [index] contains the highest RED, it returns [index]
   */
  fun nextRed(index: Int): Int {
    if ((index + 1) % dim == 0) {
      // No next number. It's at the edge
      return index
    }
    return index + 1
  }

  /**
   * Given the current index, it looks for the next GREEN index.
   * * If [index] contains the highest GREEN, it returns [index]
   */
  fun nextGreen(index: Int): Int {
    val modded = (index % (dimdim)) / dim
    if (modded == (dim - 1)) {
      return index
    }
    return index + dim
  }

  /**
   * Given the current index, it looks for the next BLUE index.
   * * * If [index] contains the highest BLUE, it returns [index]
   */
  fun nextBlue(index: Int): Int {
    val lastPanel = lut.size - dimdim
    if (index >= lastPanel) {
      return index
    }
    return index + dimdim
  }

  /**
   * Interpolates within the CLUT and returns the appropriate color.
   * 1) It looks up the appropriate (lower-bound) index in [ColorLut].
   * 2) It looks up the next (upper-bound) index in [ColorLut].
   * 3) Based on its ratio, it interpolates between 1) and 2)
   * 4) Returns the interpolated colour.
   */
  fun interpolate(inputColor: Int): Int {
    val red = inputColor shr 16 and 0xFF
    val green = inputColor shr 8 and 0xFF
    val blue = inputColor and 0xFF
    val alpha = inputColor shr 24 and 0xFF

    // 2) x - INDEX, y - COLOR. Interpolate from x to y.
    val rx: Double = red * (dim - 1) / 255.0
    val rx0: Int = Math.floor(rx).toInt()

    val gx: Double = green * (dim - 1) / 255.0
    val gx0: Int = Math.floor(gx).toInt()

    val bx: Double = blue * (dim - 1) / 255.0
    val bx0: Int = Math.floor(bx).toInt()

    val x0 = rx0 + gx0 * dim + bx0 * dimdim

    val rx1 = Math.ceil(rx).toInt()
    val gx1 = Math.ceil(gx).toInt()
    val bx1 = Math.ceil(bx).toInt()

    val ry0 = r(lut[x0])
    val ry1 = r(lut[nextRed(x0)])

    val gy0 = g(lut[x0])
    val gy1 = g(lut[nextGreen(x0)])

    val by0 = b(lut[x0])
    val by1 = b(lut[nextBlue(x0)])

    val ry = if (rx0 == rx1) ry0 else
      interpolate(rx, rx0.toDouble(), rx1.toDouble(), ry0, ry1)
    val gy = if (gx0 == gx1) gy0 else
      interpolate(gx, gx0.toDouble(), gx1.toDouble(), gy0, gy1)
    val by = if (bx0 == bx1) by0 else
      interpolate(bx, bx0.toDouble(), bx1.toDouble(), by0, by1)

    return alpha shl 24 or (ry shl 16 or (gy shl 8) or by)
  }

  override fun toString(): String {
    val builder = StringBuilder()
    for (i in lut.indices) {
      val color = lut[i]
      if ((i+1) % dim == 0) {
        builder.append("${r(color)}, ${g(color)}, ${b(color)} \n")
      } else {
        builder.append("${r(color)}, ${g(color)}, ${b(color)} \t\t")
      }

      if( (i + 1) % (dim * dim) == 0) {
        builder.append("=========================\n")
      }
    }
    return builder.toString()
  }
}

internal object ColorConverterLogger {

  private val log = HashMap<String, Pair<Long, Long>>()

  internal fun start(name: String) {
    if (!DEBUG) {
      return
    }
    log[name] = Pair(System.currentTimeMillis(), 0L)
  }

  internal fun end(name: String) {
    if (!DEBUG) {
      return
    }

    if (!log.containsKey(name)) {
      return
      //throw RuntimeException("Start not called : $name")
    }

    var out = log[name]
    log[name] = Pair(out!!.first, System.currentTimeMillis())
  }

  internal fun dumpAndClearLog(): String {
    val builder = StringBuilder()
    builder.append("Logging results : \n")
    for (entry in log) {
      val elapsed = entry.value.second - entry.value.first
      builder.append(" - ${entry.key} : $elapsed ms\n")
    }
    log.clear()
    return builder.toString()
  }
}

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

import com.intellij.openapi.Disposable
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.lang.StringBuilder
import java.util.function.Function
import kotlin.math.pow

/**
 * All the numbers, math and explanation on how things work is documented in:
 * go/cbm_simulator
 */

/**
 * Color blind simulator.
 */
class ColorConverter(val mode: ColorBlindMode) : Disposable {

  companion object {
    private var removeGammaCLut: DoubleArray? = null
  }
  private var cbmCLut: ColorLut? = null

  /**
   * Ensure that all necessary color lookup table is built.
   * Encouraged to call it every time before calling [convert]
   */
  fun init() {
    if (removeGammaCLut == null) {
      ColorConverterLogger.start("Build CLut for ${mode.name}")
      removeGammaCLut = buildGammaCLut(Function { (it / 255.0).pow(GAMMA) })
      ColorConverterLogger.end("Build CLut for ${mode.name}")
    }

    if (cbmCLut == null) {
      ColorConverterLogger.start("Build gamma CLut for ${mode.name}")
      cbmCLut = buildColorLut(DIM, mode, removeGammaCLut!!)
      ColorConverterLogger.end("Build gamma CLut for ${mode.name}")
    }
  }

  /**
   * Pre condition : BufferedImage must be [BufferedImage.TYPE_INT_ARGB]
   */
  fun convert(startImage: BufferedImage, postImage: BufferedImage) {
    if (cbmCLut == null || removeGammaCLut == null) {
      throw RuntimeException("Make sure the converter.init is called.")
    }

    ColorConverterLogger.start("Apply ${mode.name}")
    val inData = (startImage.raster.dataBuffer as DataBufferInt).data
    val outData = (postImage.raster.dataBuffer as DataBufferInt).data

    for (i in inData.indices) {
      outData[i] = convert(inData[i])
    }
    ColorConverterLogger.end("Apply ${mode.name}")

    if (DEBUG) {
      println(ColorConverterLogger.dumpAndClearLog())
    }
  }

  /**
   * Converts a single color. Color input must be [BufferedImage.TYPE_INT_ARGB].
   */
  fun convert(color: Int): Int {
    return 0xff shl 24 or cbmCLut!!.interpolate(prepare(color))
  }

  /**
   * Slightly reduce the colour domain as per the paper
   * "Digital Vido Colourmaps for Checking the Legibility of Displays by Dichromats"
   * Also strip alpha value so we can continue working with RGB.
   * We assume white background (since layoutlib works with single bitmap).
   */
  private fun prepare(color: Int): Int {
    val a = a(color).toDouble() / 255.0
    var r = r(color).toDouble() * a
    var g = g(color).toDouble() * a
    var b = b(color).toDouble() * a

    return when (mode) {
      ColorBlindMode.PROTANOMALY,
      ColorBlindMode.PROTANOPES -> {
        r = 0.992052 * r + 0.003974
        g = 0.992052 * g + 0.003974
        b = 0.992052 * b + 0.003974
        combine(r, g, b)
      }
      ColorBlindMode.DEUTERANOMALY,
      ColorBlindMode.DEUTERANOPES-> {
        r = 0.957237 * r + 0.0213814
        g = 0.957237 * g + 0.0213814
        b = 0.957237 * b + 0.0213814
        combine(r, g, b)
      }
      else -> combine(r, g, b)
    }
  }

  override fun dispose() {
    removeGammaCLut = null
    cbmCLut = null
  }
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

    return ry shl 16 or (gy shl 8) or by
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

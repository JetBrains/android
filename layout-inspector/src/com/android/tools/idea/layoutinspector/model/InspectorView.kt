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
package com.android.tools.idea.layoutinspector.model

import com.intellij.util.ui.UIUtil
import java.awt.Image
import java.awt.Point
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel

/**
 * A view as seen in a Skia image.
 *
 * @param id the id in the Skia image which is also the id found by View.getUniqueDrawingId
 * @param type the qualified class name of the view
 * @param x the left edge of the view from the device left edge
 * @param y the top edge of the view from the device top edge
 *
 * Currently primarily created through JNI by the skia parser.
 * TODO: Rename this class to e.g. SkiaViewNode
 */
class InspectorView(
  val id: String,
  val type: String,
  var x: Int,
  var y: Int,
  var width: Int,
  var height: Int
) {
  var image: Image? = null
  var imageGenerationTime: Long? = null

  /**
   * Map of View IDs to views.
   */
  val children: MutableMap<String, InspectorView> = mutableMapOf()

  @Suppress("unused") // invoked via reflection
  fun setData(data: IntArray) {
    val buffer = DataBufferInt(data, width * height)
    val model = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, intArrayOf(0xff0000, 0xff00, 0xff, -0x1000000))
    val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
    val tmpimage = BufferedImage(
      DirectColorModel(32, 0xff0000, 0xff00, 0xff, -0x1000000), raster, false, null)
    UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB).let {
      image = it
      imageGenerationTime = System.currentTimeMillis()
      val g = it.createGraphics()
      g.drawImage(tmpimage, 0, 0, null)
      g.dispose()
    }
  }

  @Suppress("unused") // invoked via reflection
  fun addChild(child: InspectorView) {
    children[child.id] = child
  }

  fun flatten(): Collection<InspectorView> {
    return children.values.flatMap { it.flatten() }.plus(this)
  }
}
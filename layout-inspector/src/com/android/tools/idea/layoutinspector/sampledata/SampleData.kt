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
package com.android.tools.idea.layoutinspector.sampledata

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.intellij.util.ui.UIUtil
import java.awt.Point
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.lang.invoke.MethodHandles
import java.util.zip.ZipInputStream

/**
 * These are static snapshots of [InspectorModel]s derived from dumped skps. They are probably only useful during initial development
 * of the dynamic layout inspector, before the complete device communication pipeline is finished.
 *
 * TODO: remove this file and the .skp.dump files.
 */
val chromeSampleData = read("sampledata/2018-12-12_001545_com.android.chrome.skp.dump")
val videosSampleData = read("sampledata/2018-12-20_154905_com.google.android.videos.skp.dump")
val youtubeSampleData = read("sampledata/2018-12-27_161641_com.google.android.youtube.skp.dump")

private fun read(filename: String) : InspectorModel {
  val stream = MethodHandles.lookup().lookupClass().classLoader.getResourceAsStream(filename)
  ZipInputStream(BufferedInputStream(stream)).use { zos ->
    zos.nextEntry
    DataInputStream(BufferedInputStream(zos)).use { return InspectorModel(readNode(it)) }
  }
}

private fun readNode(dis: DataInputStream) : InspectorView {
  val type = dis.readUTF()
  val left = dis.readInt()
  val width = dis.readInt()
  val top = dis.readInt()
  val height = dis.readInt()
  val dataLength = dis.readInt()
  val data = if (dataLength > 0) IntArray(dataLength) else null
  if (data != null) {
    for (i in 1 .. dataLength) {
      data[i - 1] = dis.readInt()
    }
  }
  val result = InspectorView("todo", type, left, top, width, height, listOf())
  data?.let {
    val buffer = DataBufferInt(data, width * height)
    val model = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, intArrayOf(0xff0000, 0xff00, 0xff, -0x1000000))
    val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
    val tmpimage = BufferedImage(
      DirectColorModel(32, 0xff0000, 0xff00, 0xff, -0x1000000), raster, false, null)
    val image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().drawImage(tmpimage, 0, 0, null)
    result.image = image
  }
  for (i in 0 until dis.readInt()) {
    result.children.add(readNode(dis))
  }
  return result
}

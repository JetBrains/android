/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.io.readImage
import com.android.test.testutils.TestUtils
import com.android.tools.layoutinspector.BITMAP_HEADER_SIZE
import com.android.tools.layoutinspector.BitmapType
import com.android.tools.layoutinspector.toBytes
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

/**
 * Process a target image png file and create the data that normally would have been generated on a
 * target device.
 */
class Screenshot(filename: String, bitmapType: BitmapType) {
  val image: BufferedImage
  val bytes: ByteArray

  init {
    val origImage = TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/$filename").readImage()
    val buffer = ByteBuffer.allocate(origImage.width * origImage.height * bitmapType.pixelSize)
    image = bitmapType.createImage(buffer, origImage.width, origImage.height)
    val graphics = image.graphics
    graphics.drawImage(origImage, 0, 0, null)
    val imageBytes =
      ArrayList<Byte>(image.width * image.height * bitmapType.pixelSize + BITMAP_HEADER_SIZE)
    if (bitmapType == BitmapType.RGB_565) {
      val dataElements =
        image.raster.getDataElements(
          0,
          0,
          image.width,
          image.height,
          ShortArray(image.width * image.height)
        ) as ShortArray
      dataElements.flatMapTo(imageBytes) {
        listOf((it.toInt() and 0xFF).toByte(), (it.toInt() ushr 8).toByte())
      }
    } else {
      val dataElements =
        image.raster.getDataElements(
          0,
          0,
          image.width,
          image.height,
          IntArray(image.width * image.height)
        ) as IntArray
      dataElements.flatMapTo(imageBytes) { it.toBytes().asIterable() }
    }
    bytes =
      (image.width.toBytes().asList() +
          image.height.toBytes().asList() +
          bitmapType.byteVal +
          imageBytes)
        .toByteArray()
        .compress()
  }
}

fun ByteArray.compress(): ByteArray {
  val deflater = Deflater(Deflater.BEST_SPEED)
  deflater.setInput(this)
  deflater.finish()
  val buffer = ByteArray(1024 * 100)
  val baos = ByteArrayOutputStream()
  while (!deflater.finished()) {
    val count = deflater.deflate(buffer)
    if (count <= 0) break
    baos.write(buffer, 0, count)
  }
  baos.flush()
  return baos.toByteArray()
}

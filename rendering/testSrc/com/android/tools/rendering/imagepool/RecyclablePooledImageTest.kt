/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering.imagepool

import com.android.ide.common.rendering.api.RecyclableImage
import com.android.tools.rendering.imagepool.ImagePoolImageDisposer.disposeImage
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecyclablePooledImageTest {
  @Test
  fun testWrappingAndDimensions() {
    val parent = BufferedImage(100, 150, BufferedImage.TYPE_INT_ARGB_PRE)
    val g = parent.createGraphics()
    try {
      g.color = Color.RED
      g.fillRect(0, 0, 100, 150)
    } finally {
      g.dispose()
    }

    val closed = AtomicBoolean(false)

    val recyclable: RecyclableImage =
      object : RecyclableImage {
        override fun getWidth() = 100

        override fun getHeight() = 150

        override fun getImage() = parent

        override fun close() = closed.set(true)
      }

    val image = RecyclablePooledImage(recyclable)

    // 1. Verify correct bounds
    assertEquals(100, image.width.toLong())
    assertEquals(150, image.height.toLong())
    assertTrue(image.isValid)

    // 2. Verify copying
    val copy = image.copy
    assertEquals(100, copy!!.width.toLong())
    assertEquals(150, copy.height.toLong())
    assertEquals(Color.RED.rgb.toLong(), copy.getRGB(50, 50).toLong())

    // 3. Dispose should trigger close on backing RecyclableImage
    disposeImage(image)
    assertFalse(image.isValid)
    assertTrue(closed.get())
  }
}

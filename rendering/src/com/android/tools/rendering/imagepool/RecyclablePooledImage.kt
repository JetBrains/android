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
import com.intellij.openapi.diagnostic.thisLogger
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** A generic, thread-safe wrapper that adapts any [RecyclableImage] to Studio's [ImagePool.Image] and [DisposableImage] contracts. */
class RecyclablePooledImage(@Volatile private var backingImage: RecyclableImage?) : ImagePool.Image, DisposableImage {
  private val isDisposed = AtomicBoolean(false)

  override fun getWidth() = backingImage?.width ?: 0

  override fun getHeight() = backingImage?.height ?: 0

  override fun drawImageTo(g: Graphics, dx1: Int, dy1: Int, dx2: Int, dy2: Int, sx1: Int, sy1: Int, sx2: Int, sy2: Int) {
    val recyclableImage = backingImage
    if (isDisposed.get() || recyclableImage == null) {
      thisLogger().warn("drawImageTo called on disposed RecyclablePooledImage")
      return
    }
    g.drawImage(recyclableImage.getImage(), dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null)
  }

  override fun paint(command: Consumer<Graphics2D?>) {
    val recyclableImage = backingImage
    if (isDisposed.get() || recyclableImage == null) {
      thisLogger().warn("paint called on disposed RecyclablePooledImage")
      return
    }
    val g = recyclableImage.getImage().createGraphics()
    try {
      command.accept(g)
    } finally {
      g.dispose()
    }
  }

  override fun getCopy(x: Int, y: Int, w: Int, h: Int): BufferedImage? {
    val recyclableImage = backingImage
    if (isDisposed.get() || recyclableImage == null) {
      thisLogger().warn("getCopy called on disposed RecyclablePooledImage")
      return null
    }
    val source = recyclableImage.getImage()
    val type = if (source.type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_ARGB_PRE else source.type
    val newImage = BufferedImage(w, h, type)
    val g = newImage.createGraphics()
    try {
      g.drawImage(source, 0, 0, w, h, x, y, x + w, y + h, null)
    } finally {
      g.dispose()
    }
    return newImage
  }

  override fun dispose() {
    if (isDisposed.compareAndSet(false, true)) {
      val image = backingImage
      backingImage = null
      image?.close()
    }
  }

  override fun isValid(): Boolean {
    return !isDisposed.get() && backingImage != null
  }
}

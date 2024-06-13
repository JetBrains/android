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
package com.android.tools.rendering.imagepool

import com.android.tools.rendering.imagepool.ImagePoolImageDisposer.runWithDisposeLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer

private class TestDisposableImage : ImagePool.Image, DisposableImage {
  private var isDisposed = false

  override fun dispose() {
    isDisposed = true
  }

  override fun getWidth(): Int = 0

  override fun getHeight(): Int = 0

  override fun drawImageTo(
    g: Graphics,
    dx1: Int,
    dy1: Int,
    dx2: Int,
    dy2: Int,
    sx1: Int,
    sy1: Int,
    sx2: Int,
    sy2: Int,
  ) {}

  override fun paint(command: Consumer<Graphics2D>?) {}

  override fun getCopy(gc: GraphicsConfiguration?, x: Int, y: Int, w: Int, h: Int): BufferedImage? =
    null

  override fun isValid(): Boolean = !isDisposed
}

class ImagePoolImageDisposerTest {
  @Test
  fun `verify image is disposed`() {
    val disposableImage = TestDisposableImage()
    assertTrue(disposableImage.isValid)
    ImagePoolImageDisposer.disposeImage(disposableImage)
    assertFalse(disposableImage.isValid)

    // Subsequent calls are ignored
    ImagePoolImageDisposer.disposeImage(disposableImage)
    ImagePoolImageDisposer.disposeImage(disposableImage)
  }

  @Test
  fun `verify image dispose lock`() {
    val disposableImage = TestDisposableImage()
    assertTrue(disposableImage.isValid)
    var executed = false
    disposableImage.runWithDisposeLock {
      disposableImage.runWithDisposeLock {
        executed = true
        assertTrue(disposableImage.isValid)
      }
    }
    assertTrue(executed)
    assertTrue(disposableImage.isValid)

    // Now try to dispose within the lock
    disposableImage.runWithDisposeLock {
      ImagePoolImageDisposer.disposeImage(disposableImage)
      assertTrue(disposableImage.isValid)
    }
    // Immediately after the lock it should be disposed
    assertFalse(disposableImage.isValid)
  }

  @Test
  fun `verify image disposed after lock`() {
    val threadStarted = CountDownLatch(1)
    val threadEnded = CountDownLatch(1)
    val latch = CountDownLatch(1)
    val disposableImage = TestDisposableImage()
    Thread {
        disposableImage.runWithDisposeLock {
          threadStarted.countDown()
          latch.await()
        }
        threadEnded.countDown()
      }
      .start()
    threadStarted.await()
    // The disposableImage is not locked and can not be disposed
    assertTrue(disposableImage.isValid)
    repeat(5) { ImagePoolImageDisposer.disposeImage(disposableImage) }
    // Image can not be disposed yet
    assertTrue(disposableImage.isValid)
    latch.countDown()
    threadEnded.await()
    // Image must be automatically disposed after this
    assertFalse(disposableImage.isValid)
  }
}

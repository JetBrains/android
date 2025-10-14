/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.rendering

import com.android.ide.common.rendering.api.IImageFactory
import java.awt.image.BufferedImage
import kotlin.random.Random
import org.junit.Test
import org.mockito.Mockito.mock

class ConstrainedImageFactoryTest {
  private val fakeImageFactory = FakeImageFactory()

  @Test
  fun testImageSize() {
    val maxSize = 128L * 128L
    var currentQuality = 1f

    val myFactory = ConstrainedImageFactory(maxSize, { currentQuality }, fakeImageFactory)

    // Quality = 1
    // wanted < max
    myFactory.getImage(64, 64)
    assert(fakeImageFactory.getImageCalls.size == 1)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 64)
    // wanted = max
    myFactory.getImage(128, 128)
    assert(fakeImageFactory.getImageCalls.size == 2)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 128)
    myFactory.getImage(64, 256)
    assert(fakeImageFactory.getImageCalls.size == 3)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 256)
    // wanted > max
    myFactory.getImage(256, 256)
    assert(fakeImageFactory.getImageCalls.size == 4)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 128)
    myFactory.getImage(128, 512)
    assert(fakeImageFactory.getImageCalls.size == 5)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 256)

    // Quality > 1 (same behavior as 1 expected)
    currentQuality = Random.nextFloat() + Random.nextInt(1, 1000)
    // wanted < max
    myFactory.getImage(64, 64)
    assert(fakeImageFactory.getImageCalls.size == 6)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 64)
    // wanted = max
    myFactory.getImage(128, 128)
    assert(fakeImageFactory.getImageCalls.size == 7)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 128)
    myFactory.getImage(64, 256)
    assert(fakeImageFactory.getImageCalls.size == 8)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 256)
    // wanted > max
    myFactory.getImage(256, 256)
    assert(fakeImageFactory.getImageCalls.size == 9)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 128)
    myFactory.getImage(128, 512)
    assert(fakeImageFactory.getImageCalls.size == 10)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 256)

    // Quality = 0.25
    currentQuality = 0.25f
    // wanted < max
    myFactory.getImage(64, 64)
    assert(fakeImageFactory.getImageCalls.size == 11)
    assert(fakeImageFactory.getImageCalls.last() == 32 to 32)
    // wanted = max
    myFactory.getImage(128, 128)
    assert(fakeImageFactory.getImageCalls.size == 12)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 64)
    myFactory.getImage(64, 256)
    assert(fakeImageFactory.getImageCalls.size == 13)
    assert(fakeImageFactory.getImageCalls.last() == 32 to 128)
    // wanted > max, and wanted * quality < max
    myFactory.getImage(256, 128)
    assert(fakeImageFactory.getImageCalls.size == 14)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 64)
    // wanted > max, and wanted * quality = max
    myFactory.getImage(256, 256)
    assert(fakeImageFactory.getImageCalls.size == 15)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 128)
    // wanted > max, and wanted * quality > max
    myFactory.getImage(512, 512)
    assert(fakeImageFactory.getImageCalls.size == 16)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 128)
    myFactory.getImage(256, 1024)
    assert(fakeImageFactory.getImageCalls.size == 17)
    assert(fakeImageFactory.getImageCalls.last() == 64 to 256)

    // Quality = 0
    currentQuality = 0f
    myFactory.getImage(1024, 1024)
    assert(fakeImageFactory.getImageCalls.size == 18)
    assert(fakeImageFactory.getImageCalls.last() == 1 to 1)

    // Huge image should not cause problems like overflow during calculation
    currentQuality = 1f
    myFactory.getImage(Int.MAX_VALUE, Int.MAX_VALUE)
    assert(fakeImageFactory.getImageCalls.size == 19)
    assert(fakeImageFactory.getImageCalls.last() == 128 to 128)

    // Empty image is unexpected, but should not cause errors like division by 0
    myFactory.getImage(0, 0)
    assert(fakeImageFactory.getImageCalls.size == 20)
    assert(fakeImageFactory.getImageCalls.last() == 1 to 1)

    // Negative dimensions are unexpected, but should be treated gracefully
    myFactory.getImage(2, -2)
    assert(fakeImageFactory.getImageCalls.size == 21)
    assert(fakeImageFactory.getImageCalls.last() == 2 to 1)

    myFactory.getImage(-2, 2)
    assert(fakeImageFactory.getImageCalls.size == 22)
    assert(fakeImageFactory.getImageCalls.last() == 1 to 2)

    myFactory.getImage(-2, -2)
    assert(fakeImageFactory.getImageCalls.size == 23)
    assert(fakeImageFactory.getImageCalls.last() == 1 to 1)
  }
}

private class FakeImageFactory() : IImageFactory {
  val getImageCalls = mutableListOf<Pair<Int, Int>>()
  val fakeImage: BufferedImage = mock()

  override fun getImage(width: Int, height: Int): BufferedImage {
    getImageCalls.add(width to height)
    return fakeImage
  }
}

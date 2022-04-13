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
package com.android.emulator

import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.UnsafeByteOperations
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for [ImageConverter].
 */
class ImageConverterTest {
  private val testImage = createTestImage()

  @Test
  fun testUnpackRgb888Slow() {
    val pixels = IntArray(IMAGE_SIZE)
    val t = runBenchmark { ImageConverter.unpackRgb888Slow(testImage, pixels) }
    println("unpackRgb888Slow: ${String.format("%.5f", t)} sec")
  }

  @Test
  fun testUnpackRgb888() {
    val pixels = IntArray(IMAGE_SIZE)
    val expectedPixels = IntArray(IMAGE_SIZE)
    ImageConverter.unpackRgb888Slow(testImage, expectedPixels)
    val t = runBenchmark { ImageConverter.unpackRgb888(testImage, pixels) }
    for (i in pixels.indices) {
      val expected = expectedPixels[i]
      val actual = pixels[i]
      if (expected != actual) {
        fail("The pixel at offset $i is ${String.format("0x%08X", actual)}, expected ${String.format("0x%08X", expected)}")
      }
    }
    println("unpackRgb888: ${String.format("%.5f", t)} sec")
  }

  @Test
  fun testUnpackRgb888ErrorHandling() {
    val bytes = ByteArray(30)
    assertThrows(IllegalArgumentException::class.java) {
      ImageConverter.unpackRgb888(UnsafeByteOperations.unsafeWrap(bytes, 3, bytes.size - 4), IntArray(9))
    }

    assertThrows(ArrayIndexOutOfBoundsException::class.java) {
      ImageConverter.unpackRgb888(UnsafeByteOperations.unsafeWrap(bytes, 0, bytes.size), IntArray(9))
    }
  }

  private fun runBenchmark(runnable: Runnable): Double {
    return runBenchmarkWithoutCorrection(runnable) - runBenchmarkWithoutCorrection {}
  }

  private fun runBenchmarkWithoutCorrection(runnable: Runnable): Double {
    // Warm up.
    for (i in 1..WARM_UP_LOOPS) {
      runnable.run()
    }
    // Measure performance.
    val start = System.currentTimeMillis()
    for (i in 1..BENCHMARK_LOOPS) {
      runnable.run()
    }
    return (System.currentTimeMillis() - start) / 1000.0 / BENCHMARK_LOOPS
  }

  private fun createTestImage(): ByteString {
    val offset = 5
    val bytes = ByteArray(offset + IMAGE_SIZE * 3) { it.toByte() }
    return UnsafeByteOperations.unsafeWrap(bytes, offset, IMAGE_SIZE * 3)
  }
}

private const val IMAGE_SIZE = 1000000
private const val WARM_UP_LOOPS = 1000
private const val BENCHMARK_LOOPS = 1000

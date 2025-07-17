/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.memory

import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BitmapDuplicationAnalyzerTest {

  private val analyzer = BitmapDuplicationAnalyzer()

  companion object {
    private const val BITMAP_CLASS_NAME = "android.graphics.Bitmap"
    private const val DUMP_DATA_CLASS_NAME = "android.graphics.Bitmap\$DumpData"
    private const val BYTE_ARRAY_CLASS_NAME = "byte[]"
    private const val BYTE_ARRAY_ARRAY_CLASS_NAME = "byte[][]"
    private const val LONG_ARRAY_CLASS_NAME = "long[]"
  }

  @Test
  fun testAnalyze_identifiesDuplicateBitmaps() {
    val fakeCapture = FakeCaptureObject.Builder().build()
    val bufferContent = byteArrayOf(1, 2, 3, 4)
    val otherBufferContent = byteArrayOf(9, 9, 9, 9)

    val nativePtrs = listOf(100L, 200L, 300L)
    val buffers = listOf(
      bufferContent.copyOf(),
      otherBufferContent.copyOf(),
      bufferContent.copyOf()
    )
    val dumpData = createDumpDataInstance(fakeCapture, nativePtrs, buffers)

    val bmp1 = createBitmapInstance(fakeCapture, 1L, 10, 10, 100L)
    val bmp2 = createBitmapInstance(fakeCapture, 2L, 20, 20, 200L)
    val bmp3 = createBitmapInstance(fakeCapture, 3L, 10, 10, 300L)

    // Add the top-level objects that should be on the heap. The test framework
    // should make any referenced objects (like the internal arrays) reachable.
    fakeCapture.addInstanceObjects(setOf(dumpData, bmp1, bmp2, bmp3))
    analyzer.analyze(fakeCapture.instances.toList())

    val duplicates = analyzer.getDuplicateInstances()
    assertThat(duplicates).containsExactly(bmp1, bmp3)
  }

  @Test
  fun testAnalyze_withNoDuplicates() {
    val fakeCapture = FakeCaptureObject.Builder().build()
    val bufferContent1 = byteArrayOf(1, 2, 3, 4)
    val bufferContent2 = byteArrayOf(5, 6, 7, 8)

    // Three bitmaps, none of which are duplicates.
    // bmp1 and bmp3 have the same content but different dimensions.
    // bmp1 and bmp2 have different content and different dimensions.
    val nativePtrs = listOf(100L, 200L, 300L)
    val buffers = listOf(
      bufferContent1.copyOf(),
      bufferContent2.copyOf(),
      bufferContent1.copyOf()
    )
    val dumpData = createDumpDataInstance(fakeCapture, nativePtrs, buffers)

    val bmp1 = createBitmapInstance(fakeCapture, 1L, 10, 10, 100L)
    val bmp2 = createBitmapInstance(fakeCapture, 2L, 20, 20, 200L)
    val bmp3 = createBitmapInstance(fakeCapture, 3L, 15, 15, 300L) // Same buffer as bmp1, different dimensions

    fakeCapture.addInstanceObjects(setOf(dumpData, bmp1, bmp2, bmp3))
    analyzer.analyze(fakeCapture.instances.toList())

    val duplicates = analyzer.getDuplicateInstances()
    assertThat(duplicates).isEmpty()
  }

  @Test
  fun testAnalyze_withMissingDumpData() {
    val fakeCapture = FakeCaptureObject.Builder().build()

    val bmp1 = createBitmapInstance(fakeCapture, 1L, 10, 10, 100L)
    val bmp2 = createBitmapInstance(fakeCapture, 2L, 10, 10, 300L)

    // No DumpData instance is added to the capture.
    fakeCapture.addInstanceObjects(setOf(bmp1, bmp2))
    analyzer.analyze(fakeCapture.instances.toList())

    val duplicates = analyzer.getDuplicateInstances()
    assertThat(duplicates).isEmpty()
  }

  @Test
  fun testAnalyze_withMismatchedArrays() {
    val fakeCapture = FakeCaptureObject.Builder().build()
    val bufferContent = byteArrayOf(1, 2, 3, 4)

    // Mismatch: 2 native pointers but only 1 buffer.
    val nativePtrs = listOf(100L, 200L)
    val buffers = listOf(bufferContent.copyOf())
    val dumpData = createDumpDataInstance(fakeCapture, nativePtrs, buffers)

    val bmp1 = createBitmapInstance(fakeCapture, 1L, 10, 10, 100L)
    val bmp2 = createBitmapInstance(fakeCapture, 2L, 20, 20, 200L)

    fakeCapture.addInstanceObjects(setOf(dumpData, bmp1, bmp2))
    analyzer.analyze(fakeCapture.instances.toList())

    val duplicates = analyzer.getDuplicateInstances()
    assertThat(duplicates).isEmpty()
  }

  /** Creates a fake `android.graphics.Bitmap` instance for testing. */
  private fun createBitmapInstance(
    capture: FakeCaptureObject,
    id: Long,
    width: Int,
    height: Int,
    nativePtr: Long
  ): FakeInstanceObject {
    val instance = FakeInstanceObject.Builder(capture, id, BITMAP_CLASS_NAME)
      .setFields(listOf("mWidth", "mHeight", "mNativePtr"))
      .setDepth(1)
      .build()
    instance.setFieldValue("mWidth", ValueObject.ValueType.INT, width)
    instance.setFieldValue("mHeight", ValueObject.ValueType.INT, height)
    instance.setFieldValue("mNativePtr", ValueObject.ValueType.LONG, nativePtr)
    return instance
  }

  /**
   * Creates a fake `android.graphics.Bitmap$DumpData` instance which holds the
   * mapping between native pointers and their corresponding pixel data buffers.
   * This structure is what the analyzer inspects to find bitmap data.
   */
  private fun createDumpDataInstance(
    capture: FakeCaptureObject,
    nativePtrs: List<Long>,
    buffers: List<ByteArray>
  ): FakeInstanceObject {
    val bufferInstances = buffers.mapIndexed { i, bytes ->
      FakeInstanceObject.Builder(capture, 1000L + i, BYTE_ARRAY_CLASS_NAME)
        .setValueType(ValueObject.ValueType.ARRAY)
        .setArray(ValueObject.ValueType.BYTE, bytes, bytes.size)
        .build()
    }

    val buffersArray = FakeInstanceObject.Builder(capture, 2000L, BYTE_ARRAY_ARRAY_CLASS_NAME)
      .setFields(buffers.indices.map { it.toString() })
      .build()
    bufferInstances.forEachIndexed { i, instance ->
      buffersArray.setFieldValue(i.toString(), ValueObject.ValueType.OBJECT, instance)
    }

    val nativesArray = FakeInstanceObject.Builder(capture, 3000L, LONG_ARRAY_CLASS_NAME)
      .setFields(nativePtrs.indices.map { it.toString() })
      .build()
    nativePtrs.forEachIndexed { i, ptr ->
      nativesArray.setFieldValue(i.toString(), ValueObject.ValueType.LONG, ptr)
    }

    val dumpDataInstance = FakeInstanceObject.Builder(capture, 4000L, DUMP_DATA_CLASS_NAME)
      .setFields(listOf("buffers", "natives"))
      .setDepth(1)
      .build()
    dumpDataInstance.setFieldValue("buffers", ValueObject.ValueType.OBJECT, buffersArray)
    dumpDataInstance.setFieldValue("natives", ValueObject.ValueType.OBJECT, nativesArray)
    return dumpDataInstance
  }
}
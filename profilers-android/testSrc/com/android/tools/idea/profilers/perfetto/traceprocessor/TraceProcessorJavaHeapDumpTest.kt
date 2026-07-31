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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.testutils.TestUtils
import com.android.tools.profiler.perfetto.proto.TraceProcessor.HeapDumpInstancesResult.InstanceData
import com.android.tools.profiler.perfetto.proto.TraceProcessor.HeapDumpResult
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryParameters
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.google.common.base.Ticker
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration test verifying that [TraceProcessorServiceImpl] can load a real `.hprof` file via the native daemon and return accurate heap
 * dump metadata, class overviews, instance lists, primitive fields, and reference graphs.
 */
class TraceProcessorJavaHeapDumpTest {
  @get:Rule val disposableRule = DisposableRule()

  private val tpdClient = TraceProcessorDaemonClient(Ticker.systemTicker())
  private val realTraceProcessorService = TraceProcessorServiceImpl(Ticker.systemTicker()) { tpdClient }
  private lateinit var customIdeServices: FakeIdeProfilerServices

  @Before
  fun setUp() {
    Disposer.register(disposableRule.disposable, tpdClient)

    val hprofPath = "tools/adt/idea/profilers/testData/hprofs/bitmap-duplicates.hprof"
    val traceFile = TestUtils.resolveWorkspacePath(hprofPath).toFile()
    require(traceFile.exists()) { "Trace file not found: $hprofPath" }

    customIdeServices =
      object : FakeIdeProfilerServices() {
        override val traceProcessorService: TraceProcessorService
          get() = realTraceProcessorService
      }

    val tempTraceFile = File.createTempFile("temp_heap", ".hprof")
    traceFile.copyTo(tempTraceFile, overwrite = true)

    assertTrue("Should load trace successfully", realTraceProcessorService.loadTrace(999L, tempTraceFile, customIdeServices))
  }

  @After
  fun tearDown() {
    Disposer.dispose(tpdClient)
    Disposer.dispose(realTraceProcessorService)
  }

  /**
   * Verifies end-to-end trace processor queries against a loaded `.hprof` trace file, checking:
   * 1. [TraceProcessorServiceImpl.loadHeapDumpData] returns populated class overviews.
   * 2. [TraceProcessorServiceImpl.getInstancesForClasses] resolves instances for known classes.
   * 3. [TraceProcessorServiceImpl.getPrimitiveFields] retrieves field data for instances.
   * 4. [TraceProcessorServiceImpl.getReferences] returns reference chains between objects.
   */
  @Test
  fun testTraceProcessorQueries() {
    // 1. Verify loadHeapDumpData (fetches Class overviews)
    val heapDumpData = realTraceProcessorService.loadHeapDumpData(999L, customIdeServices)
    verifyClassOverviews(heapDumpData)

    // 2. Verify getInstancesForClasses for Bitmap
    val sortedBitmaps = verifyInstancesForClasses(heapDumpData)

    // 3. Verify String pagination and correct order across all pages in the app heap
    verifyInstancePagination()

    // 4. Verify getPrimitiveFields for Bitmap
    val bitmapWith800x800 = sortedBitmaps.last()
    verifyPrimitiveFields(bitmapWith800x800.id)

    // 5. Verify getReferences
    verifyReferences(bitmapWith800x800.id)
  }

  private fun assertWithinTolerance(expected: Long, actual: Long, toleranceFraction: Double = 0.01, message: String = "") {
    val diff = abs(expected - actual)
    val maxDiff = (expected * toleranceFraction).toLong()
    assertTrue("$message Expected ~$expected but was $actual (diff: $diff, allowed max diff: $maxDiff)", diff <= maxDiff)
  }

  private fun verifyClassOverviews(heapDumpData: HeapDumpResult) {
    assertTrue("Should have class overviews", heapDumpData.classOverviewList.isNotEmpty())

    // Group by heap to cleanly sum up memory stats
    data class HeapStats(val shallow: Long, val retained: Long, val native: Long, val count: Long, val classCount: Int)
    val heapStats =
      heapDumpData.classOverviewList
        .groupBy { it.heapName }
        .mapValues { (_, classes) ->
          HeapStats(
            shallow = classes.sumOf { it.shallowSize },
            retained = classes.sumOf { it.retainedSize },
            native = classes.sumOf { it.nativeSize },
            count = classes.sumOf { it.instanceCount },
            classCount = classes.size,
          )
        }

    // Assert values within a 1% tolerance to avoid test brittleness on minor daemon updates,
    // while still acting as a canary for major structural changes.
    val app = heapStats["app"]!!
    assertWithinTolerance(5378590L, app.shallow, message = "App heap shallow size")
    assertWithinTolerance(9820729L, app.retained, message = "App heap retained size")
    assertWithinTolerance(5603911L, app.native, message = "App heap native size")
    assertWithinTolerance(41403L, app.count, message = "App heap instance count")
    assertWithinTolerance(3445L, app.classCount.toLong(), message = "App heap class count")

    val image = heapStats["image"]!!
    assertWithinTolerance(12721773L, image.shallow, message = "Image heap shallow size")
    assertWithinTolerance(19783488L, image.retained, message = "Image heap retained size")
    assertEquals("Image heap native size should be exactly 0", 0L, image.native)
    assertWithinTolerance(198991L, image.count, message = "Image heap instance count")
    assertWithinTolerance(29792L, image.classCount.toLong(), message = "Image heap class count")
  }

  private fun verifyInstancesForClasses(heapDumpData: HeapDumpResult): List<InstanceData> {
    val bitmapTotalCount = heapDumpData.classOverviewList.filter { it.className == "android.graphics.Bitmap" }.sumOf { it.instanceCount }
    assertEquals(4L, bitmapTotalCount)

    val bitmapInstancesResult = realTraceProcessorService.getInstancesForClasses(999L, listOf("android.graphics.Bitmap"), customIdeServices)
    assertEquals(4, bitmapInstancesResult.instanceList.size)

    // Verify individual bitmap instance dimensions
    val expectedRetainedNativeSizes = listOf(201781L, 201781L, 2560001L, 2560001L)
    val sortedBitmaps = bitmapInstancesResult.instanceList.sortedBy { it.retainedNativeSize }

    expectedRetainedNativeSizes.forEachIndexed { index, expectedSize ->
      assertEquals(expectedSize, sortedBitmaps[index].retainedNativeSize)
      assertEquals("All bitmaps should have 50 bytes shallow size", 50L, sortedBitmaps[index].selfSize)
    }
    return sortedBitmaps
  }

  private fun verifyInstancePagination() {
    val stringAppHeapCount = 2078L

    var offset = 0
    val pageSize = 1000
    val allStrings = mutableListOf<InstanceData>()

    while (offset < stringAppHeapCount) {
      val params =
        QueryParameters.HeapDumpInstancesParameters.newBuilder()
          .addClassNames("java.lang.String")
          .setHeapName("app")
          .setLimit(pageSize)
          .setOffset(offset)
          .setSortAttribute(QueryParameters.SortAttribute.SORT_RETAINED_SIZE)
          .setSortDescending(true)
          .build()
      val result = realTraceProcessorService.getInstances(999L, params, customIdeServices)
      allStrings.addAll(result.instanceList)
      offset += pageSize
    }

    assertWithinTolerance(stringAppHeapCount, allStrings.size.toLong(), message = "String instances count")

    // Verify continuously sorted by retained size descending across entire set
    for (i in 0 until allStrings.size - 1) {
      assertTrue("String pagination should maintain global sorting", allStrings[i].retainedSize >= allStrings[i + 1].retainedSize)
    }
  }

  private fun verifyPrimitiveFields(bitmapId: Long) {
    val fieldsResult = realTraceProcessorService.getPrimitiveFields(999L, listOf(bitmapId), customIdeServices)

    val bitmapFields = fieldsResult.instancesList.find { it.instanceId == bitmapId }
    assertNotNull("Should return primitive fields for bitmap", bitmapFields)

    val mHeight = bitmapFields!!.fieldList.find { it.name.endsWith(".mHeight") }
    val mWidth = bitmapFields.fieldList.find { it.name.endsWith(".mWidth") }
    val mDensity = bitmapFields.fieldList.find { it.name.endsWith(".mDensity") }
    val mRecycled = bitmapFields.fieldList.find { it.name.endsWith(".mRecycled") }
    val mRequestPremultiplied = bitmapFields.fieldList.find { it.name.endsWith(".mRequestPremultiplied") }

    assertEquals("800", mHeight?.value)
    assertEquals("800", mWidth?.value)
    assertEquals("160", mDensity?.value)
    assertEquals("0", mRecycled?.value)
    assertEquals("1", mRequestPremultiplied?.value)
  }

  private fun verifyReferences(bitmapId: Long) {
    val refsResult =
      realTraceProcessorService.getReferences(999L, listOf(bitmapId), fetchForward = true, fetchReverse = true, customIdeServices)
    assertTrue("Should have reference data for bitmap", refsResult.referenceList.isNotEmpty())
  }
}

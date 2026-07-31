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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profilers.FakeTraceProcessorService
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.ClassDb.ClassEntry
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.TraceProcessorHeapDumpInstanceObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.BitmapDuplicationInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.ProjectClassesInstanceFilter
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class TraceProcessorHeapDumpClassSetTest {

  private class TestTraceProcessorService : TraceProcessorService by FakeTraceProcessorService() {
    var lastRequest: TraceProcessor.QueryParameters.HeapDumpInstancesParameters? = null
    var lastTraceId: Long = 0L
    var response: TraceProcessor.HeapDumpInstancesResult = TraceProcessor.HeapDumpInstancesResult.getDefaultInstance()

    override fun getInstances(
      traceId: Long,
      request: TraceProcessor.QueryParameters.HeapDumpInstancesParameters,
      ideProfilerServices: IdeProfilerServices,
    ): TraceProcessor.HeapDumpInstancesResult {
      lastTraceId = traceId
      lastRequest = request
      return response
    }
  }

  private lateinit var mockCaptureObject: HeapDumpCaptureObject
  private lateinit var mockServices: IdeProfilerServices
  private lateinit var testTraceProcessorService: TestTraceProcessorService
  private lateinit var mockClassDb: ClassDb
  private lateinit var sampleClassEntry: ClassEntry

  @Before
  fun setUp() {
    mockCaptureObject = mock(HeapDumpCaptureObject::class.java)
    mockServices = mock(IdeProfilerServices::class.java)
    testTraceProcessorService = TestTraceProcessorService()
    mockClassDb = mock(ClassDb::class.java)

    doReturn(mockServices).`when`(mockCaptureObject).ideProfilerServices
    doReturn(testTraceProcessorService).`when`(mockServices).traceProcessorService
    doReturn(emptyList<Any>()).`when`(mockCaptureObject).getClassObjectInstances(anyLong())
    doReturn("hprof").`when`(mockCaptureObject).fileExtension

    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(1000L).build()
    doReturn(heapDumpInfo).`when`(mockCaptureObject).heapDumpInfo

    val appHeapSet = HeapSet(mockCaptureObject, "app", 0)
    doReturn(appHeapSet).`when`(mockCaptureObject).getHeapSet(anyInt())

    sampleClassEntry = ClassEntry(10L, 0L, "android.graphics.Bitmap", 5)
  }

  @Test
  fun testClassAndIssueFilterMatching() {
    val classSet = TraceProcessorHeapDumpClassSet(sampleClassEntry, 0, mockCaptureObject)

    // 1. Project class filter matching
    doReturn(setOf("android.graphics.Bitmap")).`when`(mockCaptureObject).projectClasses
    doReturn(ProjectClassesInstanceFilter(mockServices)).`when`(mockCaptureObject).classTypeFilter
    assertThat(classSet.isClassFilterMatch).isTrue()

    doReturn(emptySet<String>()).`when`(mockCaptureObject).projectClasses
    assertThat(classSet.isClassFilterMatch).isFalse()

    // 2. Issue filters matching: Bitmap duplicates vs Activity/Fragment leaks
    doReturn(null).`when`(mockCaptureObject).classTypeFilter
    doReturn(setOf("android.graphics.Bitmap")).`when`(mockCaptureObject).classesWithDuplicates
    doReturn(emptySet<String>()).`when`(mockCaptureObject).classesWithLeaks

    doReturn(BitmapDuplicationInstanceFilter(emptySet())).`when`(mockCaptureObject).issueTypeFilter
    assertThat(classSet.isClassFilterMatch).isTrue()

    doReturn(ActivityFragmentLeakInstanceFilter(mockClassDb)).`when`(mockCaptureObject).issueTypeFilter
    assertThat(classSet.isClassFilterMatch).isFalse()
  }

  @Test
  fun testSortingAndPaginationWithDaemon() {
    val classSet = TraceProcessorHeapDumpClassSet(sampleClassEntry, 0, mockCaptureObject)

    // Configure descending sort by retained size
    classSet.setSort(CaptureObject.InstanceAttribute.RETAINED_SIZE, true)

    val instanceData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder()
        .setId(500L)
        .setTypeId(10L)
        .setHeapName("app")
        .setSelfSize(32)
        .setRetainedSize(640000)
        .build()

    val response = TraceProcessor.HeapDumpInstancesResult.newBuilder().addInstance(instanceData).build()

    testTraceProcessorService.response = response

    val expectedInstanceObject =
      TraceProcessorHeapDumpInstanceObject(sampleClassEntry, instanceData, ValueObject.ValueType.OBJECT, mockCaptureObject)
    doReturn(expectedInstanceObject).`when`(mockCaptureObject).getOrCreateTraceProcessorHeapDumpInstance(sampleClassEntry, instanceData)

    val instances = classSet.getInstances(offset = 0, limit = 10)
    assertThat(instances).hasSize(1)
    assertThat(instances[0] === expectedInstanceObject).isTrue()

    // Verify correct pagination and sorting parameters received by TestTraceProcessorService
    assertThat(testTraceProcessorService.lastTraceId).isEqualTo(1000L)
    val request = testTraceProcessorService.lastRequest
    assertThat(request).isNotNull()
    assertThat(request!!.classNamesList).containsExactly("android.graphics.Bitmap")
    assertThat(request.offset).isEqualTo(0)
    assertThat(request.limit).isEqualTo(10)
    assertThat(request.sortAttribute).isEqualTo(TraceProcessor.QueryParameters.SortAttribute.SORT_RETAINED_SIZE)
    assertThat(request.sortDescending).isTrue()
    assertThat(request.heapName).isEqualTo("app")
  }
}

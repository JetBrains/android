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
package com.android.tools.profilers.memory

import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeTraceProcessorService
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test

/** Tests for [TraceProcessorBitmapDuplicationAnalyzer] validating duplicate bitmap detection in Trace Processor heap dumps. */
class TraceProcessorBitmapDuplicationAnalyzerTest {

  /** Tests bitmap duplication identification based on pixel buffer byte array contents and dimensions. */
  @Test
  fun testDuplicateBitmapsWithBuffers() {
    val capture = FakeCaptureObject.Builder().build()

    val bufferInstance1 =
      FakeInstanceObject.Builder(capture, 10L, "byte[]")
        .setValueType(ValueObject.ValueType.ARRAY)
        .setArray(ValueObject.ValueType.BYTE, byteArrayOf(1, 2, 3, 4), 4)
        .build()

    val bitmap1 =
      FakeInstanceObject.Builder(capture, 1L, TraceProcessorBitmapDuplicationAnalyzer.BITMAP_CLASS_NAME)
        .setValueType(ValueObject.ValueType.OBJECT)
        .addField("mWidth", ValueObject.ValueType.INT, 10)
        .addField("mHeight", ValueObject.ValueType.INT, 10)
        .addField("mBuffer", ValueObject.ValueType.OBJECT, bufferInstance1)
        .setDepth(1)
        .build()

    val bufferInstance2 =
      FakeInstanceObject.Builder(capture, 20L, "byte[]")
        .setValueType(ValueObject.ValueType.ARRAY)
        .setArray(ValueObject.ValueType.BYTE, byteArrayOf(1, 2, 3, 4), 4)
        .build()

    val bitmap2 =
      FakeInstanceObject.Builder(capture, 2L, TraceProcessorBitmapDuplicationAnalyzer.BITMAP_CLASS_NAME)
        .setValueType(ValueObject.ValueType.OBJECT)
        .addField("mWidth", ValueObject.ValueType.INT, 10)
        .addField("mHeight", ValueObject.ValueType.INT, 10)
        .addField("mBuffer", ValueObject.ValueType.OBJECT, bufferInstance2)
        .setDepth(1)
        .build()

    val analyzer = TraceProcessorBitmapDuplicationAnalyzer()
    analyzer.analyze(listOf(bitmap1, bitmap2), capture)

    val duplicates = analyzer.getDuplicateInstances()
    assertThat(duplicates).containsExactly(bitmap1, bitmap2)
  }

  /** Tests gRPC bulk prefetching of bitmap primitive fields and buffer references during duplicate detection. */
  @Test
  fun testBitmapDuplicationWithGrpcResponse() {
    val bitmapClassId = 100L
    val bitmap1Id = 1L
    val bitmap2Id = 2L

    val wField =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder().setName("mWidth").setTypeName("int").setValue("10").build()
    val hField =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder().setName("mHeight").setTypeName("int").setValue("10").build()
    val inst1 =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(bitmap1Id)
        .addField(wField)
        .addField(hField)
        .build()
    val inst2 =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(bitmap2Id)
        .addField(wField)
        .addField(hField)
        .build()

    val blob = ByteString.copyFrom(byteArrayOf(1, 2, 3, 4))
    val buf1Inst =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(10L)
        .setArrayType("byte")
        .setArrayBlob(blob)
        .build()
    val buf2Inst =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(20L)
        .setArrayType("byte")
        .setArrayBlob(blob)
        .build()

    val primitiveFieldsResult =
      TraceProcessor.GetPrimitiveFieldsResult.newBuilder()
        .addInstances(inst1)
        .addInstances(inst2)
        .addInstances(buf1Inst)
        .addInstances(buf2Inst)
        .build()

    val ref1 =
      TraceProcessor.GetReferencesResult.ReferenceData.newBuilder().setOwnerId(bitmap1Id).setOwnedId(10L).setFieldName("mBuffer").build()
    val ref2 =
      TraceProcessor.GetReferencesResult.ReferenceData.newBuilder().setOwnerId(bitmap2Id).setOwnedId(20L).setFieldName("mBuffer").build()
    val referencesResult = TraceProcessor.GetReferencesResult.newBuilder().addReference(ref1).addReference(ref2).build()

    val buf1Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(10L).setTypeId(200L).setDepth(2).setArrayLength(4).build()
    val buf2Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(20L).setTypeId(200L).setDepth(2).setArrayLength(4).build()
    val instancesResult = TraceProcessor.HeapDumpInstancesResult.newBuilder().addInstance(buf1Data).addInstance(buf2Data).build()

    val traceProcessorService =
      FakeTraceProcessorService().apply {
        this.primitiveFieldsResult = primitiveFieldsResult
        this.referencesResult = referencesResult
        this.instancesResult = instancesResult
      }

    val ideServices =
      object : FakeIdeProfilerServices() {
        override val traceProcessorService = traceProcessorService
      }

    val client = ProfilerClient("localhost")
    val session = Common.Session.newBuilder().setSessionId(1L).build()
    val dumpInfo = Memory.HeapDumpInfo.newBuilder().setStartTime(0L).setEndTime(1L).build()

    val capture =
      HeapDumpCaptureObject(
          client,
          session,
          dumpInfo,
          null,
          ideServices.featureTracker,
          ideServices,
          { File.createTempFile("test", "hprof").apply { deleteOnExit() } },
        )
        .apply { fileExtension = "hprof" }

    val bitmapClassEntry = capture.classDb.registerClass(bitmapClassId, -1L, TraceProcessorBitmapDuplicationAnalyzer.BITMAP_CLASS_NAME, -1L)
    val bufferClassEntry = capture.classDb.registerClass(200L, -1L, "byte[]", -1L)

    val inst1Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(bitmap1Id).setTypeId(bitmapClassId).setDepth(1).build()
    val inst2Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(bitmap2Id).setTypeId(bitmapClassId).setDepth(1).build()

    val tpBitmap1 = capture.getOrCreateTraceProcessorHeapDumpInstance(bitmapClassEntry, inst1Data)
    val tpBitmap2 = capture.getOrCreateTraceProcessorHeapDumpInstance(bitmapClassEntry, inst2Data)
    capture.getOrCreateTraceProcessorHeapDumpInstance(bufferClassEntry, buf1Data)
    capture.getOrCreateTraceProcessorHeapDumpInstance(bufferClassEntry, buf2Data)

    val analyzer = TraceProcessorBitmapDuplicationAnalyzer()
    analyzer.analyze(listOf(tpBitmap1, tpBitmap2), capture)

    val duplicates = analyzer.getDuplicateInstances()
    assertThat(duplicates).containsExactly(tpBitmap1, tpBitmap2)
  }

  /**
   * Tests that a background bulk fetch of primitive fields correctly interleaves with a UI thread requesting fields without throwing a
   * ConcurrentModificationException or corrupting state.
   */
  @Test
  fun testConcurrentBulkFetchAndGetFields() {
    val bitmapClassId = 100L
    val bitmap1Id = 1L
    val bitmap2Id = 2L

    val wField =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder().setName("mWidth").setTypeName("int").setValue("10").build()
    val hField =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder().setName("mHeight").setTypeName("int").setValue("10").build()
    val inst1 =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(bitmap1Id)
        .addField(wField)
        .addField(hField)
        .build()
    val inst2 =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(bitmap2Id)
        .addField(wField)
        .addField(hField)
        .build()

    val blob = ByteString.copyFrom(byteArrayOf(1, 2, 3, 4))
    val buf1Inst =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(10L)
        .setArrayType("byte")
        .setArrayBlob(blob)
        .build()
    val buf2Inst =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(20L)
        .setArrayType("byte")
        .setArrayBlob(blob)
        .build()

    val primitiveFieldsResult =
      TraceProcessor.GetPrimitiveFieldsResult.newBuilder()
        .addInstances(inst1)
        .addInstances(inst2)
        .addInstances(buf1Inst)
        .addInstances(buf2Inst)
        .build()
    val ref1 =
      TraceProcessor.GetReferencesResult.ReferenceData.newBuilder().setOwnerId(bitmap1Id).setOwnedId(10L).setFieldName("mBuffer").build()
    val ref2 =
      TraceProcessor.GetReferencesResult.ReferenceData.newBuilder().setOwnerId(bitmap2Id).setOwnedId(20L).setFieldName("mBuffer").build()
    val referencesResult = TraceProcessor.GetReferencesResult.newBuilder().addReference(ref1).addReference(ref2).build()

    val buf1Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(10L).setTypeId(200L).setDepth(2).setArrayLength(4).build()
    val buf2Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(20L).setTypeId(200L).setDepth(2).setArrayLength(4).build()
    val instancesResult = TraceProcessor.HeapDumpInstancesResult.newBuilder().addInstance(buf1Data).addInstance(buf2Data).build()

    val latchStartFetch = CountDownLatch(1)
    val latchFinishGetFields = CountDownLatch(1)

    val fakeService =
      FakeTraceProcessorService().apply {
        this.primitiveFieldsResult = primitiveFieldsResult
        this.referencesResult = referencesResult
        this.instancesResult = instancesResult
      }

    val traceProcessorService =
      object : TraceProcessorService by fakeService {
        override fun getPrimitiveFields(
          traceId: Long,
          instanceIds: List<Long>,
          ideProfilerServices: IdeProfilerServices,
        ): TraceProcessor.GetPrimitiveFieldsResult {
          latchStartFetch.countDown()
          latchFinishGetFields.await(2, TimeUnit.SECONDS)
          return fakeService.getPrimitiveFields(traceId, instanceIds, ideProfilerServices)
        }
      }

    val ideServices =
      object : FakeIdeProfilerServices() {
        override val traceProcessorService = traceProcessorService
      }

    val client = ProfilerClient("localhost")
    val session = Common.Session.newBuilder().setSessionId(1L).build()
    val dumpInfo = Memory.HeapDumpInfo.newBuilder().setStartTime(0L).setEndTime(1L).build()
    val capture =
      HeapDumpCaptureObject(
          client,
          session,
          dumpInfo,
          null,
          ideServices.featureTracker,
          ideServices,
          { File.createTempFile("test", "hprof").apply { deleteOnExit() } },
        )
        .apply { fileExtension = "hprof" }

    val bitmapClassEntry = capture.classDb.registerClass(bitmapClassId, -1L, TraceProcessorBitmapDuplicationAnalyzer.BITMAP_CLASS_NAME, -1L)
    val bufferClassEntry = capture.classDb.registerClass(200L, -1L, "byte[]", -1L)

    val inst1Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(bitmap1Id).setTypeId(bitmapClassId).setDepth(1).build()
    val inst2Data =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(bitmap2Id).setTypeId(bitmapClassId).setDepth(1).build()

    val tpBitmap1 = capture.getOrCreateTraceProcessorHeapDumpInstance(bitmapClassEntry, inst1Data)
    val tpBitmap2 = capture.getOrCreateTraceProcessorHeapDumpInstance(bitmapClassEntry, inst2Data)
    capture.getOrCreateTraceProcessorHeapDumpInstance(bufferClassEntry, buf1Data)
    capture.getOrCreateTraceProcessorHeapDumpInstance(bufferClassEntry, buf2Data)

    val analyzer = TraceProcessorBitmapDuplicationAnalyzer()

    var analyzerFailed = false
    val backgroundThread = Thread {
      try {
        analyzer.analyze(listOf(tpBitmap1, tpBitmap2), capture)
      } catch (t: Throwable) {
        analyzerFailed = true
      }
    }
    backgroundThread.start()

    // Wait until background thread enters getPrimitiveFields (bulk fetch)
    latchStartFetch.await(2, TimeUnit.SECONDS)

    // Simulate UI thread expanding a node and accessing fields while fetch is blocked
    val fields = tpBitmap1.fields

    // Resume bulk fetch
    latchFinishGetFields.countDown()
    backgroundThread.join(2000)

    assertThat(analyzerFailed).isFalse()
    assertThat(fields).isNotNull()
    val duplicates = analyzer.getDuplicateInstances()
    assertThat(duplicates).containsExactly(tpBitmap1, tpBitmap2)
  }
}

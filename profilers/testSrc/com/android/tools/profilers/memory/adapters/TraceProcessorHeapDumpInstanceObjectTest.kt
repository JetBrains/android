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
package com.android.tools.profilers.memory.adapters

import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.memory.adapters.ClassDb.ClassEntry
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyList
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TraceProcessorHeapDumpInstanceObjectTest {

  private lateinit var mockCaptureObject: HeapDumpCaptureObject
  private lateinit var sampleClassEntry: ClassEntry

  @Before
  fun setUp() {
    mockCaptureObject = mock(HeapDumpCaptureObject::class.java)
    `when`(mockCaptureObject.fileExtension).thenReturn("hprof")
    `when`(mockCaptureObject.isARTHeapDump).thenReturn(true)
    `when`(mockCaptureObject.getReferencesBulk(anyList(), anyBoolean(), anyBoolean()))
      .thenReturn(TraceProcessor.GetReferencesResult.getDefaultInstance())
    sampleClassEntry = ClassEntry(10L, 0L, "android.graphics.Bitmap", 1)
  }

  @Test
  fun testLazyFieldResolutionAndCaching() {
    val instanceId = 100L
    val instanceData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder()
        .setId(instanceId)
        .setTypeId(10L)
        .setHeapName("app")
        .setSelfSize(32)
        .setRetainedSize(640032)
        .setDepth(2)
        .setHasPrimitiveFields(true)
        .build()

    val fieldWidth =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder()
        .setName("android.graphics.Bitmap.mWidth")
        .setTypeName("int")
        .setValue("800")
        .build()
    val fieldHeight =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder()
        .setName("android.graphics.Bitmap.mHeight")
        .setTypeName("int")
        .setValue("800")
        .build()
    val fieldRecycled =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder()
        .setName("android.graphics.Bitmap.mRecycled")
        .setTypeName("boolean")
        .setValue("0")
        .build()

    val instancePrimitiveFields =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(instanceId)
        .addField(fieldWidth)
        .addField(fieldHeight)
        .addField(fieldRecycled)
        .build()

    val primitiveFieldsResult = TraceProcessor.GetPrimitiveFieldsResult.newBuilder().addInstances(instancePrimitiveFields).build()

    `when`(mockCaptureObject.getPrimitiveFields(instanceId)).thenReturn(primitiveFieldsResult)

    val instanceObject =
      TraceProcessorHeapDumpInstanceObject(sampleClassEntry, instanceData, ValueObject.ValueType.OBJECT, mockCaptureObject)

    assertThat(instanceObject.depth).isEqualTo(2)
    assertThat(instanceObject.shallowSize).isEqualTo(32)
    assertThat(instanceObject.retainedSize).isEqualTo(640032L)
    assertThat(instanceObject.heapId).isEqualTo("app".standardHeapId())

    // First call triggers resolution via captureObject.getPrimitiveFields
    val fields = instanceObject.fields
    assertThat(fields).hasSize(3)

    val widthField = fields[0]
    assertThat(widthField.fieldName).isEqualTo("mWidth")
    assertThat(widthField.valueType).isEqualTo(ValueObject.ValueType.INT)
    assertThat(widthField.valueText).isEqualTo("800")
    assertThat(widthField.value).isEqualTo(800)

    val heightField = fields[1]
    assertThat(heightField.fieldName).isEqualTo("mHeight")
    assertThat(heightField.valueType).isEqualTo(ValueObject.ValueType.INT)
    assertThat(heightField.valueText).isEqualTo("800")
    assertThat(heightField.value).isEqualTo(800)

    val recycledField = fields[2]
    assertThat(recycledField.fieldName).isEqualTo("mRecycled")
    assertThat(recycledField.valueType).isEqualTo(ValueObject.ValueType.BOOLEAN)
    assertThat(recycledField.valueText).isEqualTo("false")
    assertThat(recycledField.value).isEqualTo(false)

    verify(mockCaptureObject, times(1)).getPrimitiveFields(instanceId)

    // Second call should use cached fields and not invoke getPrimitiveFields again
    val cachedFields = instanceObject.fields
    assertThat(cachedFields === fields).isTrue()
    verify(mockCaptureObject, times(1)).getPrimitiveFields(instanceId)
  }

  @Test
  fun testLazyReferenceChainResolution() {
    val ownerId = 200L
    val ownedId = 300L

    val ownedInstanceData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder()
        .setId(ownedId)
        .setTypeId(10L)
        .setHeapName("app")
        .setHasReferences(true)
        .build()

    val ownerInstanceData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(ownerId).setTypeId(10L).setHeapName("app").build()

    val ownerInstanceObject =
      TraceProcessorHeapDumpInstanceObject(sampleClassEntry, ownerInstanceData, ValueObject.ValueType.OBJECT, mockCaptureObject)

    val reverseReference =
      TraceProcessor.GetReferencesResult.ReferenceData.newBuilder().setOwnerId(ownerId).setOwnedId(ownedId).setFieldName("mChild").build()

    val referencesResult = TraceProcessor.GetReferencesResult.newBuilder().addReference(reverseReference).build()

    `when`(mockCaptureObject.findInstanceObjectByIdCached(ownerId)).thenReturn(ownerInstanceObject)
    `when`(mockCaptureObject.getReferencesBulk(anyList(), eq(false), eq(true))).thenReturn(referencesResult)

    val ownedInstanceObject =
      TraceProcessorHeapDumpInstanceObject(sampleClassEntry, ownedInstanceData, ValueObject.ValueType.OBJECT, mockCaptureObject)

    val references = ownedInstanceObject.references
    assertThat(references).hasSize(1)

    val refObject = references[0]
    assertThat(refObject.referenceInstance).isEqualTo(ownerInstanceObject)
    assertThat(refObject.referenceFieldNames).containsExactly("mChild")
  }

  @Test
  fun testArrayFieldResolutionAndDecoding() {
    val arrayId = 500L
    val arrayClassEntry = ClassEntry(20L, 0L, "int[]", 1)
    val arrayInstanceData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder()
        .setId(arrayId)
        .setTypeId(20L)
        .setHeapName("app")
        .setArrayLength(3)
        .setHasPrimitiveFields(true)
        .build()

    // Create a 12-byte little-endian byte buffer with 3 integer values: 100, 200, 300
    val byteBuffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.putInt(100)
    byteBuffer.putInt(200)
    byteBuffer.putInt(300)
    byteBuffer.flip()

    val arrayFields =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder()
        .setInstanceId(arrayId)
        .setArrayType("int")
        .setArrayBlob(ByteString.copyFrom(byteBuffer))
        .build()

    val primitiveFieldsResult = TraceProcessor.GetPrimitiveFieldsResult.newBuilder().addInstances(arrayFields).build()

    `when`(mockCaptureObject.getPrimitiveFields(arrayId)).thenReturn(primitiveFieldsResult)

    val arrayInstanceObject =
      TraceProcessorHeapDumpInstanceObject(arrayClassEntry, arrayInstanceData, ValueObject.ValueType.ARRAY, mockCaptureObject)

    val fields = arrayInstanceObject.fields
    assertThat(fields).hasSize(3)

    assertThat(fields[0].fieldName).isEqualTo("0")
    assertThat(fields[0].valueType).isEqualTo(ValueObject.ValueType.INT)
    assertThat(fields[0].valueText).isEqualTo("100")
    assertThat(fields[0].value).isEqualTo(100)

    assertThat(fields[1].fieldName).isEqualTo("1")
    assertThat(fields[1].valueText).isEqualTo("200")
    assertThat(fields[1].value).isEqualTo(200)

    assertThat(fields[2].fieldName).isEqualTo("2")
    assertThat(fields[2].valueText).isEqualTo("300")
    assertThat(fields[2].value).isEqualTo(300)
  }
}

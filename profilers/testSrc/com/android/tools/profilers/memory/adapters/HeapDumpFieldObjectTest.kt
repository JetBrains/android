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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflib.heap.Instance
import com.android.tools.perflib.heap.Type
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HeapDumpFieldObjectTest {
  private val timer = FakeTimer()
  @get:Rule val grpcChannel = FakeGrpcChannel("HeapDumpFieldObjectTestChannel", FakeTransportService(timer))

  private lateinit var captureObject: FakeHeapDumpCaptureObject

  private class FakeHeapDumpCaptureObject(client: ProfilerClient, ideProfilerServices: IdeProfilerServices) :
    HeapDumpCaptureObject(
      client,
      Common.Session.getDefaultInstance(),
      HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(1).build(),
      null,
      FakeFeatureTracker(),
      ideProfilerServices,
      null,
    ) {
    private val myInstanceObjectMap = mutableMapOf<Instance, HeapDumpInstanceObject>()

    fun addInstance(instance: Instance, instanceObject: HeapDumpInstanceObject) {
      myInstanceObjectMap[instance] = instanceObject
    }

    fun getInstance(instance: Instance): HeapDumpInstanceObject? = myInstanceObjectMap[instance]

    override fun load(queryRange: Range?, queryJoiner: Executor?) = true

    override fun findInstanceObject(instance: Instance): InstanceObject? = myInstanceObjectMap[instance]
  }

  @Before
  fun setUp() {
    val profilerServices = FakeIdeProfilerServices()
    val profilerClient = ProfilerClient(grpcChannel.channel)
    val profilers = StudioProfilers(profilerClient, profilerServices, timer)
    val stage = MainMemoryProfilerStage(profilers, FakeCaptureObjectLoader())
    captureObject = FakeHeapDumpCaptureObject(profilers.client, stage.studioProfilers.ideServices)
  }

  @Test
  fun testHeapDumpFieldObjectProperties() {
    val parentInstance = MockClassInstance(-1, 0, "TestClass")
    val stringInstance = MockClassInstance(-1, 0, "java.lang.String")
    val classObj = MockClassObj(-1, "MyClass", 0)

    parentInstance.addFieldValue(Type.INT, "intField", 123)
    parentInstance.addFieldValue(Type.BOOLEAN, "boolField", true)
    parentInstance.addFieldValue(Type.OBJECT, "stringField", stringInstance)
    parentInstance.addFieldValue(Type.OBJECT, "classField", classObj)
    parentInstance.addFieldValue(Type.OBJECT, "nullField", null)

    captureObject.addInstance(
      stringInstance,
      HeapDumpInstanceObject(captureObject, stringInstance, captureObject.classDb.registerClass(1, "java.lang.String"), ValueType.STRING),
    )
    captureObject.addInstance(
      classObj,
      HeapDumpInstanceObject(captureObject, classObj, captureObject.classDb.registerClass(2, "MyClass"), ValueType.CLASS),
    )
    captureObject.addInstance(
      parentInstance,
      HeapDumpInstanceObject(captureObject, parentInstance, captureObject.classDb.registerClass(3, "TestClass"), ValueType.OBJECT),
    )

    val parentInstanceObject = captureObject.getInstance(parentInstance)!!
    val fields: List<FieldObject> = parentInstanceObject.fields
    assertThat(fields).hasSize(5)

    // int field
    val intField = fields[0]
    assertThat(intField.fieldName).isEqualTo("intField")
    assertThat(intField.valueType).isEqualTo(ValueType.INT)
    assertThat(intField.value).isEqualTo(123)
    assertThat(intField.valueText).isEmpty()
    assertThat(intField.toStringText).isEqualTo("123")
    assertThat(intField.asInstance).isNull()
    assertThat(intField.shallowSize).isEqualTo(Type.INT.size)

    // bool field
    val boolField = fields[1]
    assertThat(boolField.fieldName).isEqualTo("boolField")
    assertThat(boolField.valueType).isEqualTo(ValueType.BOOLEAN)
    assertThat(boolField.value).isEqualTo(true)
    assertThat(boolField.toStringText).isEqualTo("true")

    // string field
    val stringField = fields[2]
    assertThat(stringField.fieldName).isEqualTo("stringField")
    assertThat(stringField.valueType).isEqualTo(ValueType.STRING)
    assertThat(stringField.asInstance).isNotNull()
    assertThat(stringField.valueText).isEqualTo("{String}")

    // class field
    val classField = fields[3]
    assertThat(classField.fieldName).isEqualTo("classField")
    assertThat(classField.valueType).isEqualTo(ValueType.CLASS)
    assertThat(classField.asInstance).isNotNull()

    // null field
    val nullField = fields[4]
    assertThat(nullField.fieldName).isEqualTo("nullField")
    assertThat(nullField.valueType).isEqualTo(ValueType.NULL)
    assertThat(nullField.asInstance).isNull()
    assertThat(nullField.valueText).isEqualTo("null")
    assertThat(nullField.toStringText).isEmpty()

    // Equality and hashcode
    assertThat(intField).isEqualTo(fields[0])
    assertThat(intField.hashCode()).isEqualTo(fields[0].hashCode())
    assertThat(intField).isNotEqualTo(boolField)
  }
}

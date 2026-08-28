/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.perflib.heap.Snapshot
import com.android.tools.perflib.heap.Type
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.ARRAY
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.BOOLEAN
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.BYTE
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.CHAR
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.CLASS
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.DOUBLE
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.FLOAT
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.INT
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.LONG
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.NULL
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.OBJECT
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.SHORT
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.STRING
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class HeapDumpInstanceObjectTest {
  private val myTimer = FakeTimer()

  @get:Rule val myGrpcChannel = FakeGrpcChannel("MemoryNavigationTestGrpc", FakeTransportService(myTimer))

  private lateinit var myCaptureObject: FakeHeapDumpCaptureObject
  private lateinit var mySnapshot: Snapshot

  @Before
  fun setup() {
    val profilerServices = FakeIdeProfilerServices()
    val profilerClient = ProfilerClient(myGrpcChannel.channel)
    myCaptureObject = FakeHeapDumpCaptureObject(profilerClient, profilerServices)
    mySnapshot = mock()
  }

  /** Tests that FieldObjects are generated correctly based on a Hprof ClassInstance object. */
  @Test
  fun testExtractFieldsWithClassInstance() {
    val classInstance = MockClassInstance(-1, 0, "MockClass2")
    val classObj = MockClassObj(-1, "MockClass3", 0)
    val stringInstance = MockClassInstance(-1, 0, "java.lang.String")

    val targetInstance =
      MockClassInstance(-1, 0, "MockClass1").apply {
        addFieldValue(Type.OBJECT, "objectTest", classInstance)
        addFieldValue(Type.BOOLEAN, "boolTest", true)
        addFieldValue(Type.CHAR, "charTest", 'a')
        addFieldValue(Type.FLOAT, "floatTest", 1f)
        addFieldValue(Type.DOUBLE, "doubleTest", 2.0)
        addFieldValue(Type.BYTE, "byteTest", 1.toByte())
        addFieldValue(Type.SHORT, "shortTest", 3.toShort())
        addFieldValue(Type.INT, "intTest", 4)
        addFieldValue(Type.LONG, "longTest", 5L)
        addFieldValue(Type.OBJECT, "classTest", classObj)
        addFieldValue(Type.OBJECT, "stringTest", stringInstance)
        addFieldValue(Type.OBJECT, "nullTest", null)
      }

    myCaptureObject.addInstance(
      classInstance,
      HeapDumpInstanceObject(myCaptureObject, classInstance, myCaptureObject.classDb.registerClass(1, "MockClass2"), OBJECT),
    )
    myCaptureObject.addInstance(
      classObj,
      HeapDumpInstanceObject(myCaptureObject, classObj, myCaptureObject.classDb.registerClass(2, "MockClass3"), CLASS),
    )
    myCaptureObject.addInstance(
      stringInstance,
      HeapDumpInstanceObject(myCaptureObject, stringInstance, myCaptureObject.classDb.registerClass(3, "java.lang.String"), STRING),
    )
    myCaptureObject.addInstance(
      targetInstance,
      HeapDumpInstanceObject(myCaptureObject, targetInstance, myCaptureObject.classDb.registerClass(4, "MockClass1"), OBJECT),
    )

    val fields = myCaptureObject.getInstance(targetInstance).fields
    assertThat(fields).hasSize(12)
    assertThat(fields[0].fieldName).isEqualTo("objectTest")
    assertThat(fields[0].valueType).isEqualTo(OBJECT)
    assertThat(fields[1].fieldName).isEqualTo("boolTest")
    assertThat(fields[1].valueType).isEqualTo(BOOLEAN)
    assertThat(fields[2].fieldName).isEqualTo("charTest")
    assertThat(fields[2].valueType).isEqualTo(CHAR)
    assertThat(fields[3].fieldName).isEqualTo("floatTest")
    assertThat(fields[3].valueType).isEqualTo(FLOAT)
    assertThat(fields[4].fieldName).isEqualTo("doubleTest")
    assertThat(fields[4].valueType).isEqualTo(DOUBLE)
    assertThat(fields[5].fieldName).isEqualTo("byteTest")
    assertThat(fields[5].valueType).isEqualTo(BYTE)
    assertThat(fields[6].fieldName).isEqualTo("shortTest")
    assertThat(fields[6].valueType).isEqualTo(SHORT)
    assertThat(fields[7].fieldName).isEqualTo("intTest")
    assertThat(fields[7].valueType).isEqualTo(INT)
    assertThat(fields[8].fieldName).isEqualTo("longTest")
    assertThat(fields[8].valueType).isEqualTo(LONG)
    assertThat(fields[9].fieldName).isEqualTo("classTest")
    assertThat(fields[9].valueType).isEqualTo(CLASS)
    assertThat(fields[10].fieldName).isEqualTo("stringTest")
    assertThat(fields[10].valueType).isEqualTo(STRING)
    assertThat(fields[11].fieldName).isEqualTo("nullTest")
    assertThat(fields[11].valueType).isEqualTo(NULL)
  }

  /** Tests that FieldObjects are generated correctly based on a Hprof ArrayInstance object. */
  @Test
  fun testExtractFieldsWithArrayInstance() {
    val element0 = MockClassInstance(-1, 0, MOCK_CLASS)
    val element1 = MockClassInstance(-1, 0, MOCK_CLASS)
    val element2 = MockClassInstance(-1, 0, MOCK_CLASS)
    val arrayInstance =
      MockArrayInstance(-1, Type.OBJECT, 3, 0).apply {
        setValue(0, element0)
        setValue(1, element1)
        setValue(2, element2)
      }
    val mockClassEntry = myCaptureObject.classDb.registerClass(0, MOCK_CLASS)
    myCaptureObject.addInstance(element0, HeapDumpInstanceObject(myCaptureObject, element0, mockClassEntry, OBJECT))
    myCaptureObject.addInstance(element1, HeapDumpInstanceObject(myCaptureObject, element1, mockClassEntry, OBJECT))
    myCaptureObject.addInstance(element2, HeapDumpInstanceObject(myCaptureObject, element2, mockClassEntry, OBJECT))
    myCaptureObject.addInstance(arrayInstance, HeapDumpInstanceObject(myCaptureObject, arrayInstance, mockClassEntry, ARRAY))

    val fields = myCaptureObject.getInstance(arrayInstance).fields
    assertThat(fields).hasSize(3)
    assertThat(fields[0].fieldName).isEqualTo("0")
    assertThat(fields[0].valueType).isEqualTo(OBJECT)
    assertThat(fields[1].fieldName).isEqualTo("1")
    assertThat(fields[1].valueType).isEqualTo(OBJECT)
    assertThat(fields[2].fieldName).isEqualTo("2")
    assertThat(fields[2].valueType).isEqualTo(OBJECT)
  }

  /** Tests that FieldObjects are generated correctly based on a Hprof ClassObj object. */
  @Test
  fun testExtractFieldsWithClassObj() {
    val classInstance = MockClassInstance(-1, 0, MOCK_CLASS)

    val classObj =
      MockClassObj(-1, "testClass", 0).apply {
        addStaticField(Type.OBJECT, "staticObj", classInstance)
        addStaticField(Type.BOOLEAN, "staticBool", true)
        addStaticField(Type.CHAR, "staticChar", 'a')
        addStaticField(Type.FLOAT, "staticFloat", 1f)
        addStaticField(Type.DOUBLE, "staticDouble", 2.0)
        addStaticField(Type.BYTE, "staticByte", 1.toByte())
        addStaticField(Type.SHORT, "staticShort", 3.toShort())
        addStaticField(Type.INT, "staticInt", 4)
        addStaticField(Type.LONG, "staticLong", 5L)
      }

    val mockClassEntry = myCaptureObject.classDb.registerClass(0, MOCK_CLASS)
    myCaptureObject.addInstance(classInstance, HeapDumpInstanceObject(myCaptureObject, classInstance, mockClassEntry, OBJECT))
    myCaptureObject.addInstance(classObj, HeapDumpInstanceObject(myCaptureObject, classObj, mockClassEntry, CLASS))

    val fields = myCaptureObject.getInstance(classObj).fields
    assertThat(fields).hasSize(9)
    assertThat(fields[0].fieldName).isEqualTo("staticObj")
    assertThat(fields[0].valueType).isEqualTo(OBJECT)
    assertThat(fields[1].fieldName).isEqualTo("staticBool")
    assertThat(fields[1].valueType).isEqualTo(BOOLEAN)
    assertThat(fields[2].fieldName).isEqualTo("staticChar")
    assertThat(fields[2].valueType).isEqualTo(CHAR)
    assertThat(fields[3].fieldName).isEqualTo("staticFloat")
    assertThat(fields[3].valueType).isEqualTo(FLOAT)
    assertThat(fields[4].fieldName).isEqualTo("staticDouble")
    assertThat(fields[4].valueType).isEqualTo(DOUBLE)
    assertThat(fields[5].fieldName).isEqualTo("staticByte")
    assertThat(fields[5].valueType).isEqualTo(BYTE)
    assertThat(fields[6].fieldName).isEqualTo("staticShort")
    assertThat(fields[6].valueType).isEqualTo(SHORT)
    assertThat(fields[7].fieldName).isEqualTo("staticInt")
    assertThat(fields[7].valueType).isEqualTo(INT)
    assertThat(fields[8].fieldName).isEqualTo("staticLong")
    assertThat(fields[8].valueType).isEqualTo(LONG)
  }

  /**
   * Tests that ReferenceObjects are generated correctly based on the hard+soft references of a hprof Instance object. Note that as we
   * cannot directly mock the [Instance] object, we use the MockClassInstance class here to allow us to inject the hard + soft references.
   */
  @Test
  fun testExtractReferences() {
    val mockInstance = MockClassInstance(-1, 0, MOCK_CLASS)

    // Set up valid/invalid reference case
    val hardInstanceRef =
      MockClassInstance(-1, 3, MOCK_CLASS).apply {
        addFieldValue(Type.OBJECT, "hardInstanceRef", mockInstance)
        addFieldValue(Type.OBJECT, "invalidRef", Any())
      }

    // Set up multiple case
    val hardArrayRef =
      MockArrayInstance(-1, Type.OBJECT, 3, 2).apply {
        setValue(0, Any())
        setValue(1, mockInstance)
        setValue(2, mockInstance)
      }

    // Set up different type case
    val hardClassRef =
      MockClassObj(-1, "hardClassRef", 1).apply {
        addStaticField(Type.OBJECT, "staticClassRef", mockInstance)
        addStaticField(Type.BOOLEAN, "invalidBoolRef", false)
      }

    // Set up soft references appear at end
    val softInstanceRef =
      MockClassInstance(-1, 0, MOCK_CLASS).apply {
        addFieldValue(Type.OBJECT, "softInstanceRef", mockInstance)
        addFieldValue(Type.OBJECT, "invalidRef", Any())
      }

    // A transient object is not pre-added to the capture's instance map.
    val transientClassRef =
      MockClassObj(-1, "transientClassRef", 4).apply {
        addStaticField(Type.OBJECT, "transientRef", mockInstance)
        setSnapshot(mySnapshot)
      }

    mockInstance.addHardReference(hardInstanceRef)
    mockInstance.addHardReference(hardArrayRef)
    mockInstance.addHardReference(hardClassRef)
    mockInstance.addHardReference(transientClassRef)
    mockInstance.addSoftReferences(softInstanceRef)

    val mockClassEntry = myCaptureObject.classDb.registerClass(0, MOCK_CLASS)
    myCaptureObject.addInstance(
      hardInstanceRef,
      HeapDumpInstanceObject(myCaptureObject, hardInstanceRef, mockClassEntry, OBJECT),
    )
    myCaptureObject.addInstance(
      hardArrayRef,
      HeapDumpInstanceObject(myCaptureObject, hardArrayRef, mockClassEntry, ARRAY),
    )
    myCaptureObject.addInstance(
      hardClassRef,
      HeapDumpInstanceObject(myCaptureObject, hardClassRef, mockClassEntry, CLASS),
    )
    myCaptureObject.addInstance(
      softInstanceRef,
      HeapDumpInstanceObject(myCaptureObject, softInstanceRef, mockClassEntry, OBJECT),
    )
    myCaptureObject.addInstance(
      mockInstance,
      HeapDumpInstanceObject(myCaptureObject, mockInstance, mockClassEntry, OBJECT),
    )

    // extractReference is expected to return a list of sorted hard references first
    // then sorted soft references.
    val referrers = myCaptureObject.getInstance(mockInstance).extractReferences()
    assertThat(referrers).hasSize(5)
    // The first object should refer to the hardClassRef which has the shortest distance to root.
    var refs = referrers[0].referenceFieldNames
    assertThat(refs).hasSize(1)
    assertThat(refs[0]).isEqualTo("staticClassRef")
    // The second object should refer to hardArrayRef with two indices references
    refs = referrers[1].referenceFieldNames
    assertThat(refs).hasSize(2)
    assertThat(refs[0]).isEqualTo("1")
    assertThat(refs[1]).isEqualTo("2")
    // The third object should refer to hardInstanceRef.
    refs = referrers[2].referenceFieldNames
    assertThat(refs).hasSize(1)
    assertThat(refs[0]).isEqualTo("hardInstanceRef")
    // The fourth object should be the transient one, which is added last.
    val transientRef = referrers[3]
    refs = transientRef.referenceFieldNames
    assertThat(refs).hasSize(1)
    assertThat(refs[0]).isEqualTo("transientRef")
    assertThat(transientRef.referenceInstance?.isTransient).isTrue()
    // The fifth object should refer to softInstanceRef
    refs = referrers[4].referenceFieldNames
    assertThat(refs).hasSize(1)
    assertThat(refs[0]).isEqualTo("softInstanceRef")
  }

  private class FakeHeapDumpCaptureObject(
    client: ProfilerClient,
    ideProfilerServices: IdeProfilerServices,
  ) :
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

    fun getInstance(instance: Instance): HeapDumpInstanceObject = myInstanceObjectMap.getValue(instance)

    override fun load(queryRange: Range?, queryJoiner: Executor?): Boolean = true

    override fun findInstanceObject(instance: Instance): InstanceObject? = myInstanceObjectMap[instance]
  }

  companion object {
    private const val MOCK_CLASS = "MockClass"
  }
}

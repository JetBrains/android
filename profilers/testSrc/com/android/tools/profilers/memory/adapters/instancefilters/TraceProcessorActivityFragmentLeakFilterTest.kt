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
package com.android.tools.profilers.memory.adapters.instancefilters

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeTraceProcessorService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.TraceProcessorHeapDumpInstanceObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Tests for [TraceProcessorActivityFragmentLeakFilter] validating Activity and Fragment leak detection in Trace Processor heap dumps. */
class TraceProcessorActivityFragmentLeakFilterTest {

  /** Tests Activity leak identification based on mFinished/mDestroyed boolean fields and instance depth. */
  @Test
  fun testActivityLeaks() {
    val baseClassActivityId = 1L
    val subClassActivityId = 2L
    val unrelatedClassId = 3L

    val capture = FakeCaptureObject.Builder().build()

    val leakingBaseClassActivity =
      FakeInstanceObject.Builder(capture, baseClassActivityId, ActivityFragmentLeakInstanceFilter.ACTIVTY_CLASS_NAME)
        .setValueType(ValueObject.ValueType.OBJECT)
        .addField(ActivityFragmentLeakInstanceFilter.FINISHED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .setDepth(1)
        .build()

    val nonLeakingBaseClassActivity =
      FakeInstanceObject.Builder(capture, baseClassActivityId, ActivityFragmentLeakInstanceFilter.ACTIVTY_CLASS_NAME)
        .setValueType(ValueObject.ValueType.OBJECT)
        .addField("blah", ValueObject.ValueType.BOOLEAN, true)
        .build()

    val leakingSubClassActivity =
      FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setValueType(ValueObject.ValueType.OBJECT)
        .setSuperClassId(baseClassActivityId)
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .setDepth(1)
        .build()

    val nonLeakingSubClassActivity1 =
      FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setValueType(ValueObject.ValueType.OBJECT)
        .setSuperClassId(baseClassActivityId)
        .addField("blah", ValueObject.ValueType.BOOLEAN, true)
        .build()

    val nonLeakingSubClassActivity2 =
      FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setValueType(ValueObject.ValueType.OBJECT)
        .setSuperClassId(baseClassActivityId)
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .setDepth(Int.MAX_VALUE)
        .build()

    val unrelatedClassInstance =
      FakeInstanceObject.Builder(capture, unrelatedClassId, "my.other.class")
        .setValueType(ValueObject.ValueType.OBJECT)
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .build()

    val instances: Set<InstanceObject> =
      ImmutableSet.of(
        leakingBaseClassActivity,
        nonLeakingBaseClassActivity,
        leakingSubClassActivity,
        nonLeakingSubClassActivity1,
        nonLeakingSubClassActivity2,
        unrelatedClassInstance,
      )

    val filter = TraceProcessorActivityFragmentLeakFilter(capture.classDatabase, capture)
    val result = filter.filter(instances)
    assertThat(result).containsExactly(leakingBaseClassActivity, leakingSubClassActivity)
  }

  /** Tests Fragment leak identification when mFragmentManager is null or omitted in Perfetto traces. */
  @Test
  fun testFragmentLeaks() {
    val baseClassNativeFragmentId = 1L

    val capture = FakeCaptureObject.Builder().build()

    val leakingFragment =
      FakeInstanceObject.Builder(capture, baseClassNativeFragmentId, ActivityFragmentLeakInstanceFilter.NATIVE_FRAGMENT_CLASS_NAME)
        .setValueType(ValueObject.ValueType.OBJECT)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, ValueObject.ValueType.OBJECT, null)
        .setDepth(1)
        .build()

    val omittedFieldLeakingFragment =
      FakeInstanceObject.Builder(capture, baseClassNativeFragmentId + 1, ActivityFragmentLeakInstanceFilter.NATIVE_FRAGMENT_CLASS_NAME)
        .setValueType(ValueObject.ValueType.OBJECT)
        .setDepth(1)
        .build()

    val nonLeakingFragment =
      FakeInstanceObject.Builder(capture, baseClassNativeFragmentId + 2, ActivityFragmentLeakInstanceFilter.NATIVE_FRAGMENT_CLASS_NAME)
        .setValueType(ValueObject.ValueType.OBJECT)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, ValueObject.ValueType.OBJECT, Object())
        .setDepth(1)
        .build()

    val instances: Set<InstanceObject> = ImmutableSet.of(leakingFragment, omittedFieldLeakingFragment, nonLeakingFragment)

    val filter = TraceProcessorActivityFragmentLeakFilter(capture.classDatabase, capture)
    val result = filter.filter(instances)
    assertThat(result).containsExactly(leakingFragment, omittedFieldLeakingFragment)
  }

  /** Tests gRPC bulk prefetching of primitive fields during leak filter evaluation. */
  @Test
  fun testTraceProcessorHeapDumpInstanceWithGrpcResponse() {
    val activityClassId = 100L
    val instanceId = 101L

    val field =
      TraceProcessor.GetPrimitiveFieldsResult.PrimitiveField.newBuilder()
        .setName("mDestroyed")
        .setTypeName("boolean")
        .setValue("true")
        .build()
    val protoInst =
      TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder().setInstanceId(instanceId).addField(field).build()
    val primitiveFieldsResult = TraceProcessor.GetPrimitiveFieldsResult.newBuilder().addInstances(protoInst).build()

    val traceProcessorService = FakeTraceProcessorService().apply { this.primitiveFieldsResult = primitiveFieldsResult }

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

    val activityClassEntry = capture.classDb.registerClass(activityClassId, -1L, ActivityFragmentLeakInstanceFilter.ACTIVTY_CLASS_NAME, -1L)
    val instanceData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(instanceId).setTypeId(activityClassId).setDepth(1).build()

    val tpInstance =
      TraceProcessorHeapDumpInstanceObject(
        activityClassEntry,
        instanceData,
        ValueObject.ValueType.OBJECT,
        capture,
        emptyList(),
        emptyList(),
      )

    val filter = TraceProcessorActivityFragmentLeakFilter(capture.classDb, capture)
    val result = filter.filter(setOf(tpInstance))

    assertThat(result).containsExactly(tpInstance)
  }
}

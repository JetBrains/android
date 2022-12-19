/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NativeAllocationSampleCaptureObjectTest {

  private val timer = FakeTimer()
  private val service = FakeMemoryService()
  private val transportService = FakeTransportService(
    timer)

  private val ideProfilerServices = FakeIdeProfilerServices()

  @Rule
  @JvmField
  var grpcChannel = FakeGrpcChannel("NativeAllocationSampleCaptureObjectTest", transportService, service)

  private var stage: MainMemoryProfilerStage? = null

  @Before
  fun setUp() {
    stage = MainMemoryProfilerStage(
      StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer),
      FakeCaptureObjectLoader())
  }

  @Test
  @Throws(Exception::class)
  fun testAccessors() {
    val startTimeNs: Long = 3
    val endTimeNs: Long = 8
    val info = Memory.MemoryTraceInfo.newBuilder().setFromTimestamp(startTimeNs).setToTimestamp(endTimeNs).build()
    val capture = NativeAllocationSampleCaptureObject(ProfilerClient(grpcChannel.channel), ProfilersTestData.SESSION_DATA, info, stage!!)
    // Verify values associated with the MemoryTraceInfo object.
    assertThat(startTimeNs).isEqualTo(capture.startTimeNs)
    assertThat(endTimeNs).isEqualTo(capture.endTimeNs)
    assertThat(capture.isDoneLoading).isFalse()
    assertThat(capture.isError).isFalse()
  }

  @Test
  @Throws(Exception::class)
  fun testDefaultShows() {
    val startTimeNs: Long = 3
    val endTimeNs: Long = 8
    val info = Memory.MemoryTraceInfo.newBuilder().setFromTimestamp(startTimeNs).setToTimestamp(endTimeNs).build()
    val capture = NativeAllocationSampleCaptureObject(ProfilerClient(grpcChannel.channel), ProfilersTestData.SESSION_DATA, info, stage!!)
    transportService.addFile(java.lang.Long.toString(startTimeNs), ByteString.copyFrom("TODO".toByteArray()))
    assertThat(capture.load(null, null)).isTrue()
    assertThat(capture.isDoneLoading).isTrue()
    assertThat(capture.isError).isFalse()
  }

  @Test
  @Throws(Exception::class)
  fun noContentFailsToLoad() {
    val startTimeNs: Long = 3
    val endTimeNs: Long = 8
    val info = Memory.MemoryTraceInfo.newBuilder().setFromTimestamp(startTimeNs).setToTimestamp(endTimeNs).build()
    val capture = NativeAllocationSampleCaptureObject(ProfilerClient(grpcChannel.channel), ProfilersTestData.SESSION_DATA, info, stage!!)
    assertThat(capture.load(null, null)).isFalse()
    assertThat(capture.isError).isTrue()
  }
}
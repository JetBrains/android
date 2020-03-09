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
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.MemoryNativeSampleData
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HeapProfdSessionArtifactTest {

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  var grpcChannel = FakeGrpcChannel(
    "HeapProfdSessionArtifactTestChannel",
    transportService,
    FakeProfilerService(timer),
    FakeMemoryService(),
    FakeCpuService(),
    FakeEventService(),
    FakeNetworkService.newBuilder().build()
  )

  private lateinit var profilers: StudioProfilers

  @Before
  fun setup() {
    profilers = StudioProfilers(
      ProfilerClient(grpcChannel.name),
      FakeIdeProfilerServices(),
      FakeTimer()
    )
  }

  @Test
  fun testGetSessionArtifacts() {
    val nativeHeapTimestamp = 30L
    val nativeHeapInfo = MemoryNativeSampleData.newBuilder().setStartTime(
      nativeHeapTimestamp).setEndTime(nativeHeapTimestamp + 1).build()
    val nativeHeapData = ProfilersTestData.generateMemoryNativeSampleData(
      nativeHeapTimestamp, nativeHeapTimestamp + 1, nativeHeapInfo)
      .setPid(ProfilersTestData.SESSION_DATA.pid).build()
    transportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId, nativeHeapData)
    val artifacts = HeapProfdSessionArtifact.getSessionArtifacts(profilers, ProfilersTestData.SESSION_DATA,
                                                                 Common.SessionMetaData.getDefaultInstance())
    assertThat(artifacts).hasSize(1)
    assertThat(artifacts[0].name).isEqualTo("Native Sampled")
    assertThat(artifacts[0].isOngoing).isFalse()
    assertThat(artifacts[0].session).isEqualTo(ProfilersTestData.SESSION_DATA)
  }
}
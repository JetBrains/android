/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.*
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class AllocationSessionArtifactTest {

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("AllocationSessionArtifactTestChannel", transportService, FakeMemoryService())

  private lateinit var profilers: StudioProfilers

  @Before
  fun setup() {
    profilers = StudioProfilers(
      ProfilerClient(grpcChannel.channel),
      FakeIdeProfilerServices().apply { enableEventsPipeline(true) },
      FakeTimer()
    )
  }

  @Test
  fun `artifact loaded for finished allocation session`() {
    val session = with(Common.Session.newBuilder()) {
      sessionId = ProfilersTestData.SESSION_DATA.sessionId
      streamId = ProfilersTestData.SESSION_DATA.streamId
      pid = ProfilersTestData.SESSION_DATA.pid
      startTimestamp = 0
      endTimestamp = TIMESTAMP3
      build()
    }

    val info = Memory.AllocationsInfo.newBuilder().setStartTime(TIMESTAMP1).setSuccess(true).setLegacy(true)
    transportService.addEventToStream(session.streamId,
                                      ProfilersTestData.generateMemoryAllocationInfoData(TIMESTAMP1, session.pid, info.setEndTime(
                                        Long.MAX_VALUE).build()).setIsEnded(false).build())
    transportService.addEventToStream(session.streamId,
                                      ProfilersTestData.generateMemoryAllocationInfoData(TIMESTAMP1, session.pid, info.setEndTime(
                                        TIMESTAMP2).build()).setIsEnded(true).build())
    val artifacts = AllocationSessionArtifact.getSessionArtifacts(profilers, session, Common.SessionMetaData.getDefaultInstance())
    assertThat(artifacts.size).isEqualTo(1)
    assertThat(artifacts[0]).isInstanceOf(LegacyAllocationsSessionArtifact::class.java)

    with(artifacts[0] as LegacyAllocationsSessionArtifact) {
      assertThat(timestampNs).isEqualTo(TIMESTAMP1)
      assertThat(this.session).isEqualTo(session)
      assertThat(name).isEqualTo("Allocation Records")
      assertThat(isOngoing).isFalse()
      assertThat(subtitle).isNotEmpty()
      assertThat(canExport).isTrue()
      assertThat(this.profilers).isSameAs(profilers)
      assertThat(artifactProto).isNotNull()
    }
  }

  @Test
  fun `artifact loaded for ongoing allocation session`() {
    val session = with(Common.Session.newBuilder()) {
      sessionId = ProfilersTestData.SESSION_DATA.sessionId
      streamId = ProfilersTestData.SESSION_DATA.streamId
      pid = ProfilersTestData.SESSION_DATA.pid
      startTimestamp = 0
      endTimestamp = Long.MAX_VALUE
      build()
    }

    val info = Memory.AllocationsInfo.newBuilder().setStartTime(TIMESTAMP1).setEndTime(Long.MAX_VALUE).setLegacy(false)
    transportService.addEventToStream(session.streamId,
                                      ProfilersTestData.generateMemoryAllocationInfoData(TIMESTAMP1, session.pid, info.build()).setIsEnded(false).build())
    val artifacts = AllocationSessionArtifact.getSessionArtifacts(profilers, session, Common.SessionMetaData.getDefaultInstance())
    assertThat(artifacts.size).isEqualTo(1)
    assertThat(artifacts[0]).isInstanceOf(AllocationSessionArtifact::class.java)

    with(artifacts[0] as AllocationSessionArtifact) {
      assertThat(startUs).isEqualTo(TimeUnit.NANOSECONDS.toMicros(TIMESTAMP1).toDouble())
      assertThat(endUs).isEqualTo(TimeUnit.NANOSECONDS.toMicros(Long.MAX_VALUE).toDouble())
      assertThat(this.session).isEqualTo(session)
      assertThat(name).isEqualTo("Allocation Records")
      assertThat(isOngoing).isTrue()
      assertThat(subtitle).isNotEmpty()
      assertThat(this.profilers).isSameAs(profilers)
      assertThat(artifactProto).isNotNull()
    }
  }

  companion object {
    const val TIMESTAMP1 = 1000L
    const val TIMESTAMP2 = 2000L
    const val TIMESTAMP3 = 3000L
    val SAMPLING_FULL = MemoryAllocSamplingData.newBuilder().apply { samplingNumInterval = FULL.value }.build()
    val SAMPLING_SAMPLED = MemoryAllocSamplingData.newBuilder().apply { samplingNumInterval = SAMPLED.value }.build()
    val SAMPLING_NONE = MemoryAllocSamplingData.newBuilder().apply { samplingNumInterval = NONE.value }.build()
  }
}
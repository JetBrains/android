/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.FakeTransportService
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profilers.sessions.SessionArtifact
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class HprofSessionArtifactTest {

  private val timer = FakeTimer()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "SessionsManagerTestChannel",
    FakeTransportService(timer, false),
    FakeProfilerService(timer),
    FakeMemoryService(),
    FakeCpuService(),
    FakeEventService(),
    FakeNetworkService.newBuilder().build()
  )

  private lateinit var myProfilers: StudioProfilers

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
      myGrpcChannel.client,
      FakeIdeProfilerServices(),
      FakeTimer()
    )
  }

  @Test
  fun testOngoingCapture() {
    val ongoingInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(Long.MAX_VALUE).build()
    val ongoingArtifact = HprofSessionArtifact(myProfilers,
                                               Common.Session.getDefaultInstance(),
                                               Common.SessionMetaData.getDefaultInstance(),
                                               ongoingInfo)
    assertThat(ongoingArtifact.isOngoing).isTrue()

    val finishedInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(2).build()
    val finishedArtifact = HprofSessionArtifact(myProfilers,
                                                Common.Session.getDefaultInstance(),
                                                Common.SessionMetaData.getDefaultInstance(),
                                                finishedInfo)
    assertThat(finishedArtifact.isOngoing).isFalse()
  }

  @Test
  fun testSubtitle() {
    val ongoingInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(Long.MAX_VALUE).build()
    val finishedInfo = MemoryProfiler.HeapDumpInfo.newBuilder()
      .setStartTime(TimeUnit.SECONDS.toNanos(5)).setEndTime(TimeUnit.SECONDS.toNanos(10)).build()

    val ongoingCaptureArtifact = HprofSessionArtifact(myProfilers,
                                                      Common.Session.getDefaultInstance(),
                                                      Common.SessionMetaData.getDefaultInstance(),
                                                      ongoingInfo)
    assertThat(ongoingCaptureArtifact.subtitle).isEqualTo(SessionArtifact.CAPTURING_SUBTITLE)

    val finishedCaptureArtifact = HprofSessionArtifact(myProfilers,
                                                       Common.Session.getDefaultInstance(),
                                                       Common.SessionMetaData.getDefaultInstance(),
                                                       finishedInfo)
    assertThat(finishedCaptureArtifact.subtitle).isEqualTo("00:00:05.000")
  }
}
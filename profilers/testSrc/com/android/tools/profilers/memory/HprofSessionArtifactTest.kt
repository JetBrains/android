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
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profilers.sessions.SessionArtifact
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

class HprofSessionArtifactTest {

  private val myProfilerService = FakeProfilerService(false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "SessionsManagerTestChannel",
    myProfilerService,
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
    assertThat(ongoingArtifact.isOngoingCapture).isTrue()

    val finishedInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(2).build()
    val finishedArtifact = HprofSessionArtifact(myProfilers,
                                                Common.Session.getDefaultInstance(),
                                                Common.SessionMetaData.getDefaultInstance(),
                                                finishedInfo)
    assertThat(finishedArtifact.isOngoingCapture).isFalse()
  }

  @Test
  fun testSubtitle() {
    val ongoingInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(Long.MAX_VALUE).build()
    val finishedInfo = MemoryProfiler.HeapDumpInfo.newBuilder()
      .setStartTime(TimeUnit.SECONDS.toNanos(5)).setEndTime(TimeUnit.SECONDS.toNanos(10)).build()

    // Date takes in a year value relative to 1900. So a input of 100 should give year 2000.
    val fakeDateNs = TimeUnit.MILLISECONDS.toNanos(Date(100, 0, 2, 3, 4).time)
    // This is an invalid case, but we test to make sure the ongoing state of an imported capture is ignored.
    val ongoingButImportedCaptureArtifact = HprofSessionArtifact(myProfilers,
                                                                 Common.Session.newBuilder().setStartTimestamp(fakeDateNs).build(),
                                                                 Common.SessionMetaData.newBuilder().setType(
                                                                   Common.SessionMetaData.SessionType.MEMORY_CAPTURE).build(),
                                                                 ongoingInfo)
    assertThat(ongoingButImportedCaptureArtifact.subtitle).isEqualTo("01/02/2000, 03:04 AM")

    val ongoingCaptureArtifact = HprofSessionArtifact(myProfilers,
                                                      Common.Session.newBuilder().setStartTimestamp(fakeDateNs).build(),
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
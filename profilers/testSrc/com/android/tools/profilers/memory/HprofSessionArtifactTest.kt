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
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.CaptureDataSeries.ofHeapDumpSamples
import com.android.tools.profilers.sessions.SessionArtifact
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit


class HprofSessionArtifactTest {

  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "SessionsManagerTestChannel",
    transportService,
    FakeProfilerService(timer),
    FakeMemoryService(),
    FakeCpuService(),
    FakeEventService()
  )

  private lateinit var myProfilers: StudioProfilers

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      services,
      FakeTimer()
    )
  }

  @Test
  fun testOngoingCapture() {
    val ongoingInfo = HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(Long.MAX_VALUE).build()
    val ongoingArtifact = HprofSessionArtifact(myProfilers,
                                               Common.Session.getDefaultInstance(),
                                               Common.SessionMetaData.getDefaultInstance(),
                                               ongoingInfo)
    assertThat(ongoingArtifact.isOngoing).isTrue()

    val finishedInfo = HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(2).build()
    val finishedArtifact = HprofSessionArtifact(myProfilers,
                                                Common.Session.getDefaultInstance(),
                                                Common.SessionMetaData.getDefaultInstance(),
                                                finishedInfo)
    assertThat(finishedArtifact.isOngoing).isFalse()
  }

  @Test
  fun testSubtitle() {
    val ongoingInfo = HeapDumpInfo.newBuilder().setStartTime(1).setEndTime(Long.MAX_VALUE).build()
    val finishedInfo = HeapDumpInfo.newBuilder()
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

  @Test
  fun `selecting the same heap dump does not reload`() {
    services.enableEventsPipeline(true)
    fun info(setup: HeapDumpInfo.Builder.() -> Unit) = HeapDumpInfo.newBuilder().apply(setup).build()
    fun artifact(info: HeapDumpInfo) = HprofSessionArtifact(myProfilers,
                                                            Common.Session.getDefaultInstance(),
                                                            Common.SessionMetaData.getDefaultInstance(),
                                                            info)
    val info0 = info { startTime = 1; endTime = 4 }
    val info1 = info { startTime = 5; endTime = 10 }
    val artifact0 = artifact(info0)
    val artifact1 = artifact(info1)

    // load heap dump for info0
    MainMemoryProfilerStage(myProfilers, FakeCaptureObjectLoader()).let { stage ->
      transportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
                                        ProfilersTestData.generateMemoryHeapDumpData(info0.startTime, info0.startTime, info0)
                                          .setPid(ProfilersTestData.SESSION_DATA.pid)
                                          .build())
      val series = ofHeapDumpSamples(ProfilerClient(myGrpcChannel.channel), ProfilersTestData.SESSION_DATA, services.featureTracker, stage)
      stage.selectCaptureDuration(series.getDataForRange(Range(0.0, Double.MAX_VALUE)).first().value, null)
    }

    myProfilers.stage.let {
      artifact0.onSelect()
      assertThat(myProfilers.stage).isSameAs(it)
      artifact1.onSelect()
      assertThat(myProfilers.stage).isNotSameAs(it)
      artifact0.onSelect()
      assertThat(myProfilers.stage).isNotSameAs(it)
    }
  }
}
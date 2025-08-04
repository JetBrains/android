/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.cpu.CpuCaptureStage
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.sessions.SessionsManagerTest
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.FileInputStream
import java.util.Arrays

@RunWith(Parameterized::class)
class ImportedSessionTests(
  private val isTaskBasedUxEnabled: Boolean,
  private val sessionType: Common.SessionData.SessionStarted.SessionType
) {

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("ImportedSessionTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var myObserver: SessionsManagerTest.SessionsAspectObserver
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myObserver = SessionsManagerTest.SessionsAspectObserver()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    myManager.addDependency(myObserver)
      .onChange(SessionAspect.SELECTED_SESSION) { myObserver.selectedSessionChanged() }
      .onChange(SessionAspect.PROFILING_SESSION) { myObserver.profilingSessionChanged() }
      .onChange(SessionAspect.SESSIONS) { myObserver.sessionsChanged() }

    Truth.assertThat(myManager.sessionArtifacts).isEmpty()
    Truth.assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    Truth.assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  companion object {
    @JvmStatic
    @Parameters
    fun data(): Collection<Array<Any>> {
      return listOf(
        arrayOf(true, Common.SessionData.SessionStarted.SessionType.CPU_CAPTURE),
        arrayOf(false, Common.SessionData.SessionStarted.SessionType.CPU_CAPTURE),
        arrayOf(true, Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE),
        arrayOf(false, Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE),
      )
    }
  }

  @Test
  fun testImportSessionListenerBehavesCorrectly(
  ) {
    ideProfilerServices.enableTaskBasedUx(isTaskBasedUxEnabled)

    val trace = CpuProfilerTestUtils.getTraceFile("art_streaming.trace")
    val traceBytes = ByteString.readFrom(FileInputStream(trace))
    myTransportService.addFile("1", traceBytes)

    val sessionTimestamp = 1L
    val sessionStartedEvent = ProfilersTestData.generateSessionStartEvent(1, 1, sessionTimestamp, sessionType, 1).build()
    myTransportService.addEventToStream(1, sessionStartedEvent)
    val sessionEndedEvent = ProfilersTestData.generateSessionEndEvent(1, 1, sessionTimestamp).build()
    myTransportService.addEventToStream(1, sessionEndedEvent)

    myManager.update()

    Truth.assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    val cpuTraceSessionItem = myManager.sessionArtifacts[0] as SessionItem

    if (isTaskBasedUxEnabled && sessionType == Common.SessionData.SessionStarted.SessionType.CPU_CAPTURE) {
      Truth.assertThat(cpuTraceSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.CPU_CAPTURE)
      // The CPU_CAPTURE session change listener triggers a setting of the CpuCaptureStage. Thus, we test to make sure it did not enter
      // that stage.
      Truth.assertThat(myProfilers.stage).isNotInstanceOf(CpuCaptureStage::class.java)
    } else if (!isTaskBasedUxEnabled && sessionType == Common.SessionData.SessionStarted.SessionType.CPU_CAPTURE) {
      Truth.assertThat(cpuTraceSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.CPU_CAPTURE)
      // The CPU_CAPTURE session change listener triggers a setting of the CpuCaptureStage. Thus, we test to make sure it did enter
      // that stage.
      Truth.assertThat(myProfilers.stage).isInstanceOf(CpuCaptureStage::class.java)
    } else if (isTaskBasedUxEnabled && sessionType == Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE) {
      Truth.assertThat(cpuTraceSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
      // The MEMORY_CAPTURE session change listener triggers a setting of the MainMemoryProfilerStage. Thus, we test to make sure it did not
      // enter that stage.
      Truth.assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)
    } else if (!isTaskBasedUxEnabled && sessionType == Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE) {
      Truth.assertThat(cpuTraceSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
      // The MEMORY_CAPTURE session change listener triggers a setting of the MainMemoryProfilerStage. Thus, we test to make sure it did
      // enter that stage.
      Truth.assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
    }
  }
}
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
package com.android.tools.profilers.tasks.taskhandlers

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.SupportLevel
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.memory.AllocationSessionArtifact
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth

object TaskHandlerTestUtils {

  fun createCpuCaptureSessionArtifactWithConfig(profilers: StudioProfilers,
                                                session: Common.Session,
                                                sessionId: Long,
                                                traceId: Long,
                                                config: Trace.TraceConfiguration) = createCpuCaptureSessionArtifactWithConfig(profilers,
                                                                                                                              session,
                                                                                                                              sessionId,
                                                                                                                              traceId, 0, 0,
                                                                                                                              config)

  /**
   * Overload of createCpuCaptureSessionArtifactWithConfig that takes in from and end timestamps.
   */
  fun createCpuCaptureSessionArtifactWithConfig(profilers: StudioProfilers,
                                                session: Common.Session,
                                                sessionId: Long,
                                                traceId: Long,
                                                fromTimestamp: Long,
                                                toTimestamp: Long,
                                                config: Trace.TraceConfiguration): CpuCaptureSessionArtifact {
    val sessionMetadata = Common.SessionMetaData.newBuilder().setSessionId(sessionId).build()
    val info = Trace.TraceInfo.newBuilder().setFromTimestamp(fromTimestamp).setToTimestamp(toTimestamp).setTraceId(
      traceId).setConfiguration(config.toBuilder()).build()
    return CpuCaptureSessionArtifact(profilers, session, sessionMetadata, info)
  }

  fun createCpuCaptureSessionArtifact(profilers: StudioProfilers,
                                      session: Common.Session,
                                      sessionId: Long,
                                      traceId: Long) = createCpuCaptureSessionArtifactWithConfig(profilers, session, sessionId, traceId,
                                                                                                 Trace.TraceConfiguration.getDefaultInstance())

  fun createHprofSessionArtifact(profilers: StudioProfilers, session: Common.Session,
                                 startTimestamp: Long,
                                 endTimestamp: Long): HprofSessionArtifact {
    val sessionMetadata = Common.SessionMetaData.getDefaultInstance()
    val info = Memory.HeapDumpInfo.newBuilder().setStartTime(startTimestamp).setEndTime(endTimestamp).build()
    return HprofSessionArtifact(profilers, session, sessionMetadata, info)
  }

  fun createHeapProfdSessionArtifact(profilers: StudioProfilers, session: Common.Session,
                                     fromTimestamp: Long,
                                     toTimestamp: Long): HeapProfdSessionArtifact {
    val sessionMetadata = Common.SessionMetaData.getDefaultInstance()
    val info = Trace.TraceInfo.newBuilder().setFromTimestamp(fromTimestamp).setToTimestamp(toTimestamp).build()
    return HeapProfdSessionArtifact(profilers, session, sessionMetadata, info)
  }

  fun createAllocationSessionArtifact(profilers: StudioProfilers, session: Common.Session,
                                      startTimestamp: Long,
                                      endTimestamp: Long): AllocationSessionArtifact {
    val sessionMetadata = Common.SessionMetaData.getDefaultInstance()
    val info = Memory.AllocationsInfo.newBuilder().setStartTime(startTimestamp).setEndTime(endTimestamp).build()
    return AllocationSessionArtifact(profilers, session, sessionMetadata, info, startTimestamp.toDouble(), endTimestamp.toDouble())
  }

  fun createLegacyAllocationsSessionArtifact(profilers: StudioProfilers, session: Common.Session,
                                      startTimestamp: Long,
                                      endTimestamp: Long): LegacyAllocationsSessionArtifact {
    val sessionMetadata = Common.SessionMetaData.getDefaultInstance()
    val info = Memory.AllocationsInfo.newBuilder().setStartTime(startTimestamp).setEndTime(endTimestamp).build()
    return LegacyAllocationsSessionArtifact(profilers, session, sessionMetadata, info)
  }

  fun createSessionItem(profilers: StudioProfilers,
                        initialSession: Common.Session,
                        sessionId: Long,
                        childArtifacts: List<SessionArtifact<*>>): SessionItem {
    val sessionMetadata = Common.SessionMetaData.newBuilder().setSessionId(sessionId).build()
    return SessionItem(profilers, initialSession, sessionMetadata).apply {
      setChildArtifacts(childArtifacts)
    }
  }

  fun startSession(exposureLevel: Common.Process.ExposureLevel,
                   profilers: StudioProfilers,
                   transportService: FakeTransportService,
                   timer: FakeTimer,
                   taskType: Common.ProfilerTaskType) {
    // The following creates and starts a fake debuggable session so that features that requires a debuggable process are supported such as
    // heap dump and java/kotlin allocations.
    profilers.setPreferredProcess(null, FakeTransportService.FAKE_PROCESS.name, null)
    // To support the Native Allocation tracing feature, the feature level of the device must be >= Q.
    val device = FakeTransportService.FAKE_DEVICE.toBuilder().setFeatureLevel(AndroidVersion.VersionCodes.Q).build()
    transportService.addDevice(device)
    val debuggableEvent = FakeTransportService.FAKE_PROCESS.toBuilder()
      .setStartTimestampNs(5)
      .setExposureLevel(exposureLevel)
      .build()
    transportService.addProcess(device, debuggableEvent)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS) // Wait for the session to auto start and select.
    profilers.setProcess(device, null, taskType) // Will start a new session on the preferred process
    timer.tick(FakeTimer.ONE_SECOND_IN_NS) // Wait for the session to auto start and select.
    Truth.assertThat(profilers.session.pid).isEqualTo(FakeTransportService.FAKE_PROCESS.pid)
    Truth.assertThat(
      profilers.selectedSessionSupportLevel == SupportLevel.DEBUGGABLE ||
      profilers.selectedSessionSupportLevel == SupportLevel.PROFILEABLE).isTrue()
    if (exposureLevel == Common.Process.ExposureLevel.DEBUGGABLE) {
      Truth.assertThat(profilers.selectedSessionSupportLevel).isEqualTo(SupportLevel.DEBUGGABLE)
    }
    else if (exposureLevel == Common.Process.ExposureLevel.PROFILEABLE) {
      Truth.assertThat(profilers.selectedSessionSupportLevel).isEqualTo(SupportLevel.PROFILEABLE)
    }
  }

  private fun stopSession(sessionsManager: SessionsManager, timer: FakeTimer) {
    sessionsManager.endCurrentSession()
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  fun startAndStopSession(exposureLevel: Common.Process.ExposureLevel,
                          profilers: StudioProfilers,
                          sessionsManager: SessionsManager,
                          transportService: FakeTransportService,
                          timer: FakeTimer,
                          taskType: Common.ProfilerTaskType) {
    startSession(exposureLevel, profilers, transportService, timer, taskType)
    Truth.assertThat(sessionsManager.isSessionAlive)
    stopSession(sessionsManager, timer)
    Truth.assertThat(!sessionsManager.isSessionAlive)
  }
}
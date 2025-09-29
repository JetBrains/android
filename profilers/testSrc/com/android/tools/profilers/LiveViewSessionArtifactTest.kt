/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LiveViewSessionArtifactTest {

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  private val session = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(Long.MAX_VALUE).build()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("LiveViewSessionArtifactTestChannel", transportService)

  private lateinit var profilers: StudioProfilers

  @Before
  fun setup() {
    profilers = StudioProfilers(
      ProfilerClient(grpcChannel.channel),
      FakeIdeProfilerServices(),
      FakeTimer()
    )
  }

  @Test
  fun `artifact created for full session with live view event`() {
    addLiveViewEvent()
    val meta = createMetadata(Common.SessionMetaData.SessionType.FULL)
    val artifacts = LiveViewSessionArtifact.getSessionArtifacts(profilers, session, meta)
    assertThat(artifacts).hasSize(1)
    assertThat(artifacts[0]).isInstanceOf(LiveViewSessionArtifact::class.java)

    with(artifacts[0] as LiveViewSessionArtifact) {
      assertThat(name).isEqualTo("Live View")
      assertThat(isOngoing).isTrue()
      assertThat(canExport).isFalse()
    }
  }

  @Test
  fun `artifact not created for session without live view event`() {
    val meta = createMetadata(Common.SessionMetaData.SessionType.FULL)
    // No LIVE_VIEW_STATUS event is added, so no artifact should be created.
    val artifacts = LiveViewSessionArtifact.getSessionArtifacts(profilers, session, meta)
    assertThat(artifacts).isEmpty()
  }

  @Test
  fun `artifact not created for non-full session type`() {
    addLiveViewEvent()
    val meta = createMetadata(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
    val artifacts = LiveViewSessionArtifact.getSessionArtifacts(profilers, session, meta)
    assertThat(artifacts).isEmpty()
  }

  private fun addLiveViewEvent() {
    val liveViewEvent = Common.Event.newBuilder().setKind(Common.Event.Kind.LIVE_VIEW_STATUS).setGroupId(session.sessionId).build()
    transportService.addEventToStream(session.streamId, liveViewEvent)
  }

  private fun createMetadata(type: Common.SessionMetaData.SessionType) =
    Common.SessionMetaData.newBuilder().setType(type).build()
}
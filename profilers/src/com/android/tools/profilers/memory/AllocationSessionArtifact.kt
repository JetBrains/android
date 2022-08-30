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

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.MemoryProfiler.Companion.getAllocationInfosForSession
import com.android.tools.profilers.sessions.SessionArtifact
import java.util.concurrent.TimeUnit

class AllocationSessionArtifact(override val profilers: StudioProfilers,
                                override val session: Common.Session,
                                override val sessionMetaData: Common.SessionMetaData,
                                private val info: Memory.AllocationsInfo,
                                val startUs: Double,
                                val endUs: Double)
  : SessionArtifact<Memory.AllocationsInfo> {

  val subtitle: String get() = TimeFormatter.getFullClockString(timestampNs.nanosToMicros())

  override val artifactProto = info

  override val name = "Allocation Records"

  override val timestampNs
    get() = info.startTime - session.startTimestamp

  override val isOngoing
    get() = info.endTime == Long.MAX_VALUE

  override val canExport = false

  override fun onSelect() {
    if (session !== profilers.session)
      profilers.sessionsManager.setSession(session)
    profilers.stage = AllocationStage.makeStaticStage(profilers, minTrackingTimeUs = startUs, maxTrackingTimeUs = endUs)
  }

  companion object {
    @JvmStatic
    fun getSessionArtifacts(profilers: StudioProfilers,
                            session: Common.Session,
                            sessionMetadata: Common.SessionMetaData): List<SessionArtifact<*>> {
      val rangeUs = Range(session.startTimestamp.nanosToMicros().toDouble(), session.endTimestamp.nanosToMicros().toDouble())
      return getAllocationInfosForSession(profilers.client, session, rangeUs, profilers.ideServices).mapNotNull { info ->
        if (info.legacy) {
          LegacyAllocationsSessionArtifact(profilers, session, sessionMetadata, info)
        }
        else {
          AllocationSessionArtifact(profilers, session, sessionMetadata, info, info.startTime.nanosToMicros().toDouble(),
                                    info.endTime.nanosToMicros().toDouble())
        }
      }
    }
  }
}

private fun Long.nanosToMicros() = TimeUnit.NANOSECONDS.toMicros(this)
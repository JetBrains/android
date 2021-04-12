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
import com.android.tools.profilers.memory.AllocationDurationData.Companion.consecutiveAllocRanges
import com.android.tools.profilers.sessions.SessionArtifact
import java.util.concurrent.TimeUnit

class AllocationSessionArtifact(private val profilers: StudioProfilers,
                                private val session: Common.Session,
                                private val sessionMetadata: Common.SessionMetaData,
                                private val info: Memory.AllocationsInfo,
                                val startUs: Double,
                                val endUs: Double)
  : SessionArtifact<Memory.AllocationsInfo> {

  val subtitle: String get() = TimeFormatter.getFullClockString(timestampNs.nanosToMicros())

  override fun getProfilers() = profilers
  override fun getSession() = session
  override fun getArtifactProto() = info
  override fun getSessionMetaData() = sessionMetadata
  override fun getName() = "Allocation Records"
  override fun getTimestampNs() = TimeUnit.MICROSECONDS.toNanos(startUs.toLong()) - session.startTimestamp
  override fun isOngoing() = false
  override fun onSelect() {
    if (session !== profilers.session)
      profilers.sessionsManager.setSession(session)
    profilers.stage = AllocationStage.makeStaticStage(profilers, minTrackingTimeUs = startUs, maxTrackingTimeUs = endUs)
  }

  override fun canExport() = false

  companion object {
    @JvmStatic
    fun getSessionArtifacts(profilers: StudioProfilers,
                            session: Common.Session,
                            sessionMetadata: Common.SessionMetaData) : List<SessionArtifact<*>> {
      val rangeUs = Range(session.startTimestamp.nanosToMicros().toDouble(), session.endTimestamp.nanosToMicros().toDouble())
      val samplingSeries =
        AllocationSamplingRateDataSeries(profilers.client, session, profilers.ideServices.featureConfig.isUnifiedPipelineEnabled)
      return samplingSeries.getDataForRange(rangeUs).consecutiveAllocRanges().mapNotNull { r ->
        val infos = MemoryProfiler.getAllocationInfosForSession(profilers.client, session, r, profilers.ideServices)
        if (infos.isEmpty()) {
          null
        } else {
          AllocationSessionArtifact(profilers, session, sessionMetadata, infos[0], r.min, r.max)
        }
      }
    }
  }
}

private fun Long.nanosToMicros() = TimeUnit.NANOSECONDS.toMicros(this)
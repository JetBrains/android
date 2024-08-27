/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.leakcanary

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.SessionMetaData
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionArtifact

class LeakCanarySessionArtifact(override val profilers: StudioProfilers,
                                override val session: Common.Session,
                                override val sessionMetaData: SessionMetaData,
                                leakCanaryLogcatEnded: LeakCanary.LeakCanaryLogcatEnded): SessionArtifact<LeakCanary.LeakCanaryLogcatEnded> {

  override val artifactProto: LeakCanary.LeakCanaryLogcatEnded = leakCanaryLogcatEnded

  // When export/import is supported (Milestone 2) we need to fetch from the Info.
  override val name = "LeakCanary"

  override val timestampNs = leakCanaryLogcatEnded.endTimestamp

  override val isOngoing = (leakCanaryLogcatEnded.endTimestamp == Long.MAX_VALUE)

  // Export/Import is currently not supported. Will support in future (Milestone 2).
  override val canExport = false

  override fun doSelect() {
    // If the capture selected is not part of the currently selected session, we need to select the session containing the capture.
    val needsToChangeSession = session !== profilers.session
    if (needsToChangeSession) {
      profilers.sessionsManager.setSession(session)
    }

    // If leakCanary is not yet open, we need to do it.
    val needsToOpenLeakCanary = profilers.stage !is LeakCanaryModel
    if (needsToOpenLeakCanary) {
      profilers.stage = LeakCanaryModel(profilers)
    }
    (profilers.stage as LeakCanaryModel).loadFromPastSession(artifactProto.startTimestamp, artifactProto.endTimestamp, session)
    profilers.ideServices.featureTracker.trackSessionArtifactSelected(this, profilers.sessionsManager.isSessionAlive)
  }

  companion object {
    @JvmStatic
    fun getSessionArtifacts(profilers: StudioProfilers,
                            session: Common.Session,
                            sessionMetadata: SessionMetaData): List<SessionArtifact<*>> {
      val artifacts: MutableList<SessionArtifact<*>> = mutableListOf()
      val leakInfoEvents = LeakCanaryModel.getLeakCanaryLogcatInfo(profilers.client, session,
                                                                   Range(session.startTimestamp.toDouble(),
                                                                         session.endTimestamp.toDouble()))
      leakInfoEvents.forEach { leakEvent ->
        run {
          artifacts.add(LeakCanarySessionArtifact(profilers, session, sessionMetadata, leakEvent.leakCanaryLogcatInfo.logcatEnded))
        }
      }
      return artifacts
    }
  }
}
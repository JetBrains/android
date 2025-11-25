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
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.ExportableArtifact
import com.android.tools.profilers.LiveViewSessionArtifact
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionArtifact
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LeakCanarySessionArtifact(override val profilers: StudioProfilers,
                                override val session: Common.Session,
                                override val sessionMetaData: SessionMetaData,
                                leakCanaryAnalysisEnded: LeakCanary.LeakCanaryAnalysisEnded) : SessionArtifact<LeakCanary.LeakCanaryAnalysisEnded>, ExportableArtifact {

  private val logger = Logger.getInstance(LeakCanarySessionArtifact::class.java)

  override val artifactProto: LeakCanary.LeakCanaryAnalysisEnded = leakCanaryAnalysisEnded

  // When export/import is supported (Milestone 2) we need to fetch from the Info.
  override val name = "LeakCanary"

  override val timestampNs = leakCanaryAnalysisEnded.endTimestamp

  override val isOngoing = (leakCanaryAnalysisEnded.endTimestamp == Long.MAX_VALUE)

  override val canExport: Boolean
    get() = !isOngoing

  override val exportableName: String
    get() = "leakcanary-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))}"

  override val exportExtension: String
    get() = "asdb"

  override fun export(outputStream: OutputStream) {
    assert(canExport)
    val request = Transport.BytesRequest.newBuilder()
      .setStreamId(session.streamId)
      .setId(session.startTimestamp.toString())
      .build()
    val response = profilers.client.transportClient.getFile(request)

    if (response.filePath.isNotEmpty()) {
      try {
        File(response.filePath).inputStream().use { it.copyTo(outputStream) }
      }
      catch (e: IOException) {
        logger.warn("Failed to export Leak Canary file", e)
      }
    }
    else {
      logger.warn("Failed to export Leak Canary file, file path is empty.")
    }
  }

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
      val leakInfoEvents = LeakCanaryModel.getLeakCanaryAnalysisInfo(profilers.client, session,
                                                                     Range(session.startTimestamp.toDouble(),
                                                                           session.endTimestamp.toDouble()))
      leakInfoEvents.forEach { leakEvent ->
        run {
          artifacts.add(LeakCanarySessionArtifact(profilers, session, sessionMetadata, leakEvent.leakCanaryAnalysisStatus.analysisEnded))
        }
      }
      return artifacts
    }
  }
}
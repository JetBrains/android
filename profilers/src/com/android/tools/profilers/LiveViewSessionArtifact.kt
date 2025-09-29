/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.android.tools.profilers

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * An artifact for a Live View session, which is exportable.
 */
class LiveViewSessionArtifact(
  override val profilers: StudioProfilers,
  override val session: Common.Session,
  override val sessionMetaData: Common.SessionMetaData
) : SessionArtifact<Common.Session>, ExportableArtifact {

  override val artifactProto: Common.Session
    get() = session

  override val name = "Live View"

  override val timestampNs: Long
    get() = 0L

  override val isOngoing: Boolean
    get() = SessionsManager.isSessionAlive(session)

  override val canExport: Boolean
    get() = !isOngoing

  override val exportableName: String
    get() = "live-view-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))}"

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
        Logger.getInstance(LiveViewSessionArtifact::class.java).warn("Failed to export Live View file", e)
      }
    }
  }

  override fun doSelect() {
    // Set the session and explicitly navigate to the LiveStage for this task.
    profilers.sessionsManager.setSession(session)
    if (profilers.stage !is LiveStage) {
      profilers.stage = LiveStage(profilers) { profilers.sessionsManager.endCurrentSession() }
    }
    profilers.ideServices.featureTracker.trackSessionArtifactSelected(this, profilers.sessionsManager.isSessionAlive)
  }

  companion object {
    @JvmStatic
    fun getSessionArtifacts(profilers: StudioProfilers,
                            session: Common.Session,
                            sessionMetaData: Common.SessionMetaData): List<SessionArtifact<*>> {
      if (sessionMetaData.type == Common.SessionMetaData.SessionType.FULL) {
        val hasLiveViewEvent = profilers.client.transportClient.getEventGroups(
          Transport.GetEventGroupsRequest.newBuilder()
            .setStreamId(session.streamId)
            .setPid(session.pid)
            .setKind(Common.Event.Kind.LIVE_VIEW_STATUS)
            .setFromTimestamp(session.startTimestamp)
            .setToTimestamp(if (session.endTimestamp == Long.MAX_VALUE) Long.MAX_VALUE else session.endTimestamp)
            .build()).groupsList.any { it.groupId == session.sessionId }
        if (hasLiveViewEvent) {
          return listOf(LiveViewSessionArtifact(profilers, session, sessionMetaData))
        }
      }
      return emptyList()
    }
  }
}
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
package com.android.tools.profilers.sessions

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData
import com.android.tools.profiler.proto.Common.SessionMetaData
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.StudioMonitorStage
import com.android.tools.profilers.StudioProfilers
import com.google.common.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit

/**
 * A model corresponding to a [Common.Session].
 */
class SessionItem(
  private val profilers: StudioProfilers,
  private var session: Common.Session,
  private val sessionMetaData: SessionMetaData
) : AspectModel<SessionItem.Aspect>(), SessionArtifact<Common.Session?> {
  enum class Aspect {
    MODEL
  }

  private var durationNs: Long = 0
  private var waitingForAgent = false

  /**
   * The list of artifacts (e.g. cpu capture, hprof, etc) that belongs to this session.
   */
  private val childArtifacts = mutableListOf<SessionArtifact<*>>()

  init {
    if (!SessionsManager.isSessionAlive(session)) {
      durationNs = session.endTimestamp - session.startTimestamp
    }
    profilers.addDependency(this).onChange(ProfilerAspect.AGENT) { agentStatusChanged() }
    agentStatusChanged()
  }

  override fun getArtifactProto(): Common.Session {
    return session
  }

  override fun getProfilers(): StudioProfilers {
    return profilers
  }

  override fun getSession(): Common.Session {
    return session
  }

  override fun getSessionMetaData(): SessionMetaData {
    return sessionMetaData
  }

  override fun getName(): String {
    val name = sessionMetaData.sessionName
    if (sessionMetaData.type != SessionMetaData.SessionType.FULL) {
      return name
    }

    // Everything before the first space is the app's name (the format is {APP_NAME (DEVICE_NAME)})
    val firstSpace = name.indexOf(' ')
    assert(firstSpace != -1)
    var appName = name.substring(0, firstSpace)
    val lastDot = appName.lastIndexOf('.')
    if (lastDot != -1) {
      // Strips the packages from the application name
      appName = appName.substring(lastDot + 1)
    }
    return appName + name.substring(firstSpace)
  }

  override fun getTimestampNs(): Long {
    return 0
  }

  override fun isOngoing(): Boolean {
    return SessionsManager.isSessionAlive(session)
  }

  /**
   * Update the [Common.Session] object. Note that while the content within the session can change, the new session instance should
   * correspond to the same one as identified by the session's id.
   */
  fun setSession(session: Common.Session) {
    assert(this.session.sessionId == session.sessionId)
    this.session = session
  }

  override fun onSelect() {
    // Navigate to the new session
    profilers.sessionsManager.setSession(session)
    if (sessionMetaData.type == SessionMetaData.SessionType.FULL &&
        profilers.stageClass != StudioMonitorStage::class.java
    ) {
      profilers.stage = StudioMonitorStage(profilers)
    }
    profilers.ideServices.featureTracker.trackSessionArtifactSelected(this, profilers.sessionsManager.isSessionAlive)
  }

  override fun update(elapsedNs: Long) {
    if (SessionsManager.isSessionAlive(session)) {
      durationNs += elapsedNs
      changed(Aspect.MODEL)
    }
  }

  private fun agentStatusChanged() {
    val oldValue = waitingForAgent
    waitingForAgent = if (SessionsManager.isSessionAlive(session) && session == profilers.sessionsManager.selectedSession) {
      val agentData = profilers.agentData
      agentData.status == AgentData.Status.UNSPECIFIED
    } else {
      false
    }
    if (oldValue != waitingForAgent) {
      changed(Aspect.MODEL)
    }
  }

  fun deleteSession() {
    profilers.sessionsManager.deleteSession(session)
  }

  fun getSubtitle(): String {
    if (sessionMetaData.type != SessionMetaData.SessionType.FULL) {
      return if (childArtifacts.isNotEmpty()) {
        assert(childArtifacts.size == 1)
        childArtifacts[0].name
      } else {
        SESSION_LOADING
      }
    }

    return if (waitingForAgent) {
      SESSION_INITIALIZING
    } else {
      val durationUs = TimeUnit.NANOSECONDS.toMicros(durationNs)
      TimeFormatter.getMultiUnitDurationString(durationUs)
    }
  }

  @VisibleForTesting
  fun getChildArtifacts(): List<SessionArtifact<*>> {
    return childArtifacts.toList()
  }

  fun setChildArtifacts(artifacts: List<SessionArtifact<*>>) {
    childArtifacts.clear()
    childArtifacts.addAll(artifacts)

    changed(Aspect.MODEL)
  }

  companion object {
    private const val SESSION_INITIALIZING = "Starting..."

    @VisibleForTesting
    const val SESSION_LOADING = "Loading..."
  }
}
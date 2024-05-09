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
import com.android.tools.profilers.LiveStage
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.StudioMonitorStage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskSupportUtils
import com.android.tools.profilers.tasks.TaskTypeMappingUtils
import com.google.common.annotations.VisibleForTesting
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * A model corresponding to a [Common.Session].
 */
class SessionItem(
  override val profilers: StudioProfilers,
  initialSession: Common.Session,
  override val sessionMetaData: SessionMetaData
) : AspectModel<SessionItem.Aspect>(), SessionArtifact<Common.Session> {
  enum class Aspect {
    MODEL
  }

  private var activeSession: Common.Session = initialSession

  private var durationNs: Long = 0
  private var waitingForAgent = false

  /**
   * The list of artifacts (e.g. cpu capture, hprof, etc) that belongs to this session.
   */
  private val childArtifacts = mutableListOf<SessionArtifact<*>>()

  override val name = parseName(sessionMetaData)

  init {
    if (!SessionsManager.isSessionAlive(activeSession)) {
      durationNs = activeSession.endTimestamp - activeSession.startTimestamp
    }
    profilers.addDependency(this).onChange(ProfilerAspect.AGENT) { agentStatusChanged() }
    agentStatusChanged()
  }

  override val session
    get() = activeSession

  override val artifactProto
    get() = activeSession

  override val timestampNs = 0L

  override val isOngoing
    get() = SessionsManager.isSessionAlive(activeSession)

  override val canExport get() = childArtifacts.size == 1 && isImported()

  override fun export(outputStream: OutputStream) {
    assert(canExport)
    assert(childArtifacts.size == 1)
    val artifact = childArtifacts.first()
    artifact.export(outputStream)
  }

  /**
   * Update the [Common.Session] object. Note that while the content within the session can change, the new session instance should
   * correspond to the same one as identified by the session's id.
   */
  fun setSession(session: Common.Session) {
    assert(activeSession.sessionId == session.sessionId)
    activeSession = session
  }

  override fun doSelect() {
    // Navigate to the new session
    profilers.sessionsManager.setSession(activeSession)
    if (sessionMetaData.type == SessionMetaData.SessionType.FULL) {
      val targetStageClass =
        if (profilers.ideServices.featureConfig.isTaskBasedUxEnabled) LiveStage(profilers) else StudioMonitorStage(profilers)

      if (profilers.stageClass != targetStageClass::class.java) {
        profilers.stage = targetStageClass
      }
    }
    profilers.ideServices.featureTracker.trackSessionArtifactSelected(this, profilers.sessionsManager.isSessionAlive)
  }

  override fun update(elapsedNs: Long) {
    if (SessionsManager.isSessionAlive(activeSession)) {
      durationNs += elapsedNs
      changed(Aspect.MODEL)
    }
  }

  private fun agentStatusChanged() {
    val oldValue = waitingForAgent
    waitingForAgent = if (SessionsManager.isSessionAlive(activeSession) && activeSession == profilers.sessionsManager.selectedSession) {
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
    profilers.sessionsManager.deleteSession(activeSession)
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

  fun getChildArtifacts(): List<SessionArtifact<*>> {
    return childArtifacts.toList()
  }

  fun setChildArtifacts(artifacts: List<SessionArtifact<*>>) {
    childArtifacts.clear()
    childArtifacts.addAll(artifacts)

    changed(Aspect.MODEL)
  }

  fun containsExactlyOneArtifact() = childArtifacts.size == 1


  /**
   * Returns the task or viewer that can be launched for a given recording.
   *
   * Note: A "viewer" is essentially the same as a task, but it specifies the context in which the task is used when opening an imported or
   * past recording. This method assumes a one-to-one mapping between each recording or artifact and each corresponding task or viewer.
   */
  fun getTaskType(): ProfilerTaskType {
    // Attempt to find the supported task type.
    val supportedTaskTypes = profilers.taskHandlers.filter { (taskType, taskHandler) ->
      TaskSupportUtils.isTaskSupportedByRecording(taskType, taskHandler, this)
    }.keys

    // Assumes each SessionItem/recording only has one associated task type.
    if (supportedTaskTypes.size != 1) {
      return ProfilerTaskType.UNSPECIFIED
    }
    val supportedTaskType = supportedTaskTypes.single()
    // If the task is newly recorded (not imported), it is verified whether the supported task type aligns with the intended task the user
    // wants to initiate. This verification is crucial for identifying failed tasks. For instance, a startup task failing to gather an
    // artifact shares the same structure as a live view task (both lack a child artifact). Hence, cross-checking with the intended task
    // prevents misrepresentation of a live view recording.
    if (!isImported() && TaskTypeMappingUtils.convertTaskType(sessionMetaData.taskType) != supportedTaskType) {
      return ProfilerTaskType.UNSPECIFIED
    }

    return supportedTaskType
  }

  /**
   * The MEMORY_CAPTURE and CPU_CAPTURE session types are indicative of imported memory and CPU sessions respectively.
   */
  fun isImported() = sessionMetaData.type == SessionMetaData.SessionType.CPU_CAPTURE
                     || sessionMetaData.type == SessionMetaData.SessionType.MEMORY_CAPTURE

  companion object {
    private const val SESSION_INITIALIZING = "Starting..."

    @VisibleForTesting
    const val SESSION_LOADING = "Loading..."

    private fun parseName(metaData: SessionMetaData): String {
      val nameRegex = "(?<package>.+) \\((?<device>.+)\\)".toRegex()

      if (metaData.type != SessionMetaData.SessionType.FULL) {
        return metaData.sessionName
      }

      val match = nameRegex.matchEntire(metaData.sessionName)

      if (match != null) {
        val appName = match.groups["package"]!!.value.split('.').last()
        val deviceName = match.groups["device"]!!.value
        return "$appName ($deviceName)"
      }

      return metaData.sessionName
    }
  }
}
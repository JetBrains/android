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
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.leakcanarylib.LeakCanarySerializer
import com.android.tools.leakcanarylib.data.Analysis
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.ModelStage
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.NotNull

class LeakCanaryModel(@NotNull private val profilers: StudioProfilers): ModelStage(profilers) {

  private lateinit var statusListener: TransportEventListener
  private val logger: Logger = Logger.getInstance(LeakCanaryModel::class.java)
  private val leakCanarySerializer = LeakCanarySerializer()
  var leakEvents = mutableListOf<Analysis>()
  private val _leaksDetectedCount = MutableStateFlow(0)
  val leaksDetectedCount = _leaksDetectedCount.asStateFlow()

  fun startListening() {
    registerLeakCanaryListeners()
    toggleLeakCanaryLogcatTracking(profilers.session, enable = true, endSession = false)
  }

  fun stopListening() {
    toggleLeakCanaryLogcatTracking(profilers.session, enable = false, endSession = true)
    deregisterLeakCanaryListeners()
  }

  private fun registerLeakCanaryListeners() {
    val startTime = System.nanoTime()
    statusListener = registerListener(startTime)
    profilers.transportPoller.registerListener(statusListener)
  }

  private fun deregisterLeakCanaryListeners() {
    profilers.transportPoller.unregisterListener(statusListener)
  }

  /**
   * Handles LeakCanary logcat events
   * @param event: The LeakCanary logcat event.
   */
  private fun leakDetected(event: Common.Event) {
    val leakAnalysisEvent = getEventFromLogcatMessage(event.leakcanaryLogcat.logcatMessage)
    if (leakAnalysisEvent != null) {
      leakEvents.add(leakAnalysisEvent)
      onLeakDetected()
    }
  }

  private fun onLeakDetected() {
    _leaksDetectedCount.value++
  }

  /**
   * Registers a transport event listener for LeakCanary logcat events.
   * @param startTime: The start time for filtering events.
   * @return The registered transport event listener.
   */
  private fun registerListener(startTime: Long): TransportEventListener {
    return TransportEventListener(eventKind = Common.Event.Kind.LEAKCANARY_LOGCAT,
                                  executor = profilers.ideServices.mainExecutor,
                                  streamId = { profilers.session.streamId },
                                  processId = { profilers.session.pid },
                                  startTime = { startTime },
                                  callback = { event ->
                                    false.also {
                                      leakDetected(event)
                                    }
                                  })
  }

  /**
   * Extracts the leak analysis event from a LeakCanary logcat message.
   * @param logcatMessage: The LeakCanary logcat message.
   * @return The leak analysis event, or null if there's an issue parsing the message.
   */
  private fun getEventFromLogcatMessage(logcatMessage: String): Analysis? {
    try {
      return leakCanarySerializer.parseLogcatMessage(logcatMessage)
    }
    catch (e: Exception) {
      logger.warn("Leak canary serializer detected issue while parsing .. skipping leak event ")
      return null
    }
  }

  /**
   * Starts or stops LeakCanary logcat tracking in the profiler session.
   * @param session: The profiler session.
   * @param enable: true to start tracking, false to stop tracking.
   * @param endSession: true to end the session when stopping tracking.
   */
  private fun toggleLeakCanaryLogcatTracking(session: Common.Session,
                                             enable: Boolean,
                                             endSession: Boolean) {
    val cmd = Commands.Command.newBuilder().apply {
      streamId = session.streamId
      pid = session.pid
      if (enable) {
        type = Commands.Command.CommandType.START_LOGCAT_TRACKING
      }
      else {
        type = Commands.Command.CommandType.STOP_LOGCAT_TRACKING
        if (endSession) {
          sessionId = session.sessionId
        }
      }
    }
    profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(cmd).build())
  }

  // Setting it to UNKNOWN_STAGE since stage usage is avoided in task-based ux.
  override fun getStageType(): AndroidProfilerEvent.Stage = AndroidProfilerEvent.Stage.UNKNOWN_STAGE

  fun loadFromPastSession(startTimestamp: Long,
                          endTimeStamp: Long,
                          session: Common.Session) {
    leakEvents = getAllLeakCanaryEvents(session, startTimestamp, endTimeStamp) as MutableList<Analysis>
    _leaksDetectedCount.value = leakEvents.size
  }

  private fun getAllLeakCanaryEvents(session: Common.Session,
                                     startTimestamp: Long,
                                     endTimeStamp: Long): List<Analysis> {
    val result = getLeaksFromRange(profilers.client, session, Range(startTimestamp.toDouble(), endTimeStamp.toDouble()))
    return result.mapNotNull { event -> getEventFromLogcatMessage(event.leakcanaryLogcat.logcatMessage) }
  }

  companion object {
    fun getLeaksFromRange(
      profilerClient: ProfilerClient,
      session: Common.Session,
      range: Range): List<Common.Event> {
     return profilerClient.transportClient.getEventGroups(
        Transport.GetEventGroupsRequest.newBuilder()
          .setStreamId(session.streamId)
          .setPid(session.pid)
          .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
          .setFromTimestamp(range.min.toLong())
          .setToTimestamp(range.max.toLong())
          .build()).groupsList.flatMap{group -> group.eventsList.toList()}
    }
  }
}
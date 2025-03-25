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
import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.inspectors.common.api.actions.NavigateToCodeAction
import com.android.tools.leakcanarylib.LeakCanaryParser
import com.android.tools.leakcanarylib.data.Analysis
import com.android.tools.leakcanarylib.data.AnalysisFailure
import com.android.tools.leakcanarylib.data.AnalysisSuccess
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakingStatus
import com.android.tools.leakcanarylib.data.Node
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.ModelStage
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.NotNull

class LeakCanaryModel(@NotNull private val profilers: StudioProfilers): ModelStage(profilers), Updatable {

  private lateinit var statusListener: TransportEventListener
  private val logger: Logger = Logger.getInstance(LeakCanaryModel::class.java)
  private val myLeakCanaryParser = LeakCanaryParser()

  private val _leaks = MutableStateFlow(listOf<Leak>())
  val leaks = _leaks.asStateFlow()
  private val _selectedLeak = MutableStateFlow<Leak?>(null)
  val selectedLeak = _selectedLeak.asStateFlow()
  private val _isRecording = MutableStateFlow(false)
  val isRecording = _isRecording.asStateFlow()
  private val _elapsedNs = MutableStateFlow(0L)
  val elapsedNs = _elapsedNs.asStateFlow()

  fun startListening() {
    profilers.updater.register(this)
    setIsRecording(true)
    registerLeakCanaryListeners()
    toggleLeakCanaryLogcatTracking(profilers.session, enable = true, endSession = false)
  }

  fun stopListening() {
    setIsRecording(false)
    toggleLeakCanaryLogcatTracking(profilers.session, enable = false, endSession = true)
    deregisterLeakCanaryListeners()
    profilers.updater.unregister(this)
  }

  fun setIsRecording(isRecording: Boolean) {
    _isRecording.value = isRecording
  }

  @VisibleForTesting
  fun clearLeaks() {
    _leaks.value = listOf()
    onLeakSelection(null)
  }

  fun onLeakSelection(newLeak: Leak?) {
    _selectedLeak.value = newLeak
  }

  /**
   * Creates and registers a transport event listener for LeakCanary logcat events.
   */
  private fun registerLeakCanaryListeners() {
    val startTime = profilers.session.startTimestamp
    statusListener = TransportEventListener(eventKind = Common.Event.Kind.LEAKCANARY_LOGCAT,
                                            executor = profilers.ideServices.mainExecutor,
                                            streamId = { profilers.session.streamId },
                                            processId = { profilers.session.pid },
                                            startTime = { startTime },
                                            callback = { event ->
                                              false.also {
                                                leakDetected(event)
                                              }
                                            })
    profilers.transportPoller.registerListener(statusListener)
  }

  private fun deregisterLeakCanaryListeners() {
    profilers.transportPoller.unregisterListener(statusListener)
  }

  @VisibleForTesting
  fun addLeaks(newLeaks: List<Leak>) {
    val newLeakList = _leaks.value + newLeaks
    _leaks.value = newLeakList
  }

  /**
   * Gets the parsed logcat message and adds to list of leaks.
   * @param event: The LeakCanary logcat event.
   */
  private fun leakDetected(event: Common.Event) {
    if (event.leakcanaryLogcat.logcatMessage.isEmpty()) return
    val leakAnalysisEvent = getEventFromLogcatMessage(event.leakcanaryLogcat.logcatMessage)
    if (leakAnalysisEvent != null) {
      if (leakAnalysisEvent is AnalysisSuccess) {
        addLeaks(leakAnalysisEvent.leaks)
      }
      else {
        // There is failure in leak analysis.
        logger.warn("Leak analysis failure {}", (leakAnalysisEvent as AnalysisFailure).exception)
      }

      // The first leak is selected, so its leakTrace is displayed by default in UI.
      if (_selectedLeak.value == null && _leaks.value.isNotEmpty()) {
        onLeakSelection(_leaks.value.first())
      }
    }
  }

  /**
   * Extracts the leak analysis event from a LeakCanary logcat message.
   * @param logcatMessage: The LeakCanary logcat message.
   * @return The leak analysis event, or null if there's an issue parsing the message.
   */
  private fun getEventFromLogcatMessage(logcatMessage: String): Analysis? {
    try {
      return myLeakCanaryParser.parseLogcatMessage(logcatMessage)
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
    // Get all LeakCanary events from start time to end time.
    val analysisEvents = getAllLeakCanaryEvents(session, startTimestamp, endTimeStamp)
    analysisEvents.forEach { analysis ->
      if (analysis is AnalysisSuccess) {
        addLeaks(analysis.leaks)
      }
    }

    // The first leak is selected, so its leakTrace is displayed by default in UI.
    if (_leaks.value.isNotEmpty()) {
      onLeakSelection(_leaks.value.first())
    }
  }

  private fun getAllLeakCanaryEvents(session: Common.Session,
                                     startTimestamp: Long,
                                     endTimeStamp: Long): List<Analysis> {
    val eventList = getLeaksFromRange(profilers.client, session, Range(startTimestamp.toDouble(), endTimeStamp.toDouble()))
    return eventList.mapNotNull { event -> getEventFromLogcatMessage(event.leakcanaryLogcat.logcatMessage) }
  }

  fun goToDeclaration(node: Node) {
    val codeLocationSupplier: () -> CodeLocation = {
      CodeLocation.Builder(node.className.removeSuffix("[]"))
        .build()
    }
    val navigator = this.studioProfilers.ideServices.codeNavigator
    val action = NavigateToCodeAction(codeLocationSupplier, navigator)
    val event = createEvent(action, DataContext.EMPTY_CONTEXT, null, ActionPlaces.CODE_INSPECTION, ActionUiKind.NONE, null)
    action.actionPerformed(event)
  }

  companion object {
    /**
     * Fetches all LeakCanary logcat dump events within a given session. It returns leaks that are within a given range, which is
     * provided by the logcat end status events fetched by `getLeakCanaryLogcatInfo`.
     */
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
          .build()).groupsList.flatMap { group -> group.eventsList.toList() }
    }

    /**
     * Fetches all LeakCanary logcat dump events within a given session. It returns leaks that are within a given range, which is
     * the session start and end time provided by `getSessionArtifacts`.
     */
    fun getLeakCanaryLogcatInfo(
      profilerClient: ProfilerClient,
      session: Common.Session,
      range: Range): List<Common.Event> {
      return profilerClient.transportClient.getEventGroups(
        Transport.GetEventGroupsRequest.newBuilder()
          .setStreamId(session.streamId)
          .setPid(session.pid)
          .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT_INFO)
          .setFromTimestamp(range.min.toLong())
          .setToTimestamp(range.max.toLong())
          .build()).groupsList.flatMap { group -> group.eventsList.toList() }
        .filter { event -> event.isEnded }
    }

    /**
     * Extracts the class name from a Leak object, prioritizing the leaking class if available.
     * If the full class path is long, it shortens it to the last two segments.
     *
     * @param leak The Leak object to extract the class name from.
     * @return The extracted class name or an empty string if no leak or class name is found.
     */
    fun getLeakClassName(leak: Leak?): String {
      val className = leak?.displayedLeakTrace?.firstNotNullOfOrNull { leakTrace ->
        leakTrace.nodes.firstOrNull { node -> node.leakingStatus == LeakingStatus.YES }?.className
      } ?: return ""
      val classPathSplit = className.split(".")
      return if (classPathSplit.size >= 2) {
        "${classPathSplit[classPathSplit.size - 2]}.${classPathSplit.last()}"
      } else {
        className
      }
    }
  }

  override fun update(elapsedNs: Long) {
    _elapsedNs.value += elapsedNs
  }
}
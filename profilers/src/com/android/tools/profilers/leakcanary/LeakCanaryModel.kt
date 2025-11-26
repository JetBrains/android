/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.leakcanarylib.data.Analysis
import com.android.tools.leakcanarylib.data.AnalysisFailure
import com.android.tools.leakcanarylib.data.AnalysisUpdate
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
import com.android.tools.profilers.tasks.analytics.TaskFinishedState
import com.android.tools.profilers.tasks.analytics.TaskTracker
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.NotNull

class LeakCanaryModel(@NotNull private val profilers: StudioProfilers) : ModelStage(profilers), Updatable {

  private lateinit var statusListener: TransportEventListener
  private lateinit var hostAnalysisTriggerListener: TransportEventListener
  private val logger: Logger = Logger.getInstance(LeakCanaryModel::class.java)
  private var sessionData = profilers.session
  private val heapDumper = LeakCanaryHeapDumper(profilers).apply {
    onHostAnalysisFinished = { analysis ->
      handleLeakAnalysis(analysis)
    }
  }
  val requiredRetainedObjectCount = 5
  private val _leaks = MutableStateFlow(listOf<Leak>())
  val leaks = _leaks.asStateFlow()
  private val _selectedLeak = MutableStateFlow<Leak?>(null)
  val selectedLeak = _selectedLeak.asStateFlow()
  private val _isRecording = MutableStateFlow(false)
  val isRecording = _isRecording.asStateFlow()
  private val _elapsedNs = MutableStateFlow(0L)
  val elapsedNs = _elapsedNs.asStateFlow()
  private val _objectRetainedCount = MutableStateFlow(0)
  val objectRetainedCount = _objectRetainedCount.asStateFlow()
  private val _analysisProgress = MutableStateFlow(0)
  val analysisProgress = _analysisProgress.asStateFlow()
  private val _isLeakCanaryPresent = MutableStateFlow(true)
  val isLeakCanaryPresent = _isLeakCanaryPresent.asStateFlow()

  override fun onEnter() {
    sessionData = profilers.session
    // If we are entering this stage for a past recording (i.e., the session is not live),
    // we need to tell the TransportService to use task specific database to query.
    // For a new, live recording, this is handled by TransportService when the session starts.
    if (!profilers.sessionsManager.isSessionAlive) {
      profilers.sessionsManager.setTaskDb(sessionData)
    }
  }

  override fun onExit() {
    profilers.sessionsManager.unsetTaskDb(sessionData)
  }

  fun startListening() {
    profilers.updater.register(this)
    setIsRecording(true)
    checkLeakCanaryPresence()
    setObjectRetainedCount(0)
    setAnalysisProgress(0)
    registerLeakCanaryListeners()
    toggleLeakCanaryLogcatTracking(profilers.session, enable = true, endSession = false)
  }

  fun stopListening() {
    setIsRecording(false)
    toggleLeakCanaryLogcatTracking(profilers.session, enable = false, endSession = true)
    deregisterLeakCanaryListeners()
    profilers.updater.unregister(this)

    // Track the successful completion of the user-initiated leakCanary recording task.
    myTaskTracker.trackTaskFinished(TaskFinishedState.COMPLETED)
  }

  fun setIsRecording(isRecording: Boolean) {
    _isRecording.value = isRecording
  }

  fun setObjectRetainedCount(objectRetainedCount: Int) {
    _objectRetainedCount.value = objectRetainedCount
  }

  fun setAnalysisProgress(analysisProgress: Int) {
    _analysisProgress.value = analysisProgress
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
    statusListener = TransportEventListener(eventKind = Common.Event.Kind.LEAKCANARY_ANALYSIS,
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

    hostAnalysisTriggerListener = TransportEventListener(eventKind = Common.Event.Kind.LEAKCANARY_HOST_ANALYSIS_TRIGGER,
                                                         executor = profilers.ideServices.poolExecutor,
                                                         streamId = { profilers.session.streamId },
                                                         processId = { profilers.session.pid },
                                                         startTime = { startTime },
                                                         callback = { _ ->
                                                           heapDumper.triggerAndAnalyze()
                                                           false
                                                         })
    profilers.transportPoller.registerListener(hostAnalysisTriggerListener)
  }

  private fun checkLeakCanaryPresence() {
    val command = Commands.Command.newBuilder().apply {
      streamId = profilers.session.streamId
      pid = profilers.session.pid
      type = Commands.Command.CommandType.CHECK_LEAKCANARY_PRESENT
    }.build()

    profilers.ideServices.poolExecutor.execute {
      val response = profilers.client.transportClient.execute(
        Transport.ExecuteRequest.newBuilder().setCommand(command).build()
      )

      val listener = TransportEventListener(
        eventKind = Common.Event.Kind.LEAKCANARY_PRESENCE_CHECK,
        executor = profilers.ideServices.poolExecutor,
        filter = { it.commandId == response.commandId },
        streamId = { profilers.session.streamId },
        processId = { profilers.session.pid },
        callback = { event ->
          val isPresent = event.leakcanaryPresenceCheck.isPresent
          logger.info("LeakCanary presence check returned: $isPresent")
          profilers.ideServices.mainExecutor.execute {
            _isLeakCanaryPresent.value = isPresent
          }
          true // Unregister listener after first event.
        }
      )
      profilers.transportPoller.registerListener(listener)
    }
  }

  private fun deregisterLeakCanaryListeners() {
    profilers.transportPoller.unregisterListener(statusListener)
    profilers.transportPoller.unregisterListener(hostAnalysisTriggerListener)
  }

  @VisibleForTesting
  fun addLeaks(newLeaks: List<Leak>) {
    val uniqueNewLeaks = newLeaks.filter { it !in _leaks.value }
    if (uniqueNewLeaks.isNotEmpty()) {
      _leaks.value = _leaks.value + uniqueNewLeaks
    }
  }

  /**
   * Gets the parsed logcat message and adds to list of leaks.
   * @param event: The LeakCanary logcat event.
   */
  private fun leakDetected(event: Common.Event) {
    val analysis = Analysis.fromString(event.leakcanaryAnalysis.data)?: return
    if (handleRetainedObject(analysis)) return
    if (handleAnalysisProgress(analysis)) return
    handleLeakAnalysis(analysis)
  }

  private fun handleRetainedObject(analysis: Analysis): Boolean {
    if (analysis !is AnalysisUpdate) return false
    val retainedObjectsRegex = """Found (\d+) objects retained""".toRegex()
    return retainedObjectsRegex.find(analysis.message)?.let { matchResult ->
      matchResult.groupValues.getOrNull(1)?.toIntOrNull()?.let { count ->
        logger.info("LeakCanary: $count objects retained.")
        setObjectRetainedCount(count)
      }
      true
    } ?: false
  }

  private fun handleAnalysisProgress(analysis: Analysis): Boolean {
    if (analysis !is AnalysisUpdate) return false
    val analysisProgressRegex = """Analysis in progress, (\d+)% done""".toRegex()
    return analysisProgressRegex.find(analysis.message)?.let { matchResult ->
      matchResult.groupValues.getOrNull(1)?.toIntOrNull()?.let { progress ->
        logger.info("LeakCanary: Analysis is $progress% done.")
        setAnalysisProgress(progress)
      }
      true
    } ?: false
  }

  private fun handleLeakAnalysis(analysis: Analysis?) {
    if (analysis == null) return
    setObjectRetainedCount(0)
    setAnalysisProgress(0)

    if (analysis is AnalysisSuccess) {
      addLeaks(analysis.leaks)
    }
    else if (analysis is AnalysisFailure){
      // There is failure in leak analysis.
      logger.warn("Leak analysis failure", analysis.exception)
    }

    // The first leak is selected, so its leakTrace is displayed by default in UI.
    if (_selectedLeak.value == null && _leaks.value.isNotEmpty()) {
      onLeakSelection(_leaks.value.first())
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

    // Track the successful completion of loading a past Leak Canary session.
    myTaskTracker.trackTaskFinished(TaskFinishedState.COMPLETED)
  }

  private fun getAllLeakCanaryEvents(session: Common.Session,
                                     startTimestamp: Long,
                                     endTimeStamp: Long): List<Analysis> {
    val eventList = getLeaksFromRange(profilers.client, session, Range(startTimestamp.toDouble(), endTimeStamp.toDouble()))
    return eventList.mapNotNull { event -> Analysis.fromString(event.leakcanaryAnalysis.data) }
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

  fun isDeclarationAvailableAsync(node: Node): CompletableFuture<Boolean> {
    val codeLocationSupplier: CodeLocation = CodeLocation.Builder(node.className.removeSuffix("[]")).build()
    val navigator = this.studioProfilers.ideServices.codeNavigator
    return navigator.isNavigatableAsync(codeLocationSupplier)
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
          .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS)
          .setFromTimestamp(range.min.toLong())
          .setToTimestamp(range.max.toLong())
          .build()).groupsList.flatMap { group -> group.eventsList.toList() }
    }

    /**
     * Fetches all LeakCanary logcat dump events within a given session. It returns leaks that are within a given range, which is
     * the session start and end time provided by `getSessionArtifacts`.
     */
    fun getLeakCanaryAnalysisInfo(
      profilerClient: ProfilerClient,
      session: Common.Session,
      range: Range): List<Common.Event> {
      return profilerClient.transportClient.getEventGroups(
        Transport.GetEventGroupsRequest.newBuilder()
          .setStreamId(session.streamId)
          .setPid(session.pid)
          .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS)
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
      if (leak?.displayedLeakTrace == null || leak.displayedLeakTrace.isEmpty()) {
        return ""
      }
      val leakTrace = leak.displayedLeakTrace.first()
      val suspectNodeList = leakTrace.nodes.filterIndexed { index, node ->
        when (node.leakingStatus) {
          LeakingStatus.UNKNOWN -> true
          LeakingStatus.NO -> index == leakTrace.nodes.lastIndex || leakTrace.nodes[index + 1].leakingStatus != LeakingStatus.NO
          else -> false
        }
      }
      return suspectNodeList.firstOrNull()?.let { node ->
        val referenceField = node.referencingField
        "${referenceField?.className ?: ""}.${referenceField?.referenceName ?: ""}"
      } ?: leakTrace.nodes.last().className
    }
  }

  override fun update(elapsedNs: Long) {
    _elapsedNs.value += elapsedNs
  }
}

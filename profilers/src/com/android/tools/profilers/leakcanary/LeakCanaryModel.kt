/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.leakcanarylib.data.AnalysisSuccess
import com.android.tools.leakcanarylib.data.AnalysisUpdate
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakType
import com.android.tools.leakcanarylib.data.LeakingStatus
import com.android.tools.leakcanarylib.data.Node
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.StartLeakCanaryTaskData
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.LeakCanaryDeviceError.ErrorType.LEAKCANARY_ERROR_APP_CONTEXT_NULL
import com.android.tools.profiler.proto.Common.LeakCanaryDeviceError.ErrorType.LEAKCANARY_ERROR_BROADCAST_DELIVERY_FAILED
import com.android.tools.profiler.proto.Common.LeakCanaryDeviceError.ErrorType.LEAKCANARY_ERROR_LOGCAT_PARSING_FAILURE
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.ModelStage
import com.android.tools.profilers.Notification
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.config.LeakCanaryConfiguration
import com.android.tools.profilers.cpu.config.LeakCanaryMode
import com.android.tools.profilers.tasks.analytics.LeakCanaryLeakAnalysis
import com.android.tools.profilers.tasks.analytics.LeakCanaryProcessingErrorCode
import com.android.tools.profilers.tasks.analytics.LeakCanaryStartErrorCode
import com.android.tools.profilers.tasks.analytics.LeakCanaryUiAction
import com.android.tools.profilers.tasks.analytics.TaskFinishedState
import com.android.tools.profilers.tasks.analytics.TaskProcessingFailedMetadata
import com.android.tools.profilers.tasks.analytics.TaskStartFailedMetadata
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.NotNull

class LeakCanaryModel(@NotNull private val profilers: StudioProfilers, heapDumper: LeakCanaryHeapDumper? = null) :
  ModelStage(profilers), Updatable {

  private lateinit var statusListener: TransportEventListener
  private lateinit var objectCountListener: TransportEventListener
  private lateinit var deviceErrorListener: TransportEventListener
  private val logger: Logger = Logger.getInstance(LeakCanaryModel::class.java)
  private var sessionData = profilers.session
  private val heapDumper: LeakCanaryHeapDumper
  private var lastAnalysisTimestampMs: Long = 0

  init {
    this.heapDumper =
      heapDumper
        ?: LeakCanaryHeapDumper(profilers).apply {
          onHostAnalysisFinished = { analysis, size, duration, analysisDuration ->
            handleLeakAnalysis(analysis, size, duration, analysisDuration)
          }
          onAnalysisProgress = { progress -> setAnalysisProgress(progress) }
          onFatalError = { error, message -> handleLeakCanaryFatalError(error, message) }
        }
  }

  private var _retainedObjectThreshold = MutableStateFlow(5)
  val retainedObjectThreshold = _retainedObjectThreshold.asStateFlow()
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
  private val _isStopping = MutableStateFlow(false)
  val isStopping = _isStopping.asStateFlow()
  val isLeakCanaryMilestone2Enabled
    get() = profilers.ideServices.featureConfig.isLeakCanaryMilestone2Enabled

  @VisibleForTesting var leakcanaryMode = StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE

  private val _isBannerVisible = MutableStateFlow(false)
  val isBannerVisible = _isBannerVisible.asStateFlow()

  /** Sets the current LeakCanary mode (e.g., ON_DEVICE or ON_HOST) and updates the banner visibility accordingly. */
  fun setLeakCanaryMode(mode: StartLeakCanaryTaskData.LeakCanaryMode) {
    logger.info("LeakCanary running in ${mode.name} mode")
    leakcanaryMode = mode
    updateBannerVisibility()
  }

  /**
   * Reads the current LeakCanary configuration from the profiler settings and updates the running mode and threshold. If the user has
   * explicitly modified the settings from their defaults, it will also dismiss the new feature banner permanently.
   */
  fun updateModeFromSettings() {
    val featureLevel = profilers.device?.featureLevel ?: 0
    val configs = profilers.ideServices.getTaskCpuProfilerConfigs(featureLevel)
    val config = configs.filterIsInstance<LeakCanaryConfiguration>().firstOrNull()
    if (config != null) {
      setLeakCanaryMode(config.mode)
      if (config.source == LeakCanaryMode.STUDIO) {
        _retainedObjectThreshold.value = config.threshold
      }

      // If the user has explicitly changed the settings from the default, suppress the banner permanently.
      if (config.source != LeakCanaryMode.STUDIO || config.threshold != 5) {
        setBannerDoNotShowAgain()
      }
    }
  }

  /**
   * Evaluates all conditions to determine if the educational feature banner should be displayed.
   *
   * The banner is only shown if ALL the following conditions are met:
   * 1. The Milestone 2 feature flag is enabled.
   * 2. The user has not permanently suppressed the banner (by dismissing it or changing settings).
   * 3. The current mode is Studio mode (ON_HOST).
   */
  private fun shouldShowEducationalBanner(): Boolean {
    val doNotShowAgain = profilers.ideServices.persistentProfilerPreferences.getBoolean(KEY_LEAKCANARY_BANNER_DO_NOT_SHOW, false)
    val isStudioMode = leakcanaryMode == StartLeakCanaryTaskData.LeakCanaryMode.ON_HOST

    if (!isLeakCanaryMilestone2Enabled || !isStudioMode || doNotShowAgain) {
      return false
    }
    return true
  }

  /** Updates whether the milestone 2 feature banner should be displayed to the user. */
  private fun updateBannerVisibility() {
    _isBannerVisible.value = shouldShowEducationalBanner()
  }

  /** Temporarily dismisses the feature banner for the current session. */
  fun dismissBanner() {
    _isBannerVisible.value = false
  }

  /** Permanently hides the feature banner across all sessions by updating user preferences. */
  fun setBannerDoNotShowAgain() {
    profilers.ideServices.persistentProfilerPreferences.setBoolean(KEY_LEAKCANARY_BANNER_DO_NOT_SHOW, true)
    updateBannerVisibility()
  }

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
    if (isLeakCanaryMilestone2Enabled) {
      updateModeFromSettings()
    }
    profilers.updater.register(this)
    setIsRecording(true)
    checkPresenceAndFetchThreshold()
    setObjectRetainedCount(0)
    setAnalysisProgress(0)
    registerLeakCanaryListeners()
    toggleLeakCanaryTracking(profilers.session, enable = true, endSession = false)
  }

  fun requestStopRecording() {
    _isStopping.value = true
    if (objectRetainedCount.value > 0 && analysisProgress.value == 0) {
      forceHeapDump()
    } else if (analysisProgress.value == 0) {
      stopListening()
    }
  }

  fun stopListening(isUserInitiated: Boolean = true) {
    if (isUserInitiated) {
      myTaskTracker.trackLeakCanaryUiAction(LeakCanaryUiAction.STOP_RECORDING_CLICKED)
      if (heapDumper.isHeapDumpInProgress() || _analysisProgress.value in 1..99) {
        myTaskTracker.trackLeakCanaryUiAction(LeakCanaryUiAction.CANCELLED_DURING_ANALYSIS)
      }

      // Track the successful completion of the user-initiated leakCanary recording task.
      myTaskTracker.trackTaskFinished(TaskFinishedState.COMPLETED)
    }

    _isStopping.value = false
    setIsRecording(false)
    toggleLeakCanaryTracking(profilers.session, enable = false, endSession = true)
    deregisterLeakCanaryListeners()
    profilers.updater.unregister(this)
  }

  /**
   * Forces a heap dump based on the current mode.
   *
   * In ON_DEVICE mode, it sends a command to the device to trigger LeakCanary's internal heap dumper. In ON_HOST mode, it triggers the
   * Studio-side heap dumper (LeakCanaryHeapDumper).
   */
  fun forceHeapDump() {
    myTaskTracker.trackLeakCanaryUiAction(LeakCanaryUiAction.FORCE_DUMP_CLICKED)
    if (leakcanaryMode == StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE) {
      val forceDumpCommand =
        Commands.Command.newBuilder()
          .setStreamId(sessionData.streamId)
          .setPid(sessionData.pid)
          .setSessionId(sessionData.sessionId)
          .setType(Commands.Command.CommandType.FORCE_DUMP_LEAKCANARY_ON_DEVICE)
          .build()
      profilers.ideServices.poolExecutor.execute {
        try {
          profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(forceDumpCommand).build())
        } catch (e: Exception) {
          logger.warn("Failed to execute force dump on device command", e)
        }
      }
    } else {
      profilers.ideServices.poolExecutor.execute { heapDumper.triggerAndAnalyze() }
    }
  }

  fun setIsRecording(isRecording: Boolean) {
    _isRecording.value = isRecording
  }

  fun setObjectRetainedCount(objectRetainedCount: Int) {
    logger.info("Setting retained object count: $objectRetainedCount")
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
    if (newLeak != null && _selectedLeak.value != newLeak) {
      myTaskTracker.trackLeakCanaryUiAction(LeakCanaryUiAction.NEW_LEAK_SELECTED)
    }
    _selectedLeak.value = newLeak
  }

  private fun checkPresenceAndFetchThreshold() {
    if (!isLeakCanaryMilestone2Enabled) {
      checkLeakCanaryPresence()
    } else if (leakcanaryMode == StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE) {

      val thresholdValue = profilers.ideServices.temporaryProfilerPreferences.getInt("LEAKCANARY_THRESHOLD", -1)
      if (thresholdValue != -1) {
        _retainedObjectThreshold.value = thresholdValue
      }

      if (thresholdValue == -1) {
        fetchRetainedVisibleThreshold()
      }
      // Reset the state to -1
      profilers.ideServices.temporaryProfilerPreferences.setInt("LEAKCANARY_THRESHOLD", -1)
    }
  }

  /** Creates and registers transport event listeners that run from the start of the session until the end. */
  private fun registerLeakCanaryListeners() {
    val startTime = profilers.session.startTimestamp
    lastAnalysisTimestampMs = TimeUnit.NANOSECONDS.toMillis(startTime)

    if (leakcanaryMode == StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE) {
      // This is for shark running on the device and sending logcat readings.
      statusListener =
        TransportEventListener(
          eventKind = Common.Event.Kind.LEAKCANARY_ANALYSIS,
          executor = profilers.ideServices.mainExecutor,
          streamId = { profilers.session.streamId },
          processId = { profilers.session.pid },
          startTime = { startTime },
          callback = { event -> false.also { leakDetected(event) } },
        )
      profilers.transportPoller.registerListener(statusListener)
    } else {
      // This is for shark running on host and we are getting retained object count from the studio leakcanary integration library.
      objectCountListener =
        TransportEventListener(
          eventKind = Common.Event.Kind.LEAKCANARY_OBJECT_COUNT,
          executor = profilers.ideServices.mainExecutor,
          streamId = { profilers.session.streamId },
          processId = { profilers.session.pid },
          startTime = { startTime },
          callback = { event ->
            val count = event.leakcanaryObjectCount.count
            setObjectRetainedCount(count)
            if (count >= _retainedObjectThreshold.value) {
              forceHeapDump()
            }
            false
          },
        )
      profilers.transportPoller.registerListener(objectCountListener)
    }

    deviceErrorListener =
      TransportEventListener(
        eventKind = Common.Event.Kind.LEAKCANARY_DEVICE_ERROR,
        executor = profilers.ideServices.mainExecutor,
        streamId = { profilers.session.streamId },
        processId = { profilers.session.pid },
        startTime = { startTime },
        callback = { event ->
          val errorType = event.leakcanaryDeviceError.errorType
          when (errorType) {
            LEAKCANARY_ERROR_APP_CONTEXT_NULL -> {
              myTaskTracker.trackStartTaskFailed(TaskStartFailedMetadata(leakCanaryStartStatus = LeakCanaryStartErrorCode.APP_CONTEXT_NULL))
              handleLeakCanaryFatalError(LeakCanaryProcessingErrorCode.UNKNOWN_ERROR, "Failed to get Application Context on device.")
            }
            LEAKCANARY_ERROR_BROADCAST_DELIVERY_FAILED -> {
              handleLeakCanaryFatalError(LeakCanaryProcessingErrorCode.BROADCAST_DELIVERY_FAILED, "Failed to deliver broadcast to the app.")
            }
            LEAKCANARY_ERROR_LOGCAT_PARSING_FAILURE -> {
              myTaskTracker.trackProcessingTaskFailed(
                TaskProcessingFailedMetadata(leakCanaryProcessingStatus = LeakCanaryProcessingErrorCode.PARSING_FAILURE)
              )
              handleLeakCanaryFatalError(LeakCanaryProcessingErrorCode.PARSING_FAILURE, "Failed to parse LeakCanary logcat trace.")
            }
            else -> {}
          }
          false
        },
      )
    profilers.transportPoller.registerListener(deviceErrorListener)
  }

  private fun fetchRetainedVisibleThreshold() {
    val fetchThresholdCommand =
      Commands.Command.newBuilder()
        .setStreamId(profilers.session.streamId)
        .setPid(profilers.session.pid)
        .setSessionId(profilers.session.sessionId)
        .setType(Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD)
        .build()

    profilers.ideServices.poolExecutor.execute {
      val commandIdFuture = CompletableFuture<Int>()
      val listener =
        TransportEventListener(
          eventKind = Common.Event.Kind.LEAKCANARY_THRESHOLD,
          executor = profilers.ideServices.poolExecutor,
          streamId = { profilers.session.streamId },
          filter = { event ->
            val targetCommandId = commandIdFuture.getNow(-1)
            targetCommandId != -1 && event.commandId == targetCommandId
          },
          processId = { profilers.session.pid },
          callback = { event ->
            val threshold = event.leakcanaryThreshold.threshold
            profilers.ideServices.mainExecutor.execute { _retainedObjectThreshold.value = threshold }
            true // Unregister listener
          },
        )
      profilers.transportPoller.registerListener(listener)

      try {
        val response =
          profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(fetchThresholdCommand).build())
        commandIdFuture.complete(response.commandId)
      } catch (e: Exception) {
        logger.warn("Failed to fetch retained visible threshold", e)
        profilers.transportPoller.unregisterListener(listener)
      }
    }
  }

  private fun checkLeakCanaryPresence() {
    val command =
      Commands.Command.newBuilder()
        .apply {
          streamId = profilers.session.streamId
          pid = profilers.session.pid
          sessionId = profilers.session.sessionId
          type = Commands.Command.CommandType.CHECK_LEAKCANARY_PRESENT
        }
        .build()

    profilers.ideServices.poolExecutor.execute {
      val response = profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())

      val listener =
        TransportEventListener(
          eventKind = Common.Event.Kind.LEAKCANARY_PRESENCE_CHECK,
          executor = profilers.ideServices.poolExecutor,
          filter = { it.commandId == response.commandId },
          streamId = { profilers.session.streamId },
          processId = { profilers.session.pid },
          callback = { event ->
            val isPresent = event.leakcanaryPresenceCheck.isPresent
            logger.info("LeakCanary presence check returned: $isPresent")
            profilers.ideServices.mainExecutor.execute { _isLeakCanaryPresent.value = isPresent }
            true // Unregister listener after first event.
          },
        )
      profilers.transportPoller.registerListener(listener)
    }
  }

  private fun deregisterLeakCanaryListeners() {
    if (::statusListener.isInitialized) {
      profilers.transportPoller.unregisterListener(statusListener)
    }
    if (::objectCountListener.isInitialized) {
      profilers.transportPoller.unregisterListener(objectCountListener)
    }
    if (::deviceErrorListener.isInitialized) {
      profilers.transportPoller.unregisterListener(deviceErrorListener)
    }
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
   *
   * @param event: The LeakCanary logcat event.
   */
  private fun leakDetected(event: Common.Event) {
    val analysis = Analysis.fromString(event.leakcanaryAnalysis.data) ?: return
    if (handleRetainedObject(analysis)) return
    if (handleAnalysisProgress(analysis)) return
    setObjectRetainedCount(0)
    handleLeakAnalysis(analysis)
  }

  private fun handleRetainedObject(analysis: Analysis): Boolean {
    if (analysis !is AnalysisUpdate) return false
    val retainedObjectsRegex = """Found (\d+) objects? retained""".toRegex()
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

  private fun handleLeakAnalysis(
    analysis: Analysis?,
    hprofFileSizeBytes: Long? = null,
    downloadDurationMs: Long? = null,
    heapDumpAnalysisTimeMs: Long? = null,
  ) {
    if (analysis == null) {
      myTaskTracker.trackProcessingTaskFailed(
        TaskProcessingFailedMetadata(leakCanaryProcessingStatus = LeakCanaryProcessingErrorCode.PARSING_FAILURE)
      )
      return
    }
    setAnalysisProgress(0)

    if (analysis is AnalysisSuccess) {
      addLeaks(analysis.leaks)

      val totalRecordingTimeMs = System.currentTimeMillis() - lastAnalysisTimestampMs
      trackLeakAnalysisTelemetry(analysis, hprofFileSizeBytes, downloadDurationMs, heapDumpAnalysisTimeMs, totalRecordingTimeMs)
      lastAnalysisTimestampMs = System.currentTimeMillis()
    } else if (analysis is AnalysisFailure) {
      // There is failure in leak analysis.
      logger.warn("Leak analysis failure", analysis.exception)
    }

    // The first leak is selected, so its leakTrace is displayed by default in UI.
    if (_selectedLeak.value == null && _leaks.value.isNotEmpty()) {
      onLeakSelection(_leaks.value.first())
    }

    if (_isStopping.value) {
      stopListening()
    }
  }

  /**
   * Starts or stops LeakCanary logcat tracking & retained object count tracking.
   *
   * @param session: The profiler session.
   * @param enable: true to start tracking, false to stop tracking.
   * @param endSession: true to end the session when stopping tracking.
   */
  private fun toggleLeakCanaryTracking(session: Common.Session, enable: Boolean, endSession: Boolean) {
    val startLeakCanaryTaskData = StartLeakCanaryTaskData.newBuilder().setMode(leakcanaryMode).build()

    val cmd =
      Commands.Command.newBuilder().apply {
        streamId = session.streamId
        pid = session.pid
        sessionId = session.sessionId
        if (enable) {
          type = Commands.Command.CommandType.START_LEAKCANARY_TASK
          setStartLeakcanaryTask(startLeakCanaryTaskData)
        } else {
          type = Commands.Command.CommandType.STOP_LEAKCANARY_TASK
        }
      }
    profilers.ideServices.poolExecutor.execute {
      try {
        profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(cmd).build())
      } catch (e: Exception) {
        logger.warn("Failed to toggle LeakCanary tracking", e)
      }
    }
  }

  // Setting it to UNKNOWN_STAGE since stage usage is avoided in task-based ux.
  override fun getStageType(): AndroidProfilerEvent.Stage = AndroidProfilerEvent.Stage.UNKNOWN_STAGE

  fun loadFromPastSession(startTimestamp: Long, endTimeStamp: Long, session: Common.Session) {
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

  private fun getAllLeakCanaryEvents(session: Common.Session, startTimestamp: Long, endTimeStamp: Long): List<Analysis> {
    val eventList = getLeaksFromRange(profilers.client, session, Range(startTimestamp.toDouble(), endTimeStamp.toDouble()))
    return eventList.mapNotNull { event -> Analysis.fromString(event.leakcanaryAnalysis.data) }
  }

  private fun trackLeakAnalysisTelemetry(
    analysis: AnalysisSuccess,
    hprofFileSizeBytes: Long?,
    downloadDurationMs: Long?,
    heapDumpAnalysisTimeMs: Long?,
    totalRecordingTimeMs: Long,
  ) {
    var totalRetainedBytes = 0L
    var noCount = 0
    var maybeCount = 0
    var yesCount = 0
    var occurrences = 0
    var libraryLeak = false

    for (leak in analysis.leaks) {
      occurrences += leak.leakTraceCount
      totalRetainedBytes += leak.retainedByteSize.toLong().coerceAtLeast(0L)
      if (leak.type == LeakType.LIBRARY_LEAKS) {
        libraryLeak = true
      }

      leak.displayedLeakTrace.firstOrNull()?.nodes?.forEach { node ->
        when (node.leakingStatus) {
          LeakingStatus.NO -> noCount++
          LeakingStatus.UNKNOWN -> maybeCount++
          LeakingStatus.YES -> yesCount++
          else -> {}
        }
      }
    }

    val leakAnalysisPayload =
      LeakCanaryLeakAnalysis(
        retainedObjectsCount = analysis.leaks.size,
        occurrencesCount = occurrences,
        estimatedMemoryLeakedBytes = totalRetainedBytes,
        leakingNoRows = noCount,
        leakingMaybeRows = maybeCount,
        leakingYesRows = yesCount,
        heapDumpAnalysisTimeMs = heapDumpAnalysisTimeMs,
        hprofFileSizeBytes = hprofFileSizeBytes,
        hprofDownloadDurationMs = downloadDurationMs,
        totalRecordingTimeMs = totalRecordingTimeMs,
        isLibraryLeak = libraryLeak,
      )
    myTaskTracker.trackLeakCanaryAnalysis(leakAnalysisPayload)
  }

  fun goToDeclaration(node: Node) {
    myTaskTracker.trackLeakCanaryUiAction(LeakCanaryUiAction.GO_TO_DECLARATION_CLICKED)
    val codeLocationSupplier: () -> CodeLocation = { CodeLocation.Builder(node.className.removeSuffix("[]")).build() }
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

  fun handleLeakCanaryFatalError(error: LeakCanaryProcessingErrorCode, message: String) {
    logger.error("LeakCanary Fatal Error ($error): $message")
    myTaskTracker.trackProcessingTaskFailed(TaskProcessingFailedMetadata(leakCanaryProcessingStatus = error))

    // Show IDE balloon notification
    profilers.ideServices.showNotification(Notification(Notification.Severity.ERROR, "LeakCanary Task Failed", message, null))

    // Safely tear down the task
    stopListening(isUserInitiated = false)
  }

  fun trackUiAction(action: LeakCanaryUiAction) {
    myTaskTracker.trackLeakCanaryUiAction(action)
  }

  companion object {
    /**
     * Fetches all LeakCanary logcat dump events within a given session. It returns leaks that are within a given range, which is provided
     * by the logcat end status events fetched by `getLeakCanaryLogcatInfo`.
     */
    fun getLeaksFromRange(profilerClient: ProfilerClient, session: Common.Session, range: Range): List<Common.Event> {
      return profilerClient.transportClient
        .getEventGroups(
          Transport.GetEventGroupsRequest.newBuilder()
            .setStreamId(session.streamId)
            .setPid(session.pid)
            .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS)
            .setFromTimestamp(range.min.toLong())
            .setToTimestamp(range.max.toLong())
            .build()
        )
        .groupsList
        .flatMap { group -> group.eventsList.toList() }
    }

    /**
     * Fetches all LeakCanary logcat dump events within a given session. It returns leaks that are within a given range, which is the
     * session start and end time provided by `getSessionArtifacts`.
     */
    fun getLeakCanaryAnalysisInfo(profilerClient: ProfilerClient, session: Common.Session, range: Range): List<Common.Event> {
      return profilerClient.transportClient
        .getEventGroups(
          Transport.GetEventGroupsRequest.newBuilder()
            .setStreamId(session.streamId)
            .setPid(session.pid)
            .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS)
            .setFromTimestamp(range.min.toLong())
            .setToTimestamp(range.max.toLong())
            .build()
        )
        .groupsList
        .flatMap { group -> group.eventsList.toList() }
        .filter { event -> event.isEnded }
    }

    /**
     * Extracts the class name from a Leak object, prioritizing the leaking class if available. If the full class path is long, it shortens
     * it to the last two segments.
     *
     * @param leak The Leak object to extract the class name from.
     * @return The extracted class name or an empty string if no leak or class name is found.
     */
    fun getLeakClassName(leak: Leak?): String {
      if (leak?.displayedLeakTrace == null || leak.displayedLeakTrace.isEmpty()) {
        return ""
      }
      val leakTrace = leak.displayedLeakTrace.first()
      val suspectNodeList =
        leakTrace.nodes.filterIndexed { index, node ->
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

    private const val KEY_LEAKCANARY_BANNER_DO_NOT_SHOW = "leakcanary.banner.donotshow"
  }

  override fun update(elapsedNs: Long) {
    _elapsedNs.value += elapsedNs
  }
}

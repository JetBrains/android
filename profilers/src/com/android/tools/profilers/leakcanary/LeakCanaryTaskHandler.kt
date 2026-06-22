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

import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.StartLeakCanaryTaskData
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.Notification
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.SupportLevel
import com.android.tools.profilers.cpu.config.LeakCanaryConfiguration
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError.StartTaskSelectionErrorCode
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.analytics.LeakCanaryStartErrorCode
import com.android.tools.profilers.tasks.analytics.TaskAttachmentPoint
import com.android.tools.profilers.tasks.analytics.TaskDataOrigin
import com.android.tools.profilers.tasks.analytics.TaskMetadata
import com.android.tools.profilers.tasks.analytics.TaskPreflightTracker
import com.android.tools.profilers.tasks.analytics.TaskStartFailedMetadata
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.leakcanary.LeakCanaryTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerUtils
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.SingleArtifactTaskHandler
import fleet.util.logging.logger
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = logger<LeakCanaryTaskHandler>()

class LeakCanaryTaskHandler(private val sessionsManager: SessionsManager) : SingleArtifactTaskHandler<LeakCanaryModel>(sessionsManager) {

  private val profilers = sessionsManager.studioProfilers

  enum class LeakCanaryCheckState {
    IDLE,
    CHECKING,
    PRESENT,
    NOT_PRESENT,
    TIMEOUT,
    REFLECTION_FAILED,
  }

  // Thread-safe state flags to track the async verification process across UI and background threads.
  private val lastCheckedProcessId = AtomicReference<String?>(null)
  private val isCheckInProgress = AtomicBoolean(false)
  private val isPresent = AtomicBoolean(false)

  // Cache to store process IDs (streamId:pid) that have successfully passed the LeakCanary presence check.
  private val successfullyCheckedProcessIds = ConcurrentHashMap.newKeySet<String>()

  // UI state Flow - used to trigger UI redraws.
  private val _checkState = MutableStateFlow(LeakCanaryCheckState.IDLE)
  val checkState = _checkState.asStateFlow()

  private var pendingArgs: LeakCanaryTaskArgs? = null

  private fun createPreFlightTracker(): TaskPreflightTracker {
    return TaskPreflightTracker(
      profilers,
      TaskMetadata(
        ProfilerTaskType.LEAKCANARY,
        0L, // Task ID/Session ID is 0 because the session hasn't started yet
        TaskDataOrigin.NEW,
        TaskAttachmentPoint.EXISTING_PROCESS,
        ExposureLevel.UNKNOWN, // Let it be unknown for pre-flight
        null,
      ),
    )
  }

  override fun setupStage() {
    val studioProfilers = sessionsManager.studioProfilers
    val stage = LeakCanaryModel(studioProfilers)
    pendingArgs?.let { stage.setLeakCanaryMode(it.leakCanaryMode) }
    // Set the new stage to be the current stage in the Profiler.
    studioProfilers.stage = stage
    // Set the new stage to be this task handler's stage, which can now be used ot start and stop captures.
    super.stage = stage
  }

  /**
   * Fetches the LeakCanary retained visible threshold from the device. This is a blocking call that waits up to 7 seconds for the app to
   * respond with the threshold via the LEAKCANARY_THRESHOLD event.
   *
   * @return the threshold value (> 0 on success, -1 on reflection failure, 0 on timeout/not found).
   */
  private fun fetchThresholdAndWait(): Int {
    val fetchThresholdCommand =
      Commands.Command.newBuilder()
        .setStreamId(profilers.session.streamId)
        .setPid(profilers.session.pid)
        .setSessionId(profilers.session.sessionId)
        .setType(Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD)
        .build()

    val commandIdFuture = CompletableFuture<Int>()
    val thresholdFetchedFuture = CompletableFuture<Int>()

    val listener =
      TransportEventListener(
        eventKind = Common.Event.Kind.LEAKCANARY_THRESHOLD,
        executor = profilers.ideServices.poolExecutor,
        streamId = { profilers.session.streamId },
        processId = { profilers.session.pid },
        filter = { event ->
          val targetCommandId = commandIdFuture.getNow(-1)
          targetCommandId != -1 && event.commandId == targetCommandId
        },
        callback = { event ->
          val threshold = event.leakcanaryThreshold.threshold
          if (threshold > 0) {
            profilers.ideServices.mainExecutor.execute {
              profilers.ideServices.temporaryProfilerPreferences.setInt("LEAKCANARY_THRESHOLD", threshold)
              logger.info("Stored LeakCanary threshold $threshold in preferences.")
            }
          } else if (threshold == REFLECTION_FAILED_THRESHOLD) {
            logger.info("Studio_leakCanary library present but reflection failed.")
          }
          thresholdFetchedFuture.complete(threshold)
          true // Unregister listener
        },
      )
    profilers.transportPoller.registerListener(listener)

    try {
      val response =
        profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(fetchThresholdCommand).build())
      logger.info(
        "Sent GET_LEAKCANARY_THRESHOLD command to transport. streamId: ${fetchThresholdCommand.streamId}, pid: ${fetchThresholdCommand.pid}, sessionId: ${fetchThresholdCommand.sessionId}"
      )
      commandIdFuture.complete(response.commandId)

      // Block the background thread until the listener catches the event or we hit the 7-second timeout.
      return thresholdFetchedFuture.get(THRESHOLD_FETCH_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      logger.warn(
        "Timed out waiting for LEAKCANARY_THRESHOLD response after $THRESHOLD_FETCH_TIMEOUT_MS ms. streamId: ${fetchThresholdCommand.streamId}, pid: ${fetchThresholdCommand.pid}, sessionId: ${fetchThresholdCommand.sessionId}"
      )
      return 0
    } catch (e: Exception) {
      logger.warn(
        e,
        "Failed to send GET_LEAKCANARY_THRESHOLD command. streamId: ${fetchThresholdCommand.streamId}, pid: ${fetchThresholdCommand.pid}, sessionId: ${fetchThresholdCommand.sessionId}",
      )
      return 0
    } finally {
      // Always clean up the listener to prevent memory leaks.
      profilers.transportPoller.unregisterListener(listener)
    }
  }

  /**
   * Attempts to attach the JVMTI agent to the target process and then sequentially fetches the LeakCanary threshold. This operates on a
   * background thread to prevent IDE freezes or NetworkOnMainThread exceptions. Its guaranteed that the agent is attached, and the
   * threshold is fetched before we transition the UI to the active task state.
   */
  private fun attachAgentAndFetchThresholdAsync(streamId: Long, process: Common.Process, onComplete: () -> Unit) {
    profilers.ideServices.poolExecutor.execute {
      // Clear the temporary preference before attaching so that old values don't leak into the next session
      profilers.ideServices.temporaryProfilerPreferences.setInt("LEAKCANARY_THRESHOLD", -1)
      val tracker = createPreFlightTracker()

      val isAttached = attachAgentAndWait(profilers, streamId, process)
      if (isAttached) {
        val fetchedThreshold = fetchThresholdAndWait()
        if (fetchedThreshold > 0) {
          profilers.ideServices.mainExecutor.execute { onComplete() }
        } else if (fetchedThreshold == REFLECTION_FAILED_THRESHOLD) {
          handleStartupFailure(
            tracker,
            LeakCanaryStartErrorCode.UNKNOWN_ERROR,
            "Studio's LeakCanary integration library failed to attach. Required internal APIs are missing from the compiled app.",
          )
        } else {
          handleStartupFailure(
            tracker,
            LeakCanaryStartErrorCode.LIBRARY_NOT_INSTALLED_TIMEOUT,
            "Failed to detect LeakCanary library in the app.",
          )
        }
      } else {
        handleStartupFailure(tracker, LeakCanaryStartErrorCode.AGENT_ATTACH_FAILED, "Profiler agent failed to attach to the process.")
      }
    }
  }

  /** Helper method to abort the broken task UI, log telemetry, present an error balloon, and cleanly terminate the session. */
  private fun handleStartupFailure(tracker: TaskPreflightTracker, errorCode: LeakCanaryStartErrorCode, errorMessage: String) {
    logger.error(errorMessage)
    tracker.trackPreflightCheckFailed(TaskStartFailedMetadata(leakCanaryStartStatus = errorCode))
    profilers.ideServices.showNotification(Notification(Notification.Severity.ERROR, "LeakCanary Task Failed", errorMessage, null))
    profilers.ideServices.mainExecutor.execute {
      profilers.sessionsManager.endCurrentSession()
      profilers.ideServices.closeTaskTab(ProfilerTaskType.LEAKCANARY)
      pendingArgs = null
    }
  }

  /** Helper method to finalize the task entrance by calling the super class and resetting pending arguments. */
  private fun enterStage(args: TaskArgs): Boolean {
    logEnterStage()
    val result = super.enter(args)
    pendingArgs = null
    return result
  }

  override fun enter(args: TaskArgs): Boolean {
    logger.info("Entering LeakCanary task.")
    if (args !is LeakCanaryTaskArgs) {
      logger.error("TaskArgs must be of type LeakCanaryTaskArgs. Actual type: ${args.javaClass.name}")
      return false
    }
    pendingArgs = args

    if (sessionsManager.isSessionAlive && args.isFromStartup) {
      val streamId = profilers.session.streamId
      val process = profilers.process

      // If this is a live ongoing task from startup, ensure we have a valid target process to attach the agent to.
      // We must attach the agent and fetch the threshold before the task starts.
      if (process != null && process != Common.Process.getDefaultInstance()) {
        attachAgentAndFetchThresholdAsync(streamId, process) { enterStage(args) }
        return true
      } else {
        logger.warn("Cannot start LeakCanary task: Valid process not found for startup task.")
        profilers.ideServices.mainExecutor.execute { profilers.sessionsManager.endCurrentSession() }
        pendingArgs = null
        return false
      }
    }

    // For dead sessions (imported/completed tasks) or the "Start with 'Now'" flow,
    // the agent is either unneeded or already attached via pre-flight checks.
    return enterStage(args)
  }

  override fun startTask(args: TaskArgs) {
    logger.info("Starting LeakCanary task.")
    if (stage == null) {
      handleError("Cannot start the task as the InterimStage was null")
      return
    }

    if (args.isFromStartup) {
      TaskHandlerUtils.executeTaskAction(action = { stage!!.startListening() }, errorHandler = ::handleError)
      return
    }

    super.startTask(args)
  }

  override fun startCapture(stage: LeakCanaryModel) {
    stage.startListening()
  }

  override fun stopCapture(stage: LeakCanaryModel) {
    logger.info("Stopping active LeakCanary capture.")
    stage.stopListening()
  }

  override fun loadTask(args: TaskArgs): Boolean {
    logger.info("Loading LeakCanary task from historical artifact.")
    if (args !is LeakCanaryTaskArgs) {
      handleError("The task arguments (TaskArgs) supplier are not of the expected type (LeakCanaryTaskArgs)")
      return false
    }

    val leakCanaryArtifact = args.getLeakCanaryArtifact()
    if (leakCanaryArtifact == null) {
      handleError("The task arguments (LeakCanaryTaskArgs) supplied do not contains a valid artifact to load")
      return false
    }
    loadCapture(leakCanaryArtifact)
    return true
  }

  override fun getTaskName() = "LeakCanary"

  override fun supportsArtifact(artifact: SessionArtifact<*>?): Boolean {
    return artifact is LeakCanarySessionArtifact
  }

  override fun createStartTaskArgs(isStartupTask: Boolean): LeakCanaryTaskArgs {
    val featureLevel = profilers.device?.featureLevel ?: 0
    val configs = sessionsManager.studioProfilers.ideServices.getTaskCpuProfilerConfigs(featureLevel)
    val leakCanaryConfig = configs.filterIsInstance<LeakCanaryConfiguration>().firstOrNull()
    val mode = leakCanaryConfig?.mode ?: StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE
    logger.info("Creating start task args in mode: $mode")
    return LeakCanaryTaskArgs(isStartupTask, null, mode)
  }

  override fun createLoadingTaskArgs(artifact: SessionArtifact<*>) = LeakCanaryTaskArgs(false, artifact as LeakCanarySessionArtifact)

  /**
   * Instantly updates the state to 'PRESENT'. This marks the current process as active so that delayed background checks from previously
   * clicked apps don't accidentally overwrite the UI.
   */
  private fun updateStateToCachedSuccess(processId: String) {
    logger.info("LeakCanary check state: CACHED_SUCCESS for $processId")
    lastCheckedProcessId.set(processId)
    isPresent.set(true)
    isCheckInProgress.set(false)
    _checkState.value = LeakCanaryCheckState.PRESENT
  }

  /**
   * Resets the verification state variables to their initial defaults. This is used to cleanly abort and reset the check when the currently
   * selected device or process becomes invalid or unsupported.
   */
  private fun updateStateToIdle() {
    lastCheckedProcessId.set(null)
    _checkState.value = LeakCanaryCheckState.IDLE
    isCheckInProgress.set(false)
    isPresent.set(false)
  }

  /**
   * Marks the beginning of a new verification check for the given process, locking the state to 'CHECKING' and ensuring the UI shows the
   * loading indicator.
   */
  private fun updateStateToChecking(processId: String) {
    logger.info("LeakCanary check state: CHECKING for $processId")
    lastCheckedProcessId.set(processId)
    _checkState.value = LeakCanaryCheckState.CHECKING
    isCheckInProgress.set(true)
    isPresent.set(false)
  }

  /** Safely transitions the UI state to TIMEOUT if the verification process fails or takes too long. */
  private fun updateStateToTimeout(processId: String) {
    profilers.ideServices.mainExecutor.execute {
      // The isCheckInProgress guard prevents a delayed timeout from overwriting a successful check.
      if (isProcessLastChecked(processId) && isCheckInProgress.get()) {
        logger.warn("LeakCanary check state: TIMEOUT for $processId")
        isPresent.set(false)
        isCheckInProgress.set(false)
        _checkState.value = LeakCanaryCheckState.TIMEOUT
      }
    }
  }

  /** Called when the verification process successfully finishes. Updates the UI state with the result. */
  private fun updateStateToCompleted(processId: String, threshold: Int, tracker: TaskPreflightTracker) {
    profilers.ideServices.mainExecutor.execute {
      val found = threshold > 0

      // Always cache successes, even if the user navigated away while the background check was running.
      if (found) {
        successfullyCheckedProcessIds.add(processId)
      }

      if (isProcessLastChecked(processId)) {
        isPresent.set(found)
        isCheckInProgress.set(false)

        _checkState.value =
          when {
            found -> LeakCanaryCheckState.PRESENT
            threshold == REFLECTION_FAILED_THRESHOLD -> LeakCanaryCheckState.REFLECTION_FAILED
            else -> LeakCanaryCheckState.NOT_PRESENT
          }

        if (!found) {
          val errorCode =
            if (threshold == REFLECTION_FAILED_THRESHOLD) LeakCanaryStartErrorCode.UNKNOWN_ERROR
            else LeakCanaryStartErrorCode.LIBRARY_NOT_INSTALLED_TIMEOUT
          tracker.trackPreflightCheckFailed(TaskStartFailedMetadata(leakCanaryStartStatus = errorCode))
        }

        logger.info("PROFILER: Check finished for $processId. State: ${_checkState.value}")
      }
    }
  }

  /**
   * Helper method to verify if the provided process ID matches the one currently being checked. This is used to ensure delayed background
   * callbacks do not overwrite state for a newer process selection.
   */
  private fun isProcessLastChecked(processId: String): Boolean {
    return lastCheckedProcessId.get() == processId
  }

  /**
   * Verifies whether the selected device and process support the LeakCanary task.
   *
   * By default, the LeakCanary task requires a debuggable process.
   *
   * This method also initiates and monitors an asynchronous pre-start verification check. It attempts to attach a JVMTI agent to the
   * running process to confirm if the `studio-leakcanary` library is installed and fetch its threshold. During this check, it returns
   * transient error states (like [StartTaskSelectionErrorCode.LEAKCANARY_CHECK_IN_PROGRESS] or
   * [StartTaskSelectionErrorCode.LEAKCANARY_CHECK_TIMEOUT]) to proactively disable the "Start" button in the UI until the presence is
   * successfully confirmed.
   *
   * @return null if the task is fully supported and verified; otherwise, returns an error object indicating why it cannot start.
   */
  override fun checkSupportForDeviceAndProcess(device: Common.Device, process: Common.Process): StartTaskSelectionError? {
    if (profilers.ideServices.isDebuggerAttached(device.serial, process.pid)) {
      logger.info("LeakCanary unsupported: Debugger is attached to ${process.pid}")
      updateStateToIdle()
      return StartTaskSelectionError(StartTaskSelectionErrorCode.TASK_HAS_DEBUGGER_ATTACHED)
    }
    val isFeatureSupported = SupportLevel.of(process.exposureLevel).isFeatureSupported(SupportLevel.Feature.MEMORY_LEAK_WITH_LEAKCANARY)
    if (!isFeatureSupported) {
      logger.info("LeakCanary unsupported: Process ${process.pid} is not profileable")
      updateStateToIdle()
      return StartTaskSelectionError(StartTaskSelectionErrorCode.TASK_REQUIRES_DEBUGGABLE_PROCESS)
    }
    if (process.uid in FIRST_PCC_UID..LAST_PCC_UID) {
      logger.info("LeakCanary unsupported: Process ${process.pid} is a PCC process")
      updateStateToIdle()
      return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_NOT_SUPPORTED_FOR_PCC)
    }
    // Bypass LeakCanary presence check in testing mode because tests use dummy apps.
    if (profilers.ideServices.featureConfig.isTestingModeEnabled) {
      return null
    }

    val streamId = profilers.getStreamId(device)
    val processId = "${streamId}:${process.pid}:${process.name}"

    // If this process has already been successfully verified, instantly return success (null).
    // We do not cache failures, as the app might initialize LeakCanary later in its lifecycle.
    if (successfullyCheckedProcessIds.contains(processId)) {
      if (!isProcessLastChecked(processId)) {
        updateStateToCachedSuccess(processId)
      }
      return null
    }

    // If the user selects a new process, or if a previous check timed out and cleared its ID,
    // lock the state to 'CHECKING' and immediately send off the background verification task.
    // This guard ensures we only launch the expensive background agent attachment once per selection.
    if (!isProcessLastChecked(processId)) {
      verifyLeakCanaryPresenceAsync(device, process, streamId)
      return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_CHECK_IN_PROGRESS)
    }

    // If the background task is still running, keep the UI in the "Checking..." loading state.
    if (isCheckInProgress.get()) {
      return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_CHECK_IN_PROGRESS)
    }

    // If the check has finished but LeakCanary wasn't found, map the internal failure reason
    // to the appropriate UI error message (either a timeout warning or a missing dependency error).
    if (!isPresent.get()) {
      if (_checkState.value == LeakCanaryCheckState.TIMEOUT) {
        return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_CHECK_TIMEOUT)
      } else if (_checkState.value == LeakCanaryCheckState.REFLECTION_FAILED) {
        return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_REFLECTION_FAILED)
      }

      val preferredProcess = sessionsManager.studioProfilers.preferredProcessName
      val selectedProcess = process.name

      // To determine if the user is trying to profile an external APK vs their currently opened project,
      // we compare the selected process against the IDE's preferredProcessName.
      // We split by ':' to safely handle multi-process applications (e.g., matching 'com.app:leakcanary' to 'com.app').
      val isProjectMatch = !preferredProcess.isNullOrEmpty() && preferredProcess.split(':').first() == selectedProcess.split(':').first()

      if (!isProjectMatch) {
        return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_NOT_FOUND_EXTERNAL_PROCESS)
      }
      return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_NOT_FOUND)
    }
    return null
  }

  /**
   * Orchestrates the asynchronous verification of LeakCanary's presence on the device.
   *
   * This method performs a complex sequence of operations on background threads:
   * 1. Starts a safety timer ([LEAKCANARY_CHECK_TIMEOUT_MS]) to ensure the IDE doesn't hang if the app is heavily throttled or completely
   *    unresponsive.
   * 2. Attempts to attach the JVMTI agent to the selected process. If the app is frozen in the background, this will intentionally fail
   *    fast (after [AGENT_ATTACH_TIMEOUT_MS]) to prevent endless "Connection refused" log spam from the native transport daemon.
   * 3. Once attached, sends a gRPC command ([Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD]) to the agent, which broadcasts an
   *    intent to the `studio-leakcanary` library inside the app.
   * 4. Listens for the response event, caches the threshold for the task, and updates the UI state.
   *
   * Any failure or timeout during this sequence gracefully resets the internal state, allowing the user to retry the check by bringing the
   * app to the foreground and reselecting it.
   */
  private fun verifyLeakCanaryPresenceAsync(device: Common.Device, process: Common.Process, streamId: Long) {
    // Combine stream ID with PID and process name to ensure global uniqueness,
    // preventing false cache hits across multiple devices or PID reuse wrap-around.
    val processId = "${streamId}:${process.pid}:${process.name}"
    logger.info("PROFILER: Starting LeakCanary check for $processId")

    updateStateToChecking(processId)
    val tracker = createPreFlightTracker()

    // This is a local variable, meaning each UI click creates a completely separate,
    // independent reference. If the user clicks 3 apps rapidly, 3 separate timers will individually
    // clean up their own specific abandoned listeners without interfering with each other.
    val listenerRef = AtomicReference<TransportEventListener?>(null)
    val timer = startSafetyTimer(processId, listenerRef, tracker)

    // Offload the blocking attachment and network calls to a background thread
    profilers.ideServices.poolExecutor.execute {
      if (!attachAgentAndWait(profilers, streamId, process)) {
        logger.warn("PROFILER: Agent attachment failed or timed out for $processId")
        updateStateToTimeout(processId)
        tracker.trackPreflightCheckFailed(TaskStartFailedMetadata(leakCanaryStartStatus = LeakCanaryStartErrorCode.AGENT_ATTACH_FAILED))
        timer.cancel()
        return@execute
      }

      sendThresholdCommandAndListen(process, streamId, processId, timer, listenerRef, tracker)
    }
  }

  /**
   * Starts a safety timer that will forcefully abort the verification process if it takes too long. This protects the UI from hanging if
   * the target app is frozen or unresponsive.
   */
  private fun startSafetyTimer(
    processId: String,
    listenerRef: AtomicReference<TransportEventListener?>,
    tracker: TaskPreflightTracker,
  ): Timer {
    val timer = Timer()
    timer.schedule(
      object : TimerTask() {
        override fun run() {
          if (isProcessLastChecked(processId) && isCheckInProgress.get()) {
            logger.info("PROFILER: Safety timeout for $processId. Failing check.")
            updateStateToTimeout(processId)
            tracker.trackPreflightCheckFailed(TaskStartFailedMetadata(leakCanaryStartStatus = LeakCanaryStartErrorCode.TRANSPORT_TIMEOUT))
          }
          listenerRef.get()?.let { profilers.transportPoller.unregisterListener(it) }
          timer.cancel()
        }
      },
      LEAKCANARY_CHECK_TIMEOUT_MS,
    )
    return timer
  }

  /** Sends the command to the device to request the LeakCanary threshold and sets up a listener to catch the asynchronous response. */
  private fun sendThresholdCommandAndListen(
    process: Common.Process,
    streamId: Long,
    processId: String,
    timer: Timer,
    listenerRef: AtomicReference<TransportEventListener?>,
    tracker: TaskPreflightTracker,
  ) {
    val command =
      Commands.Command.newBuilder()
        .setStreamId(streamId)
        .setPid(process.pid)
        .setSessionId(profilers.session.sessionId)
        // Instructs the native agent to broadcast an intent to the app to fetch the user's LeakCanary threshold.
        .setType(Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD)
        .build()

    val commandIdFuture = CompletableFuture<Int>()
    val listener =
      TransportEventListener(
        eventKind = Common.Event.Kind.LEAKCANARY_THRESHOLD,
        executor = profilers.ideServices.poolExecutor,
        filter = { true },
        streamId = { streamId },
        processId = { process.pid },
        // The callback evaluates all incoming LEAKCANARY_THRESHOLD events to find the one matching our command.
        callback = { event ->
          val targetCommandId = commandIdFuture.getNow(-1)
          // Only process the event if it's the direct response to the specific command we just sent.
          if (targetCommandId != -1 && event.commandId == targetCommandId) {
            timer.cancel()
            val threshold = event.leakcanaryThreshold.threshold
            // A threshold greater than 0 confirms the Studio-LeakCanary library is present and responding.
            val found = threshold > 0
            if (found) {
              profilers.ideServices.temporaryProfilerPreferences.setInt("LEAKCANARY_THRESHOLD", threshold)
              logger.info("Stored LeakCanary threshold $threshold in preferences.")
            } else if (threshold == REFLECTION_FAILED_THRESHOLD) {
              logger.info("Stuio_leakCanary library present but reflection failed for $processId")
            } else {
              logger.info("Stuio_leakCanary library not detected in app for $processId")
            }
            updateStateToCompleted(processId, threshold, tracker)
            true // Match found, unregister listener.
          } else {
            false // Match not found (e.g. stale event), keep listening.
          }
        },
      )
    // Save the listener reference instantly so the safety timer can unregister it if the app freezes.
    listenerRef.set(listener)
    profilers.transportPoller.registerListener(listener)

    try {
      // Execute the command via gRPC. This network call blocks the background thread until it finishes.
      val response = profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
      logger.info(
        "Sent GET_LEAKCANARY_THRESHOLD command to transport for $processId. streamId: ${command.streamId}, pid: ${command.pid}, sessionId: ${command.sessionId}"
      )
      commandIdFuture.complete(response.commandId)
    } catch (e: Exception) {
      logger.warn(
        "PROFILER: Failed to send GET_LEAKCANARY_THRESHOLD command for $processId. streamId: ${command.streamId}, pid: ${command.pid}, sessionId: ${command.sessionId}\n${e.message}"
      )
      // If the gRPC network call fails, we must manually clean up the listener to prevent a memory leak.
      profilers.transportPoller.unregisterListener(listener)
      updateStateToTimeout(processId)
    }
  }

  /** Log a message, indicating the entering of a profiler stage for E2E testing. */
  private fun logEnterStage() {
    logger.info("Entering LeakCanary stage")
  }

  companion object {
    const val FIRST_PCC_UID = 30000
    const val LAST_PCC_UID = 39999

    // Total timeout for the entire check sequence: Agent Attach + Broadcast Round Trip.
    // Derived from: Agent Attach (7s) + Broadcast (3s) + Buffer (4s) = 14s.
    private const val LEAKCANARY_CHECK_TIMEOUT_MS = 14000L
    // Timeout for the JVMTI agent to attach. Cold attachment can take 3-5s on slower devices.
    private const val AGENT_ATTACH_TIMEOUT_MS = 7000L
    // Timeout for fetching the LeakCanary threshold from the device.
    private const val THRESHOLD_FETCH_TIMEOUT_MS = 7000L
    // Value returned by the device if LeakCanary reflection failed due to minification.
    private const val REFLECTION_FAILED_THRESHOLD = -1

    /**
     * Attempts to attach the JVMTI agent (`libjvmtiagent.so`) to the target process. This agent acts as the low-level bridge between
     * Android Studio and the Android JVM.
     *
     * @return true if the agent attached successfully, false if the attachment failed or timed out.
     */
    fun attachAgentAndWait(profilers: StudioProfilers, streamId: Long, process: Common.Process?): Boolean {
      if (process == null) {
        logger.warn("PROFILER: Valid process not found. Cannot attach agent.")
        return false
      }
      // A future that acts as a synchronization lock. It will block the thread until the agent attaches.
      val agentAttachedFuture = CompletableFuture<Boolean>()

      // Listen for the specific AGENT event that confirms the JVMTI agent has finished loading.
      val listener =
        TransportEventListener(
          eventKind = Common.Event.Kind.AGENT,
          executor = profilers.ideServices.poolExecutor,
          streamId = { streamId },
          processId = { process.pid },
          callback = { event ->
            if (event.agentData.status == Common.AgentData.Status.ATTACHED) {
              logger.info("Agent attached for ${process.pid}. Event Timestamp: ${event.timestamp}")
              agentAttachedFuture.complete(true)
              true // Match found, unregister listener.
            } else {
              false // Match not found, keep listening.
            }
          },
        )
      profilers.transportPoller.registerListener(listener)

      // Build the gRPC command to instruct the on-device transport daemon to attach our JVMTI agent.
      val attachCommand =
        Commands.Command.newBuilder()
          .setStreamId(streamId)
          .setPid(process.pid)
          .setSessionId(profilers.session.sessionId)
          .setType(Commands.Command.CommandType.ATTACH_AGENT)
          .setAttachAgent(
            Commands.AttachAgent.newBuilder()
              // Specify the exact C++ agent library to load based on the device's CPU architecture (e.g. arm64-v8a).
              .setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.abiCpuArch))
              // The path where the agent expects to find its initial configuration data.
              .setAgentConfigPath(TransportFileManager.getAgentConfigFile())
              // Needed by the on-device daemon to correctly locate the app's local data directory.
              .setPackageName(
                process.packageName.ifEmpty { process.name.substringBefore(":").ifEmpty { profilers.preferredProcessName ?: "" } }
              )
              // Pass our strict 7-second timeout to the native C++ daemon. If the app is frozen in the
              // background, the daemon will honor this timeout and stop attempting to connect, preventing log spam.
              .setAttachTimeoutMs(AGENT_ATTACH_TIMEOUT_MS.toInt())
          )
          .build()

      try {
        // Send the command to the device.
        profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
        logger.info(
          "Sent ATTACH_AGENT command to transport. streamId: ${attachCommand.streamId}, pid: ${attachCommand.pid}, sessionId: ${attachCommand.sessionId}, agentLib: ${attachCommand.attachAgent.agentLibFileName}, agentConfig: ${attachCommand.attachAgent.agentConfigPath}, packageName: ${attachCommand.attachAgent.packageName}"
        )

        // Block the background thread until the listener catches the ATTACHED event or we hit the 7-second timeout.
        return agentAttachedFuture.get(AGENT_ATTACH_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
      } catch (e: TimeoutException) {
        logger.warn("Agent attachment timed out after ${AGENT_ATTACH_TIMEOUT_MS}ms for ${process.pid}.")
        return false
      } catch (e: Exception) {
        logger.warn(e, "Failed to send ATTACH_AGENT command or wait for ${process.pid}: ${e.message}")
        return false
      } finally {
        // Always clean up the listener to prevent memory leaks, regardless of success, failure, or thread crash.
        profilers.transportPoller.unregisterListener(listener)
      }
    }
  }
}

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
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.SupportLevel
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError.StartTaskSelectionErrorCode
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.leakcanary.LeakCanaryTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.SingleArtifactTaskHandler
import fleet.util.logging.logger
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CompletableFuture
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
  }

  // Thread-safe state flags to track the async verification process across UI and background threads.
  private val lastCheckedProcessId = AtomicReference<String?>(null)
  private val isCheckInProgress = AtomicBoolean(false)
  private val isPresent = AtomicBoolean(false)

  // UI state Flow - used to trigger UI redraws.
  private val _checkState = MutableStateFlow(LeakCanaryCheckState.IDLE)
  val checkState = _checkState.asStateFlow()

  // Total timeout for the entire check sequence: Agent Attach + Broadcast Round Trip.
  // Derived from: Agent Attach (7s) + Broadcast (2s) + Buffer (2s) = 11s.
  private val LEAKCANARY_CHECK_TIMEOUT_MS = 11000L
  // Timeout for the JVMTI agent to attach. Cold attachment can take 3-5s on slower devices.
  private val AGENT_ATTACH_TIMEOUT_MS = 7000L

  override fun setupStage() {
    val studioProfilers = sessionsManager.studioProfilers
    val stage = LeakCanaryModel(studioProfilers)
    // Set the new stage to be the current stage in the Profiler.
    studioProfilers.stage = stage
    // Set the new stage to be this task handler's stage, which can now be used ot start and stop captures.
    super.stage = stage
  }

  override fun enter(args: TaskArgs): Boolean {
    logEnterStage()
    return super.enter(args)
  }

  override fun startCapture(stage: LeakCanaryModel) {
    stage.startListening()
  }

  override fun stopCapture(stage: LeakCanaryModel) {
    stage.stopListening()
  }

  override fun loadTask(args: TaskArgs): Boolean {
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

  override fun createStartTaskArgs(isStartupTask: Boolean) = LeakCanaryTaskArgs(false, null)

  override fun createLoadingTaskArgs(artifact: SessionArtifact<*>) = LeakCanaryTaskArgs(false, artifact as LeakCanarySessionArtifact)

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
        isPresent.set(false)
        isCheckInProgress.set(false)
        _checkState.value = LeakCanaryCheckState.TIMEOUT
      }
    }
  }

  /** Called when the verification process successfully finishes. Updates the UI state with the result. */
  private fun updateStateToCompleted(processId: String, found: Boolean) {
    profilers.ideServices.mainExecutor.execute {
      if (isProcessLastChecked(processId)) {
        isPresent.set(found)
        isCheckInProgress.set(false)
        _checkState.value = if (found) LeakCanaryCheckState.PRESENT else LeakCanaryCheckState.NOT_PRESENT

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
   * When the milestone 2 feature flag is enabled, this method also initiates and monitors an asynchronous pre-start verification check. It
   * attempts to attach a JVMTI agent to the running process to confirm if the `studio-leakcanary` library is installed and fetch its
   * threshold. During this check, it returns transient error states (like [StartTaskSelectionErrorCode.LEAKCANARY_CHECK_IN_PROGRESS] or
   * [StartTaskSelectionErrorCode.LEAKCANARY_CHECK_TIMEOUT]) to proactively disable the "Start" button in the UI until the presence is
   * successfully confirmed.
   *
   * @return null if the task is fully supported and verified; otherwise, returns an error object indicating why it cannot start.
   */
  override fun checkSupportForDeviceAndProcess(device: Common.Device, process: Common.Process): StartTaskSelectionError? {
    val isFeatureSupported = SupportLevel.of(process.exposureLevel).isFeatureSupported(SupportLevel.Feature.MEMORY_LEAK_WITH_LEAKCANARY)

    if (!isFeatureSupported) {
      updateStateToIdle()
      return StartTaskSelectionError(StartTaskSelectionErrorCode.TASK_REQUIRES_DEBUGGABLE_PROCESS)
    }

    if (profilers.ideServices.featureConfig.isLeakCanaryMilestone2Enabled) {

      val streamId = profilers.getStreamId(device)
      val processId = "${streamId}:${process.pid}"

      // If the user selects a new process, or if a previous check timed out and cleared its ID,
      // lock the state to 'CHECKING' and immediately fire off the background verification task.
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
        }
        return StartTaskSelectionError(StartTaskSelectionErrorCode.LEAKCANARY_NOT_FOUND)
      }
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
    // Combine stream ID with PID to ensure global uniqueness, preventing false cache hits across multiple devices.
    val processId = "${streamId}:${process.pid}"
    logger.info("PROFILER: Starting LeakCanary check for $processId")

    updateStateToChecking(processId)

    // This is a local variable, meaning each UI click creates a completely separate,
    // independent reference. If the user clicks 3 apps rapidly, 3 separate timers will individually
    // clean up their own specific abandoned listeners without interfering with each other.
    val listenerRef = AtomicReference<TransportEventListener?>(null)
    val timer = startSafetyTimer(processId, listenerRef)

    // Offload the blocking attachment and network calls to a background thread
    profilers.ideServices.poolExecutor.execute {
      if (!attachAgentAndWait(streamId, process)) {
        logger.warn("PROFILER: Agent attachment failed or timed out for $processId")
        updateStateToTimeout(processId)
        timer.cancel()
        return@execute
      }

      sendThresholdCommandAndListen(process, streamId, processId, timer, listenerRef)
    }
  }

  /**
   * Starts a safety timer that will forcefully abort the verification process if it takes too long. This protects the UI from hanging if
   * the target app is frozen or unresponsive.
   */
  private fun startSafetyTimer(processId: String, listenerRef: AtomicReference<TransportEventListener?>): Timer {
    val timer = Timer()
    timer.schedule(
      object : TimerTask() {
        override fun run() {
          if (isProcessLastChecked(processId) && isCheckInProgress.get()) {
            logger.info("PROFILER: Safety timeout for $processId. Failing check.")
            updateStateToTimeout(processId)
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
  ) {
    val command =
      Commands.Command.newBuilder()
        .setStreamId(streamId)
        .setPid(process.pid)
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
            // A threshold greater than 0 confirms the Studio-LeakCanary library is present and responding.
            val found = event.leakcanaryThreshold.threshold > 0
            if (found) {
              profilers.ideServices.temporaryProfilerPreferences.setInt("LEAKCANARY_THRESHOLD", event.leakcanaryThreshold.threshold)
            }
            updateStateToCompleted(processId, found)
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
      commandIdFuture.complete(response.commandId)
    } catch (e: Exception) {
      logger.warn("PROFILER: Failed to send GET_LEAKCANARY_THRESHOLD command for $processId\n${e.message}")
      // If the gRPC network call fails, we must manually clean up the listener to prevent a memory leak.
      profilers.transportPoller.unregisterListener(listener)
      updateStateToTimeout(processId)
    }
  }

  /**
   * Attempts to attach the JVMTI agent (`libjvmtiagent.so`) to the target process. This agent acts as the low-level bridge between Android
   * Studio and the Android JVM.
   *
   * @return true if the agent attached successfully, false if the attachment failed or timed out.
   */
  private fun attachAgentAndWait(streamId: Long, process: Common.Process): Boolean {
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
        .setType(Commands.Command.CommandType.ATTACH_AGENT)
        .setAttachAgent(
          Commands.AttachAgent.newBuilder()
            // Specify the exact C++ agent library to load based on the device's CPU architecture (e.g. arm64-v8a).
            .setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.abiCpuArch))
            // The path where the agent expects to find its initial configuration data.
            .setAgentConfigPath(TransportFileManager.getAgentConfigFile())
            // Needed by the on-device daemon to correctly locate the app's local data directory.
            .setPackageName(process.packageName)
            // Pass our strict 7-second timeout to the native C++ daemon. If the app is frozen in the
            // background, the daemon will honor this timeout and stop attempting to connect, preventing log spam.
            .setAttachTimeoutMs(AGENT_ATTACH_TIMEOUT_MS.toInt())
        )
        .build()

    try {
      // Fire the command to the device.
      profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
      return try {
        // Block the background thread until the listener catches the ATTACHED event or we hit the 7-second timeout.
        agentAttachedFuture.get(AGENT_ATTACH_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
      } catch (e: Exception) {
        false
      }
    } catch (e: Exception) {
      return false
    } finally {
      // Always clean up the listener to prevent memory leaks, regardless of success, failure, or thread crash.
      profilers.transportPoller.unregisterListener(listener)
    }
  }

  /** Log a message, indicating the entering of a profiler stage for E2E testing. */
  private fun logEnterStage() {
    logger.info("Entering LeakCanary stage")
  }
}

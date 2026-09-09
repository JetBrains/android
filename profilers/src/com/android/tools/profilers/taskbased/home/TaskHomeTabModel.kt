/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.home

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.LogUtils
import com.android.tools.profilers.ProcessUtils.isProfileable
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.taskbased.TaskEntranceTabModel
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canTaskStartFromNow
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canTaskStartFromProcessStart
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.isTaskStartFromNowEnabled
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.isTaskStartFromProcessStartEnabled
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection
import com.android.tools.profilers.taskbased.logging.TaskLoggingUtils
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskTypeMappingUtils
import com.google.common.annotations.VisibleForTesting
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The TaskHomeTabModel serves as the data model for the task home tab. It owns the process list model to manage the available processes to
 * the user to select from, as well as current process selection. It also implements the behavior on start Profiler task button click,
 * reading the process and Profiler task selection and using such values to launch the Profiler task.
 */
class TaskHomeTabModel(profilers: StudioProfilers) : TaskEntranceTabModel(profilers) {
  private val _profilingProcessStartingPoint = MutableStateFlow(ProfilingProcessStartingPoint.UNSPECIFIED)
  val profilingProcessStartingPoint = _profilingProcessStartingPoint.asStateFlow()
  private val _isProfilingFromNowOptionEnabled = MutableStateFlow(false)
  val isProfilingFromNowOptionEnabled = _isProfilingFromNowOptionEnabled.asStateFlow()
  private val _isProfilingFromProcessStartOptionEnabled = MutableStateFlow(false)
  val isProfilingFromProcessStartOptionEnabled = _isProfilingFromProcessStartOptionEnabled.asStateFlow()
  private val _isPrevTaskStartDone = MutableStateFlow(true)
  val isPrevTaskStartDone = _isPrevTaskStartDone.asStateFlow()
  private val _unfocusedLiveTaskInEditor = MutableStateFlow<ProfilerTaskType?>(null)

  /**
   * Represents the active live profiler task currently open in an editor tab but not focused/selected by the user. Null if no live task is
   * open, the live task editor tab is currently focused, or the profiling session has ended.
   */
  val unfocusedLiveTaskInEditor = _unfocusedLiveTaskInEditor.asStateFlow()
  private val aspectObserver = AspectObserver()

  init {
    // Automatically clear the banner if the active profiling session ends or if session selection changes to a terminated session.
    profilers.sessionsManager
      .addDependency(aspectObserver)
      .onChange(SessionAspect.ONGOING_SESSION_NEWLY_ENDED) {
        _unfocusedLiveTaskInEditor.value = null
      }
      .onChange(SessionAspect.SELECTED_SESSION) {
        if (!profilers.sessionsManager.isSessionAlive) {
          _unfocusedLiveTaskInEditor.value = null
        }
      }
  }

  /**
   * Updates the unfocused live task type. The value is only set if the profiling session is currently alive; otherwise, it is cleared to
   * null.
   *
   * @param taskType the [ProfilerTaskType] of the live task running in the editor tab, or null if no live task is open or unfocused.
   */
  fun setUnfocusedLiveTaskInEditor(taskType: ProfilerTaskType?) {
    _unfocusedLiveTaskInEditor.value = if (profilers.sessionsManager.isSessionAlive) taskType else null
  }

  /**
   * The user's selections made at the moment of clicking the start profiler task button. This state needs to be stored as performing a
   * startup task is an asynchronous operation, and therefore there is a non-zero amount of time in between the user clicking the start
   * profiler task button and the app actually launching where the user can change their selections.
   *
   * Note: Once the selection state is consumed for startup task purposes, the value is reset to null.
   */
  var selectionStateOnTaskEnter: SelectionStateOnTaskEnter? = null

  val processListModel = ProcessListModel(profilers)

  /**
   * Updated the TaskStartingPointDropdown options availability, and performs auto-selection of options in certain scenarios as a
   * convenience to the user.
   *
   * To be called on process and task selection as both selections can influence the dropdown state.
   */
  override fun updateProfilingProcessStartingPointDropdown() {
    val isNowOptionEnabled = isTaskStartFromNowEnabled(selectedProcess)
    val isProcessStartOptionEnabled = isTaskStartFromProcessStartEnabled(selectedTaskType, selectedProcess, profilers)

    // Update the options' availability
    _isProfilingFromNowOptionEnabled.value = isNowOptionEnabled
    _isProfilingFromProcessStartOptionEnabled.value = isProcessStartOptionEnabled

    // Perform auto-selection of starting point dropdown options in two scenarios:
    // Scenario 1: If no selection has been made yet (such as on initial opening of the Profiler) as indicated by the UNSPECIFIED
    // selection value, then prefer selection of NOW option if its enabled (a running process is selected), otherwise choose PROCESS_START.
    if (_profilingProcessStartingPoint.value == ProfilingProcessStartingPoint.UNSPECIFIED) {
      if (isNowOptionEnabled) {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.NOW)
      } else {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.PROCESS_START)
      }
    }
    // Scenario 2: If a selection has already been made prior, then auto-select an option if it is the only one enabled.
    else {
      if (isNowOptionEnabled && !isProcessStartOptionEnabled) {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.NOW)
      } else if (!isNowOptionEnabled && isProcessStartOptionEnabled) {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.PROCESS_START)
      }
    }
  }

  fun setProfilingProcessStartingPoint(profilingProcessStartingPoint: ProfilingProcessStartingPoint) {
    _profilingProcessStartingPoint.value = profilingProcessStartingPoint
  }

  fun resetSelectionStateAndClearStartupTaskConfigs() {
    selectionStateOnTaskEnter = null
    // The call to clearStartupTaskConfigs might already be done by the `AndroidProfilerTaskLaunchContributor` after consuming the config
    // to start the task capture, but in other cases (such as when the user cancels a debuggable build via the dialog), it is not.
    // Nonetheless, it is harmless to call this method multiple times.
    profilers.ideServices.clearStartupTaskConfigs()
  }

  /**
   * Disables the start task button, and then re-enables it once whichever of the following conditions is met first: (1) there is
   * confirmation the previous task has started successfully or (2) a timeout value is met.
   */
  fun disableStartButtonUntilPrevTaskStarts() {
    _isPrevTaskStartDone.value = false
    CompletableFuture.runAsync({ waitForTaskStart(profilers) }, AndroidExecutors.getInstance().workerThreadExecutor).exceptionally {
      // If any exception occurs, default to enabling the button.
      _isPrevTaskStartDone.value = true
      null
    }
  }

  private fun waitForTaskStart(profilers: StudioProfilers) {
    val latch = CountDownLatch(1)
    val executor = Executors.newSingleThreadExecutor()

    executor.submit {
      val initialSessionId = profilers.session.sessionId
      while (initialSessionId == profilers.session.sessionId) {
        // Avoid tight loop
        Thread.sleep(100L)
      }
      latch.countDown()
    }

    try {
      // Wait for session change (indicating previous task started successfully), or timeout--whichever comes first.
      latch.await(WAIT_FOR_TASK_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      _isPrevTaskStartDone.value = true
    } finally {
      executor.shutdownNow()
    }
  }

  private fun setSelectionState() {
    selectionStateOnTaskEnter = SelectionStateOnTaskEnter(_profilingProcessStartingPoint.value, selectedTaskType)
  }

  @VisibleForTesting
  val selectedDevice: ProfilerDeviceSelection?
    get() = processListModel.selectedDevice.value

  @VisibleForTesting
  val selectedProcess: Common.Process
    get() = processListModel.selectedProcess.value

  override fun doEnterTaskButton() {
    // Save snapshot of the task home selections made just in case user changes any selection in between enter task button click and usage
    // of the selection state.
    setSelectionState()

    val profilingProcessStartingPoint = _profilingProcessStartingPoint.value

    // Reset the current task type as starting a new task should populate the current task type on processing of new session.
    profilers.sessionsManager.currentTaskType = ProfilerTaskType.UNSPECIFIED

    // Log selections to aid troubleshooting future user-reported issues.
    LogUtils.log(
      javaClass,
      TaskLoggingUtils.buildStartTaskLogMessage(selectedTaskType, profilingProcessStartingPoint, selectedProcess.isProfileable()),
    )

    when (profilingProcessStartingPoint) {
      ProfilingProcessStartingPoint.PROCESS_START -> {
        assert(canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, profilers))

        val device = selectedDevice ?: return
        val startTaskAction = Runnable {
          disableStartButtonUntilPrevTaskStarts()
          val prefersProfileable = taskGridModel.selectedTaskType.value.prefersProfileable
          profilers.ideServices.buildAndLaunchAction(prefersProfileable, device)
          // Reset process selection as process will be recreated and thus the original selection will be lost.
          processListModel.resetProcessSelection()
        }

        // The only way the user would be able to set `isProfilingFromProcessStart` to be true is if they already selected a startup-capable
        // task. Thus, it is safe to enable the corresponding startup config for the selected task.
        if (selectedTaskType == ProfilerTaskType.LEAKCANARY) {
          profilers.ideServices.addDependency(GoogleMavenArtifactId.LEAKCANARY, DependencyType.DEBUG_IMPLEMENTATION).thenAccept { success ->
            if (success) {
              startTaskAction.run()
            } else {
              // The user canceled the dialog to add the LeakCanary dependency.
              // We must clear the pending selection state; otherwise, the Profiler will
              // erroneously attempt to auto-start the task the next time the app is launched.
              LogUtils.log(javaClass, "User canceled adding LeakCanary dependency. Clearing startup task state.")
              resetSelectionStateAndClearStartupTaskConfigs()
            }
          }
        } else {
          profilers.ideServices.enableStartupTask(selectedTaskType)
          startTaskAction.run()
        }
      }

      ProfilingProcessStartingPoint.NOW -> {
        assert(canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, profilers.taskHandlers))
        disableStartButtonUntilPrevTaskStarts()
        profilers.setProcess(selectedDevice!!.device, selectedProcess, TaskTypeMappingUtils.convertTaskType(selectedTaskType), false)
      }

      else -> {
        throw IllegalStateException("Could not start profiler task with the current selections made.")
      }
    }
  }

  data class SelectionStateOnTaskEnter(
    val profilingProcessStartingPoint: ProfilingProcessStartingPoint,
    val selectedStartupTaskType: ProfilerTaskType,
  )

  enum class ProfilingProcessStartingPoint {
    UNSPECIFIED,
    NOW,
    PROCESS_START,
  }

  companion object {
    private const val WAIT_FOR_TASK_START_TIMEOUT_MS = 10000L
  }
}

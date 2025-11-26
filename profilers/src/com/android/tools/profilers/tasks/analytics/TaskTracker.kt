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
package com.android.tools.profilers.tasks.analytics

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.cpu.config.PerfettoNativeAllocationsConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.tasks.ProfilerTaskType

/**
 * A class responsible for tracking the lifecycle events of a profiler task.
 *
 * This class serves as a wrapper around [com.android.tools.profilers.analytics.FeatureTracker] for task-specific events,
 * gathering necessary metadata about the task (e.g., origin, configuration, exposure level)
 * and reporting events such as task entry, completion, or failure.
 *
 * ### Usage Restrictions
 * This class should **only be instantiated by [com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler.enter]
 * and [com.android.tools.profilers.Stage.enter]** in production. Because [TaskMetadata] is built
 * at the moment of instantiation, calling this at the wrong time (e.g., before a session is
 * fully initialized) will result in incorrect or stale telemetry.
 *
 * Use [createTaskTracker] to obtain an instance. If the task-based UX is disabled,
 * a no-op [NullTaskTracker] will be returned.
 */
open class TaskTracker(
  private val profilers: StudioProfilers,
  private val taskMetadata: TaskMetadata
) {

  /**
   * Tracks the event where the user enters a task.
   */
  open fun trackTaskEntered() {
    profilers.ideServices.featureTracker.trackTaskEntered(taskMetadata)
  }

  /**
   * Tracks the event where a task is successfully finished.
   *
   * @param taskFinishedState The state details describing how the task finished (e.g., successful recording).
   */
  open fun trackTaskFinished(taskFinishedState: TaskFinishedState) {
    profilers.ideServices.featureTracker.trackTaskFinished(taskMetadata, taskFinishedState)
  }

  /**
   * Tracks an error that occurred while attempting to start a task.
   *
   * @param metadata Metadata describing the start failure.
   */
  open fun trackStartTaskFailed(metadata: TaskStartFailedMetadata) {
    profilers.ideServices.featureTracker.trackTaskFailed(taskMetadata, metadata)
  }

  /**
   * Tracks an error that occurred while attempting to stop a task.
   *
   * @param metadata Metadata describing the stop failure.
   */
  open fun trackStopTaskFailed(metadata: TaskStopFailedMetadata) {
    profilers.ideServices.featureTracker.trackTaskFailed(taskMetadata, metadata)
  }

  /**
   * Tracks an error that occurred during the processing/parsing phase of a task.
   *
   * @param metadata Metadata describing the processing failure.
   */
  open fun trackProcessingTaskFailed(metadata: TaskProcessingFailedMetadata) {
    profilers.ideServices.featureTracker.trackTaskFailed(taskMetadata, metadata)
  }

  /**
   * A no-op implementation of [TaskTracker] used when task tracking is disabled
   * or as a safe default value.
   *
   * This implementation is used to initialize the `taskTracker` property in
   * [com.android.tools.profilers.Stage] before a concrete instance is explicitly
   * created via [TaskTracker.createTaskTracker]. It overrides all tracking
   * methods with empty bodies, ensuring that tracking calls remain safe
   * (avoiding NullPointerExceptions) even if triggered before full
   * initialization or when the task-based UX is inactive.
   */
  private class NullTaskTracker(profilers: StudioProfilers) : TaskTracker(
    profilers,
    TaskMetadata(
      ProfilerTaskType.UNSPECIFIED,
      0,
      TaskDataOrigin.UNSPECIFIED,
      TaskAttachmentPoint.UNSPECIFIED,
      ExposureLevel.UNKNOWN,
      null
    )
  ) {
    override fun trackTaskEntered() {}
    override fun trackTaskFinished(taskFinishedState: TaskFinishedState) {}
    override fun trackStartTaskFailed(metadata: TaskStartFailedMetadata) {}
    override fun trackStopTaskFailed(metadata: TaskStopFailedMetadata) {}
    override fun trackProcessingTaskFailed(metadata: TaskProcessingFailedMetadata) {}
  }

  companion object {
    /**
     * Creates a [TaskTracker] based on the current profiler state.
     *
     * Warning: Should only be called by `ProfilerTaskHandler.enter()` and `Stage.enter()`
     * in production. This method captures a snapshot of the current session and task state;
     * calling it outside the standard task-entry lifecycle can lead to inaccurate telemetry metadata.
     *
     * @param profilers The [StudioProfilers] instance used to retrieve state and services.
     * @param isNewlyRecordedTask True if the task involves a new recording,
     * false if it is from a past recording/imported file.
     */
    @JvmStatic
    fun createTaskTracker(profilers: StudioProfilers, isNewlyRecordedTask: Boolean): TaskTracker {
      val taskMetadata = buildTaskMetadata(profilers, isNewlyRecordedTask)
      return TaskTracker(profilers, taskMetadata)
    }

    /**
     * Creates a no-op [TaskTracker] that ignores all tracking calls.
     * This avoids NullReferenceException bugs and unnecessary checks at call sites.
     *
     * Warning: Should only be called by the `Stage` constructor (or during
     * [com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler] initialization) in production to provide a safe default.
     * For actual telemetry recording, use [createTaskTracker] during the task's
     * entry phase.
     */
    @JvmStatic
    fun createNullTaskTracker(profilers: StudioProfilers): TaskTracker {
      return NullTaskTracker(profilers)
    }

    private fun buildTaskMetadata(profilers: StudioProfilers, isNewlyRecordedTask: Boolean): TaskMetadata {
      val sessionsManager = profilers.sessionsManager
      return TaskMetadata(
        taskType = sessionsManager.currentTaskType,
        taskId = sessionsManager.selectedSession.sessionId,
        taskDataOrigin = resolveDataOrigin(isNewlyRecordedTask, sessionsManager.selectedSessionMetaData.type),
        taskAttachmentPoint = resolveAttachmentPoint(sessionsManager.isCurrentTaskStartup, isNewlyRecordedTask),
        exposureLevel = sessionsManager.selectedSessionMetaData.exposureLevel,
        taskConfig = if (isNewlyRecordedTask) {
          resolveTaskConfig(profilers, sessionsManager.currentTaskType)
        } else {
          null
        }
      )
    }

    /**
     * Derive data origin based on whether this is a new recording or a previously recorded session.
     * TODO (b/472493920)
     */
    private fun resolveDataOrigin(
      isNewlyRecordedTask: Boolean,
      sessionType: Common.SessionMetaData.SessionType
    ): TaskDataOrigin {
      if (isNewlyRecordedTask) {
        return TaskDataOrigin.NEW
      }

      return when (sessionType) {
        Common.SessionMetaData.SessionType.FULL -> TaskDataOrigin.PAST_RECORDING
        Common.SessionMetaData.SessionType.CPU_CAPTURE,
        Common.SessionMetaData.SessionType.MEMORY_CAPTURE -> TaskDataOrigin.IMPORTED
        else -> TaskDataOrigin.UNSPECIFIED
      }
    }

    private fun resolveAttachmentPoint(
      isStartupTask: Boolean,
      isNewlyRecordedTask: Boolean
    ): TaskAttachmentPoint {
      return when {
        !isNewlyRecordedTask -> TaskAttachmentPoint.UNSPECIFIED
        isStartupTask -> TaskAttachmentPoint.NEW_PROCESS
        else -> TaskAttachmentPoint.EXISTING_PROCESS
      }
    }

    /**
     * Retrieves the specific configuration used for the task.
     */
    private fun resolveTaskConfig(
      profilers: StudioProfilers,
      taskType: ProfilerTaskType
    ): ProfilingConfiguration? {
      val availableConfigs = getCustomTaskConfigs(profilers)

      return when (taskType) {
        ProfilerTaskType.CALLSTACK_SAMPLE -> {
          availableConfigs.filterIsInstance<SimpleperfConfiguration>().firstOrNull()
        }
        ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> {
          val recordingType = profilers.taskHomeTabModel.persistentStateOnTaskEnter.recordingType
          when (recordingType) {
            TaskHomeTabModel.TaskRecordingType.SAMPLED ->
              availableConfigs.filterIsInstance<ArtSampledConfiguration>().firstOrNull()
            TaskHomeTabModel.TaskRecordingType.INSTRUMENTED ->
              availableConfigs.filterIsInstance<ArtInstrumentedConfiguration>().firstOrNull()
            else -> null
          }
        }
        ProfilerTaskType.NATIVE_ALLOCATIONS -> {
          availableConfigs.filterIsInstance<PerfettoNativeAllocationsConfiguration>().firstOrNull()
        }
        else -> null
      }
    }

    private fun getCustomTaskConfigs(profilers: StudioProfilers): List<ProfilingConfiguration> =
      CpuProfilerConfigModel(profilers, CpuProfilerStage(profilers)).apply {
        updateProfilingConfigurations()
      }.taskProfilingConfigurations
  }
}
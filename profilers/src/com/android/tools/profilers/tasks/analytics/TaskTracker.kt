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
import com.android.tools.profilers.ProfilerContext
import com.android.tools.profilers.Stage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.UnifiedTraceOpener
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.cpu.config.LeakCanaryConfiguration
import com.android.tools.profilers.cpu.config.PerfettoNativeAllocationsConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler

/**
 * A class responsible for tracking the lifecycle events of a profiler task.
 *
 * This class serves as a wrapper around [FeatureTracker] for task-specific events, gathering necessary metadata about the task (e.g.,
 * origin, configuration, exposure level) and reporting events such as task entry, completion, or failure.
 *
 * ### Usage Restrictions
 * This class should **only be instantiated by [ProfilerTaskHandler.enter], [Stage.enter], and [UnifiedTraceOpener.openUnifiedTrace]** in
 * production. Because [TaskMetadata] is built at the moment of instantiation, calling this at the wrong time (e.g., before a session is
 * fully initialized) will result in incorrect or stale telemetry.
 *
 * Use [createTaskTracker] to obtain an instance. If the task-based UX is disabled, a no-op [NullTaskTracker] will be returned.
 *
 * This class is strictly for generic lifecycle events. To add task-specific telemetry, define your methods as extension functions in your
 * own feature package.
 */
open class TaskTracker private constructor(private val profilers: StudioProfilers, val taskMetadata: TaskMetadata) {

  /** Tracks the event where the user enters a task. */
  open fun trackTaskEntered(profilerTabsCount: Int = 0) {
    profilers.ideServices.featureTracker.trackTaskEntered(taskMetadata, profilerTabsCount)
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
   * A no-op implementation of [TaskTracker] used when task tracking is disabled or as a safe default value.
   *
   * This implementation is used to initialize the `taskTracker` property in [Stage] before a concrete instance is explicitly created via
   * [TaskTracker.createTaskTracker]. It overrides all tracking methods with empty bodies, ensuring that tracking calls remain safe
   * (avoiding NullPointerExceptions) even if triggered before full initialization or when the task-based UX is inactive.
   */
  private class NullTaskTracker(profilers: StudioProfilers) :
    TaskTracker(
      profilers,
      TaskMetadata(
        ProfilerTaskType.UNSPECIFIED,
        0,
        TaskDataOrigin.UNSPECIFIED,
        TaskAttachmentPoint.UNSPECIFIED,
        ExposureLevel.UNKNOWN,
        null,
      ),
    ) {
    override fun trackTaskEntered(profilerTabsCount: Int) {}

    override fun trackTaskFinished(taskFinishedState: TaskFinishedState) {}

    override fun trackStartTaskFailed(metadata: TaskStartFailedMetadata) {}

    override fun trackStopTaskFailed(metadata: TaskStopFailedMetadata) {}

    override fun trackProcessingTaskFailed(metadata: TaskProcessingFailedMetadata) {}
  }

  companion object {
    /**
     * Creates a [TaskTracker] based on the current profiler state.
     *
     * Warning: Should only be called by `ProfilerTaskHandler.enter()` and `Stage.enter()` in production. This method captures a snapshot of
     * the current session and task state; calling it outside the standard task-entry lifecycle can lead to inaccurate telemetry metadata.
     *
     * @param profilers The [StudioProfilers] instance used to retrieve state and services.
     */
    @JvmStatic
    fun createTaskTracker(profilers: StudioProfilers): TaskTracker {
      val taskMetadata = buildTaskMetadata(profilers)
      return TaskTracker(profilers, taskMetadata)
    }

    /**
     * Creates a [TaskTracker] for a specific session and task type.
     *
     * @param profilers The [StudioProfilers] instance used to retrieve state and services.
     * @param session The [Common.Session] for which the task is being tracked.
     * @param taskType The [ProfilerTaskType] of the task.
     */
    @JvmStatic
    fun createTaskTracker(profilers: StudioProfilers, session: Common.Session, taskType: ProfilerTaskType): TaskTracker {
      val taskMetadata = buildTaskMetadata(profilers, session, taskType)
      return TaskTracker(profilers, taskMetadata)
    }

    /**
     * Creates a [TaskTracker] utilizing deferred offline metadata from the [context] if loaded.
     *
     * @param profilers The [StudioProfilers] instance used to retrieve state and services.
     * @param context The [ProfilerContext] to inspect for offline metadata.
     */
    @JvmStatic
    fun createTaskTracker(profilers: StudioProfilers, context: ProfilerContext): TaskTracker {
      val proto = context.taskMetadata
      if (proto != null) {
        val taskMetadata = TaskMetadataConverter.fromProto(proto)
        return TaskTracker(profilers, taskMetadata)
      }
      return createTaskTracker(profilers)
    }

    /**
     * Creates a no-op [TaskTracker] that ignores all tracking calls. This avoids NullReferenceException bugs and unnecessary checks at call
     * sites.
     *
     * Warning: Should only be called by the `Stage` constructor (or during [ProfilerTaskHandler] initialization) in production to provide a
     * safe default. For actual telemetry recording, use [createTaskTracker] during the task's entry phase.
     */
    @JvmStatic
    fun createNullTaskTracker(profilers: StudioProfilers): TaskTracker {
      return NullTaskTracker(profilers)
    }

    private fun buildTaskMetadata(profilers: StudioProfilers): TaskMetadata {
      val sessionsManager = profilers.sessionsManager
      return buildTaskMetadata(profilers, sessionsManager.selectedSession, sessionsManager.currentTaskType)
    }

    /** Builds [TaskMetadata] derived from the given [session] and [taskType]. */
    private fun buildTaskMetadata(profilers: StudioProfilers, session: Common.Session, taskType: ProfilerTaskType): TaskMetadata {
      val sessionsManager = profilers.sessionsManager
      val metaData = sessionsManager.getSessionMetaData(session.sessionId) ?: Common.SessionMetaData.getDefaultInstance()
      val isAlive = SessionsManager.isSessionAlive(session)
      return TaskMetadata(
        taskType = taskType,
        taskId = session.sessionId,
        taskDataOrigin = resolveDataOrigin(session),
        taskAttachmentPoint = resolveAttachmentPoint(metaData, isAlive),
        exposureLevel = metaData.exposureLevel,
        taskConfig =
          if (isAlive) {
            resolveTaskConfig(profilers, taskType)
          } else {
            null
          },
      )
    }

    /** Derive data origin based on whether this is a new recording or a previously recorded session. */
    private fun resolveDataOrigin(session: Common.Session): TaskDataOrigin {
      return when {
        SessionsManager.isSessionAlive(session) -> TaskDataOrigin.NEW
        SessionsManager.isSessionImported(session) -> TaskDataOrigin.IMPORTED
        session != Common.Session.getDefaultInstance() -> TaskDataOrigin.PAST_RECORDING
        else -> TaskDataOrigin.UNSPECIFIED
      }
    }

    /** Derive attachment point based on process startup state and whether the session is currently alive. */
    private fun resolveAttachmentPoint(metaData: Common.SessionMetaData, isAlive: Boolean): TaskAttachmentPoint {
      return when {
        !isAlive -> TaskAttachmentPoint.UNSPECIFIED
        metaData.isStartupTask -> TaskAttachmentPoint.NEW_PROCESS
        else -> TaskAttachmentPoint.EXISTING_PROCESS
      }
    }

    /** Retrieves the specific configuration used for the task. */
    private fun resolveTaskConfig(profilers: StudioProfilers, taskType: ProfilerTaskType): ProfilingConfiguration? {
      val availableConfigs = getCustomTaskConfigs(profilers)

      return when (taskType) {
        ProfilerTaskType.CALLSTACK_SAMPLE -> {
          availableConfigs.filterIsInstance<SimpleperfConfiguration>().firstOrNull()
        }
        ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> {
          availableConfigs.filterIsInstance<ArtInstrumentedConfiguration>().firstOrNull()
        }
        ProfilerTaskType.NATIVE_ALLOCATIONS -> {
          availableConfigs.filterIsInstance<PerfettoNativeAllocationsConfiguration>().firstOrNull()
        }
        ProfilerTaskType.LEAKCANARY -> {
          availableConfigs.filterIsInstance<LeakCanaryConfiguration>().firstOrNull()
        }
        else -> null
      }
    }

    private fun getCustomTaskConfigs(profilers: StudioProfilers): List<ProfilingConfiguration> =
      CpuProfilerConfigModel(profilers, CpuProfilerStage(profilers)).apply { updateProfilingConfigurations() }.taskProfilingConfigurations
  }
}

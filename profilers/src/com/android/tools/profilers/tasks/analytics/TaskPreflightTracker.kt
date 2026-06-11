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
package com.android.tools.profilers.tasks.analytics

import com.android.tools.profilers.StudioProfilers

/**
 * A telemetry tracker strictly for use before a session is fully initialized. For tracking life cycle events, use [TaskTracker].
 *
 * @param profilers The StudioProfilers instance.
 * @param metadata Metadata describing the task.
 */
class TaskPreflightTracker(private val profilers: StudioProfilers, val metadata: TaskMetadata) {
  /**
   * Tracks an error that occurred while attempting to start a task. Valid for pre-session validation context since it occurs before a task
   * successfully enters its lifecycle.
   *
   * @param metadata Metadata describing the start failure.
   */
  fun trackPreflightCheckFailed(metadata: TaskStartFailedMetadata) {
    profilers.ideServices.featureTracker.trackTaskFailed(this.metadata, metadata)
  }
}

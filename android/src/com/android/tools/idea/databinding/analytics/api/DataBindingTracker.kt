/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.analytics.api

import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.intellij.openapi.project.Project

/**
 * Defines the end point through which components can report data binding related metrics, in order
 * to help us understand how many projects is using data binding.
 */
interface DataBindingTracker {
  companion object {
    @JvmStatic
    /**
     * This will always return data binding module's implementation of [DataBindingTracker] if data
     * binding module is enabled. Otherwise return a fake [DataBindingDefaultTracker] that returns a
     * default (negative) proto.
     */
    fun getInstance(project: Project): DataBindingTracker {
      return project.getService(DataBindingTracker::class.java)
    }
  }

  /**
   * Tracks metrics that we actively poll for. Stats such as # of DB layout xmls, imports,
   * variables, etc. See [DataBindingEvent.DataBindingPollingMetadata] for full list of metrics.
   */
  fun trackPolledMetaData()

  /** Tracks data binding completion events in layout xml. */
  fun trackDataBindingCompletion(
    eventType: DataBindingEvent.EventType,
    context: DataBindingEvent.DataBindingContext,
  )
}

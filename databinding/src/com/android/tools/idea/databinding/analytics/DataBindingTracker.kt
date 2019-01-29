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
package com.android.tools.idea.databinding.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.databinding.analytics.api.DataBindingTracker
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

/**
 * Class for logging data binding related metrics.
 */
class DataBindingTracker private constructor(private val project: Project) : DataBindingTracker {

  override fun trackDataBindingEnabled() {
    // TODO(b/123721754): Track whether data binding is enabled on a per module basis.
    // Currently, one module is data binding enabled = entire project is data binding enabled.
    val isEnabled = ModuleManager.getInstance(project).modules
      .mapNotNull { it.androidFacet }
      .any { DataBindingUtil.isDataBindingEnabled(it) }
    track(DataBindingEvent.EventType.DATA_BINDING_SYNC_EVENT,
          DataBindingEvent.DataBindingPollMetadata.newBuilder().setDataBindingEnabled(isEnabled).build())
  }

  private fun track(eventType: DataBindingEvent.EventType,
                    pollMetaData: DataBindingEvent.DataBindingPollMetadata) {
    val studioEventBuilder = AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.DATA_BINDING).setDataBindingEvent(
      DataBindingEvent.newBuilder().setType(eventType).setPollMetadata(pollMetaData))

    UsageTracker.log(studioEventBuilder.withProjectId(project))
  }
}
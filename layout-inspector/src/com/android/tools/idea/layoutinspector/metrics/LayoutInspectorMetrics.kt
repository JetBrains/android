/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.appinspection.ide.analytics.toDeviceInfo
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.project.Project

class LayoutInspectorMetrics private constructor(
  private val project: Project,
  private val process: ProcessDescriptor?,
  private val stats: SessionStatistics?,
) {

  private var loggedInitialRender = false

  companion object {
    fun create(project: Project) = LayoutInspectorMetrics(project, null, null)
    fun create(project: Project, process: ProcessDescriptor, stats: SessionStatistics) = LayoutInspectorMetrics(project, process, stats)
  }

  fun logEvent(
    eventType: DynamicLayoutInspectorEventType,
  ) {
    when(eventType) {
      DynamicLayoutInspectorEventType.INITIAL_RENDER,
      DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE,
      DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS -> if (loggedInitialRender) return else loggedInitialRender = true
      else -> {} // continue
    }
    val builder = AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
      dynamicLayoutInspectorEventBuilder.apply {
        type = eventType
        if (stats != null && eventType == DynamicLayoutInspectorEventType.SESSION_DATA) {
          stats.save(sessionBuilder)
        }
      }
      if (process != null) {
        deviceInfo = process.device.toDeviceInfo()
      }
    }.withProjectId(project)

    UsageTracker.log(builder)
  }
}

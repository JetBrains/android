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
import com.android.tools.idea.layoutinspector.snapshots.SnapshotMetadata
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.project.Project

class LayoutInspectorMetrics(
  private val project: Project?,
  private var process: ProcessDescriptor? = null,
  private val snapshotMetadata: SnapshotMetadata? = null
) {

  private var loggedInitialRender = false
  private var loggedInitialConnect = false

  fun setProcess(process: ProcessDescriptor) {
    this.process = process
    loggedInitialConnect = false
  }

  companion object {
    fun logEvent(eventType: DynamicLayoutInspectorEventType) {
      val builder = AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
        dynamicLayoutInspectorEventBuilder.apply {
          type = eventType
        }
      }

      UsageTracker.log(builder)
    }
  }

  fun logEvent(
    eventType: DynamicLayoutInspectorEventType,
    stats: SessionStatistics,
    errorState: AttachErrorState? = null,
    errorCode: AttachErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE,
  ) {
    when(eventType) {
      DynamicLayoutInspectorEventType.INITIAL_RENDER,
      DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE,
      DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS -> if (loggedInitialRender) return else loggedInitialRender = true
      DynamicLayoutInspectorEventType.ATTACH_REQUEST,
      DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST -> if (loggedInitialConnect) return else loggedInitialConnect = true
      DynamicLayoutInspectorEventType.ATTACH_ERROR -> stats.attachError(errorState, errorCode)
      DynamicLayoutInspectorEventType.ATTACH_SUCCESS -> stats.attachSuccess()
      DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS -> stats.attachSuccess()
      else -> {} // continue
    }
    val builder = AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
      dynamicLayoutInspectorEventBuilder.apply {
        type = eventType
        if (eventType == DynamicLayoutInspectorEventType.SESSION_DATA) {
          stats.save(sessionBuilder)
        }
        snapshotMetadata?.toSnapshotInfo()?.let { snapshotInfo = it }
        if (errorState != null) {
          errorInfoBuilder.apply {
            attachErrorState = errorState
            attachErrorCode = errorCode
          }
        }
      }
      process?.let { deviceInfo = it.device.toDeviceInfo() }
    }.withProjectId(project)

    UsageTracker.log(builder)
  }
}

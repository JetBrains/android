/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.internal.AppInspectionAnalyticsTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service
class AppInspectionAnalyticsTrackerService(private val project: Project) :
  AppInspectionAnalyticsTracker {
  companion object {
    fun getInstance(project: Project) = project.service<AppInspectionAnalyticsTrackerService>()
  }

  override fun trackErrorOccurred(errorKind: AppInspectionEvent.ErrorKind) {
    track(AppInspectionEvent.Type.ERROR_OCCURRED) { events ->
      events.inspectionEvent.errorKind = errorKind
    }
  }

  override fun trackToolWindowOpened() {
    track(AppInspectionEvent.Type.TOOL_WINDOW_OPENED)
  }

  override fun trackToolWindowHidden() {
    track(AppInspectionEvent.Type.TOOL_WINDOW_HIDDEN)
  }

  override fun trackProcessSelected(device: DeviceDescriptor, numDevices: Int, numProcesses: Int) {
    track(AppInspectionEvent.Type.PROCESS_SELECTED) { events ->
      events.studioEvent.deviceInfo = device.toDeviceInfo()
      events.inspectionEvent.environmentMetadata = toEnvironmentMetadata(numDevices, numProcesses)
    }
  }

  override fun trackInspectionStopped() {
    track(AppInspectionEvent.Type.INSPECTION_STOPPED)
  }

  override fun trackInspectionRestarted() {
    track(AppInspectionEvent.Type.INSPECTION_RESTARTED)
  }

  private fun toEnvironmentMetadata(
    numDevices: Int,
    numProcesses: Int
  ): AppInspectionEvent.EnvironmentMetadata {
    return AppInspectionEvent.EnvironmentMetadata.newBuilder()
      .setNumDevices(numDevices)
      .setNumProcesses(numProcesses)
      .build()
  }

  private class Events(
    val studioEvent: AndroidStudioEvent.Builder,
    val inspectionEvent: AppInspectionEvent.Builder
  )

  private fun track(type: AppInspectionEvent.Type, addMetadataTo: (Events) -> Unit = {}) {
    val appInspectionEvent = AppInspectionEvent.newBuilder().setType(type)
    val studioEvent =
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.APP_INSPECTION)
        .withProjectId(project)

    addMetadataTo(Events(studioEvent, appInspectionEvent))
    studioEvent.setAppInspectionEvent(appInspectionEvent)

    UsageTracker.log(studioEvent)
  }
}

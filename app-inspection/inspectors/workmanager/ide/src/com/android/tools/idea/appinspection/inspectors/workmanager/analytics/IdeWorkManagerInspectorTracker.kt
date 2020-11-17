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
package com.android.tools.idea.appinspection.inspectors.workmanager.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.WorkManagerInspectorEvent
import com.intellij.openapi.project.Project

/**
 * A [Project]-aware [WorkManagerInspectorTracker] that sends tracking events via [UsageTracker].
 */
class IdeWorkManagerInspectorTracker(private val project: Project) : WorkManagerInspectorTracker {
  private var activeMode = WorkManagerInspectorEvent.Mode.TABLE_MODE

  override fun trackTableModeSelected() {
    track(WorkManagerInspectorEvent.Context.TOOL_BUTTON_CONTEXT, WorkManagerInspectorEvent.Type.TABLE_MODE_SELECTED.toEvent())
    activeMode = WorkManagerInspectorEvent.Mode.TABLE_MODE
  }

  override fun trackGraphModeSelected(context: WorkManagerInspectorEvent.Context, chainInfo: WorkManagerInspectorEvent.ChainInfo) {
    val event = WorkManagerInspectorEvent.Type.GRAPH_MODE_SELECTED.toEvent().setChainInfo(chainInfo)
    track(context, event)
    activeMode = WorkManagerInspectorEvent.Mode.GRAPH_MODE
  }

  override fun trackWorkSelected(context: WorkManagerInspectorEvent.Context) {
    track(context, WorkManagerInspectorEvent.Type.WORK_SELECTED.toEvent())
  }

  override fun trackJumpedToSource() {
    track(WorkManagerInspectorEvent.Context.DETAILS_CONTEXT, WorkManagerInspectorEvent.Type.JUMPED_TO_SOURCE.toEvent())
  }

  override fun trackWorkCancelled() {
    track(WorkManagerInspectorEvent.Context.TOOL_BUTTON_CONTEXT, WorkManagerInspectorEvent.Type.JUMPED_TO_SOURCE.toEvent())
  }

  private fun WorkManagerInspectorEvent.Type.toEvent(): WorkManagerInspectorEvent.Builder {
    return WorkManagerInspectorEvent.newBuilder().setType(this)
  }

  // Note: We could have just set |context| directly before calling track, but making it a
  // parameter ensures we never forget to do so.
  private fun track(context: WorkManagerInspectorEvent.Context, inspectorEvent: WorkManagerInspectorEvent.Builder) {
    inspectorEvent.context = context
    inspectorEvent.mode = activeMode

    val inspectionEvent = AppInspectionEvent.newBuilder()
      .setType(AppInspectionEvent.Type.INSPECTOR_EVENT)
      .setWorkManagerInspectorEvent(inspectorEvent)

    val studioEvent: AndroidStudioEvent.Builder = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.APP_INSPECTION)
      .setAppInspectionEvent(inspectionEvent)

    // TODO(b/153270761): Use studioEvent.withProjectId instead, after code is moved out of
    //  monolithic core module
    studioEvent.projectId = AnonymizerUtil.anonymizeUtf8(project.basePath!!)
    UsageTracker.log(studioEvent)
  }
}

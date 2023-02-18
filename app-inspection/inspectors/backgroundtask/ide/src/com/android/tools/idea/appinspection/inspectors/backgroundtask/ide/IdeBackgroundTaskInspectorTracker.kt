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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.ide

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTracker
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.BackgroundTaskInspectorEvent
import com.intellij.openapi.project.Project

class IdeBackgroundTaskInspectorTracker(private val project: Project) :
  BackgroundTaskInspectorTracker {
  private var activeMode = BackgroundTaskInspectorEvent.Mode.TABLE_MODE

  override fun trackTableModeSelected() {
    track(
      BackgroundTaskInspectorEvent.Context.TOOL_BUTTON_CONTEXT,
      BackgroundTaskInspectorEvent.Type.TABLE_MODE_SELECTED.toEvent()
    )
    activeMode = BackgroundTaskInspectorEvent.Mode.TABLE_MODE
  }

  override fun trackGraphModeSelected(
    context: BackgroundTaskInspectorEvent.Context,
    chainInfo: BackgroundTaskInspectorEvent.ChainInfo
  ) {
    val event =
      BackgroundTaskInspectorEvent.Type.GRAPH_MODE_SELECTED.toEvent().setChainInfo(chainInfo)
    track(context, event)
    activeMode = BackgroundTaskInspectorEvent.Mode.GRAPH_MODE
  }

  override fun trackJumpedToSource() {
    track(
      BackgroundTaskInspectorEvent.Context.DETAILS_CONTEXT,
      BackgroundTaskInspectorEvent.Type.JUMPED_TO_SOURCE.toEvent()
    )
  }

  override fun trackWorkCancelled() {
    track(
      BackgroundTaskInspectorEvent.Context.TOOL_BUTTON_CONTEXT,
      BackgroundTaskInspectorEvent.Type.WORK_CANCELED.toEvent()
    )
  }

  private fun BackgroundTaskInspectorEvent.Type.toEvent(): BackgroundTaskInspectorEvent.Builder {
    return BackgroundTaskInspectorEvent.newBuilder().setType(this)
  }

  override fun trackWorkSelected(context: BackgroundTaskInspectorEvent.Context) {
    track(context, BackgroundTaskInspectorEvent.Type.WORK_SELECTED.toEvent())
  }

  override fun trackJobSelected() {
    track(
      BackgroundTaskInspectorEvent.Context.TABLE_CONTEXT,
      BackgroundTaskInspectorEvent.Type.JOB_SELECTED.toEvent()
    )
  }

  override fun trackJobUnderWorkSelected() {
    track(
      BackgroundTaskInspectorEvent.Context.TABLE_CONTEXT,
      BackgroundTaskInspectorEvent.Type.JOB_UNDER_WORK_SELECTED.toEvent()
    )
  }

  override fun trackAlarmSelected() {
    track(
      BackgroundTaskInspectorEvent.Context.TABLE_CONTEXT,
      BackgroundTaskInspectorEvent.Type.ALARM_SELECTED.toEvent()
    )
  }

  override fun trackWakeLockSelected() {
    track(
      BackgroundTaskInspectorEvent.Context.TABLE_CONTEXT,
      BackgroundTaskInspectorEvent.Type.WAKE_LOCK_SELECTED.toEvent()
    )
  }

  override fun trackWakeLockUnderJobSelected() {
    track(
      BackgroundTaskInspectorEvent.Context.TABLE_CONTEXT,
      BackgroundTaskInspectorEvent.Type.WAKE_LOCK_UNDER_JOB_SELECTED.toEvent()
    )
  }

  // Note: We could have just set |context| directly before calling track, but making it a
  // parameter ensures we never forget to do so.
  private fun track(
    context: BackgroundTaskInspectorEvent.Context,
    inspectorEvent: BackgroundTaskInspectorEvent.Builder
  ) {
    inspectorEvent.context = context
    inspectorEvent.mode = activeMode

    val inspectionEvent =
      AppInspectionEvent.newBuilder()
        .setType(AppInspectionEvent.Type.INSPECTOR_EVENT)
        .setBackgroundTaskInspectorEvent(inspectorEvent)

    val studioEvent: AndroidStudioEvent.Builder =
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.APP_INSPECTION)
        .setAppInspectionEvent(inspectionEvent)

    // TODO(b/153270761): Use studioEvent.withProjectId instead, after code is moved out of
    //  monolithic core module
    studioEvent.projectId = AnonymizerUtil.anonymizeUtf8(project.basePath!!)
    UsageTracker.log(studioEvent)
  }
}

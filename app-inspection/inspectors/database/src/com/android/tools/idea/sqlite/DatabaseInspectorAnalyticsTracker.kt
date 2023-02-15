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
package com.android.tools.idea.sqlite

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.project.Project

interface DatabaseInspectorAnalyticsTracker {
  companion object {
    fun getInstance(project: Project): DatabaseInspectorAnalyticsTracker {
      return project.getService(DatabaseInspectorAnalyticsTracker::class.java)
    }
  }

  fun trackErrorOccurred(errorKind: AppInspectionEvent.DatabaseInspectorEvent.ErrorKind)
  fun trackTableCellEdited()
  fun trackTargetRefreshed(targetType: AppInspectionEvent.DatabaseInspectorEvent.TargetType)

  fun trackStatementExecuted(
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState,
    statementContext: AppInspectionEvent.DatabaseInspectorEvent.StatementContext
  )

  fun trackStatementExecutionCanceled(
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState,
    statementContext: AppInspectionEvent.DatabaseInspectorEvent.StatementContext
  )

  fun trackLiveUpdatedToggled(enabled: Boolean)
  fun trackEnterOfflineModeUserCanceled()
  fun trackOfflineDatabaseDownloadFailed()
  fun trackOfflineModeEntered(
    metadata: AppInspectionEvent.DatabaseInspectorEvent.OfflineModeMetadata
  )
  fun trackExportDialogOpened(
    actionOrigin: AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
  )
  fun trackExportCompleted(
    source: AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Source,
    sourceFormat:
      AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.SourceFormat,
    destination:
      AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Destination,
    durationMs: Int,
    outcome: AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Outcome,
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState
  )
}

class DatabaseInspectorAnalyticsTrackerImpl(val project: Project) :
  DatabaseInspectorAnalyticsTracker {
  override fun trackErrorOccurred(errorKind: AppInspectionEvent.DatabaseInspectorEvent.ErrorKind) {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.ERROR_OCCURRED)
        .setErrorKind(errorKind)
    )
  }

  override fun trackTableCellEdited() {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.TABLE_CELL_EDITED)
    )
  }

  override fun trackTargetRefreshed(
    targetType: AppInspectionEvent.DatabaseInspectorEvent.TargetType
  ) {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.TARGET_REFRESHED)
        .setTargetType(targetType)
    )
  }

  override fun trackStatementExecuted(
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState,
    statementContext: AppInspectionEvent.DatabaseInspectorEvent.StatementContext
  ) {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.STATEMENT_EXECUTED)
        .setConnectivityState(connectivityState)
        .setStatementContext(statementContext)
    )
  }

  override fun trackStatementExecutionCanceled(
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState,
    statementContext: AppInspectionEvent.DatabaseInspectorEvent.StatementContext
  ) {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.STATEMENT_EXECUTION_CANCELED)
        .setConnectivityState(connectivityState)
        .setStatementContext(statementContext)
    )
  }

  override fun trackLiveUpdatedToggled(enabled: Boolean) {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.LIVE_UPDATES_TOGGLED)
        .setLiveUpdatingEnabled(enabled)
    )
  }

  override fun trackEnterOfflineModeUserCanceled() {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.ENTER_OFFLINE_MODE_USER_CANCELED)
    )
  }

  override fun trackOfflineDatabaseDownloadFailed() {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.OFFLINE_DATABASE_DOWNLOAD_FAILED)
    )
  }

  override fun trackOfflineModeEntered(
    metadata: AppInspectionEvent.DatabaseInspectorEvent.OfflineModeMetadata
  ) {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.OFFLINE_MODE_ENTERED)
        .setOfflineModeMetadata(metadata)
    )
  }

  override fun trackExportDialogOpened(
    actionOrigin: AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
  ) {
    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.EXPORT_DIALOG_OPENED)
        .setExportDialogOpenedEvent(
          AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.newBuilder()
            .setOrigin(actionOrigin)
        )
    )
  }

  override fun trackExportCompleted(
    source: AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Source,
    sourceFormat:
      AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.SourceFormat,
    destination:
      AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Destination,
    durationMs: Int,
    outcome: AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Outcome,
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState
  ) {
    val event =
      AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.newBuilder()
        .setSource(source)
        .setSourceFormat(sourceFormat)
        .setDestination(destination)
        .setExportDurationMs(durationMs)
        .setOutcome(outcome)
        .build()

    track(
      AppInspectionEvent.DatabaseInspectorEvent.newBuilder()
        .setType(AppInspectionEvent.DatabaseInspectorEvent.Type.EXPORT_OPERATION_COMPLETED)
        .setConnectivityState(connectivityState)
        .setExportCompletedEvent(event)
    )
  }

  private fun track(inspectorEvent: AppInspectionEvent.DatabaseInspectorEvent.Builder) {
    val inspectionEvent =
      AppInspectionEvent.newBuilder()
        .setType(AppInspectionEvent.Type.INSPECTOR_EVENT)
        .setDatabaseInspectorEvent(inspectorEvent)

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

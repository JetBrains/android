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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.google.wireless.android.sdk.stats.AppInspectionEvent

open class FakeDatabaseInspectorAnalyticsTracker : DatabaseInspectorAnalyticsTracker {
  override fun trackErrorOccurred(errorKind: AppInspectionEvent.DatabaseInspectorEvent.ErrorKind) {}

  override fun trackTableCellEdited() {}

  override fun trackTargetRefreshed(
    targetType: AppInspectionEvent.DatabaseInspectorEvent.TargetType
  ) {}

  override fun trackStatementExecuted(
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState,
    statementContext: AppInspectionEvent.DatabaseInspectorEvent.StatementContext
  ) {}

  override fun trackStatementExecutionCanceled(
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState,
    statementContext: AppInspectionEvent.DatabaseInspectorEvent.StatementContext
  ) {}

  override fun trackLiveUpdatedToggled(enabled: Boolean) {}

  override fun trackEnterOfflineModeUserCanceled() {}

  var offlineDownloadFailed: Boolean? = null
  var offlineDownloadFailedCount = 0
  override fun trackOfflineDatabaseDownloadFailed() {
    offlineDownloadFailed = true
    offlineDownloadFailedCount += 1
  }

  var metadata: AppInspectionEvent.DatabaseInspectorEvent.OfflineModeMetadata? = null
  override fun trackOfflineModeEntered(
    metadata: AppInspectionEvent.DatabaseInspectorEvent.OfflineModeMetadata
  ) {
    this.metadata = metadata
  }

  override fun trackExportDialogOpened(
    actionOrigin: AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
  ) {}

  override fun trackExportCompleted(
    source: AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Source,
    sourceFormat:
      AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.SourceFormat,
    destination:
      AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Destination,
    durationMs: Int,
    outcome: AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Outcome,
    connectivityState: AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState
  ) {}
}

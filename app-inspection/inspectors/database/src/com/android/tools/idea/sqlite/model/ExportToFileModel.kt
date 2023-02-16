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
@file:Suppress("unused") // TODO(161081452): remove once all features are implemented

package com.android.tools.idea.sqlite.model

import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
import java.nio.file.Path

sealed class ExportFormat {
  object DB : ExportFormat()
  object SQL : ExportFormat()
  data class CSV(val delimiter: Delimiter) : ExportFormat()
}

enum class Delimiter(val delimiter: Char) {
  SEMICOLON(';'),
  TAB('	'),
  COMMA(','),
  VERTICAL_BAR('|'),
  SPACE(' ')
}

/** All the information needed to perform an export operation */
sealed class ExportRequest(
  open val srcDatabase: SqliteDatabaseId,
  open val format: ExportFormat,
  open val dstPath: Path
) {
  data class ExportDatabaseRequest(
    override val srcDatabase: SqliteDatabaseId,
    override val format: ExportFormat,
    override val dstPath: Path
  ) : ExportRequest(srcDatabase, format, dstPath)

  data class ExportTableRequest(
    override val srcDatabase: SqliteDatabaseId,
    val srcTable: String,
    override val format: ExportFormat,
    override val dstPath: Path
  ) : ExportRequest(srcDatabase, format, dstPath)

  data class ExportQueryResultsRequest(
    override val srcDatabase: SqliteDatabaseId,
    val srcQuery: SqliteStatement,
    override val format: ExportFormat,
    override val dstPath: Path
  ) : ExportRequest(srcDatabase, format, dstPath)
}

/**
 * All the information needed to show a dialog asking a user to specify an [ExportRequest].
 *
 * @param actionOrigin Represents the UI area where the export dialog was launched from. Captured
 *   for analytics purposes.
 */
sealed class ExportDialogParams(
  open val srcDatabase: SqliteDatabaseId,
  open val actionOrigin: Origin
) {
  /** @param actionOrigin see [ExportDialogParams.actionOrigin] */
  data class ExportDatabaseDialogParams(
    override val srcDatabase: SqliteDatabaseId,
    override val actionOrigin: Origin
  ) : ExportDialogParams(srcDatabase, actionOrigin)

  /** @param actionOrigin see [ExportDialogParams.actionOrigin] */
  data class ExportTableDialogParams(
    override val srcDatabase: SqliteDatabaseId,
    val srcTable: String,
    override val actionOrigin: Origin
  ) : ExportDialogParams(srcDatabase, actionOrigin)

  /** @param actionOrigin see [ExportDialogParams.actionOrigin] */
  data class ExportQueryResultsDialogParams(
    override val srcDatabase: SqliteDatabaseId,
    val query: SqliteStatement,
    override val actionOrigin: Origin
  ) : ExportDialogParams(srcDatabase, actionOrigin)
}

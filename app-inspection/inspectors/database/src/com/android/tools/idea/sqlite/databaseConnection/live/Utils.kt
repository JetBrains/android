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
package com.android.tools.idea.sqlite.databaseConnection.live

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.getRowIdName
import com.google.common.io.BaseEncoding
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.Executor

/**
 * Builds a [SqliteInspectorProtocol.Command] from a [SqliteStatement] and a database connection id.
 */
internal fun buildQueryCommand(
  sqliteStatement: SqliteStatement,
  databaseConnectionId: Int,
  responseSizeByteLimitHint: Long? = null
): SqliteInspectorProtocol.Command {
  val parameterValues =
    sqliteStatement.parametersValues.map { param ->
      SqliteInspectorProtocol.QueryParameterValue.newBuilder()
        .also { builder ->
          if (param is SqliteValue.StringValue) {
            builder.stringValue = param.value
          }
        }
        .build()
    }

  val queryBuilder =
    SqliteInspectorProtocol.QueryCommand.newBuilder()
      .setQuery(sqliteStatement.sqliteStatementText)
      .addAllQueryParameterValues(parameterValues)
      .setDatabaseId(databaseConnectionId)

  if (responseSizeByteLimitHint != null) {
    queryBuilder.responseSizeLimitHint = responseSizeByteLimitHint
  }

  return SqliteInspectorProtocol.Command.newBuilder().setQuery(queryBuilder).build()
}

internal fun SqliteInspectorProtocol.CellValue.toSqliteColumnValue(
  colName: String
): SqliteColumnValue {
  return when (oneOfCase) {
    // TODO(b/150761542) Handle all types in SqliteValue.
    SqliteInspectorProtocol.CellValue.OneOfCase.STRING_VALUE ->
      SqliteColumnValue(colName, SqliteValue.StringValue(stringValue))
    SqliteInspectorProtocol.CellValue.OneOfCase.DOUBLE_VALUE ->
      SqliteColumnValue(colName, SqliteValue.StringValue(doubleValue.toString()))
    SqliteInspectorProtocol.CellValue.OneOfCase.BLOB_VALUE ->
      // TODO(b/151757927): this approach is inefficient, as we are using 4x the amount of memory.
      // Create SqliteValue.BlobValue instead.
      SqliteColumnValue(
        colName,
        SqliteValue.StringValue(BaseEncoding.base16().encode(blobValue.toByteArray()))
      )
    SqliteInspectorProtocol.CellValue.OneOfCase.LONG_VALUE ->
      SqliteColumnValue(colName, SqliteValue.StringValue(longValue.toString()))
    SqliteInspectorProtocol.CellValue.OneOfCase.ONEOF_NOT_SET ->
      SqliteColumnValue(colName, SqliteValue.NullValue)
    null -> error("value is null")
  }
}

internal fun List<SqliteInspectorProtocol.Table>.toSqliteSchema(): SqliteSchema {
  val tables = map { table ->
    val columns = table.columnsList.map { it.toSqliteColumn() }
    val rowIdName = getRowIdName(columns)
    SqliteTable(table.name, columns, rowIdName, table.isView)
  }
  return SqliteSchema(tables)
}

fun ListenableFuture<SqliteInspectorProtocol.Response>.mapToColumns(executor: Executor) =
  transform(executor) { response ->
    response.query.columnNamesList.map { columnName ->
      ResultSetSqliteColumn(columnName, null, null, null)
    }
  }

/**
 * This method is used to handle synchronous errors from the on-device inspector. All errors are
 * logged, except for recoverable errors.
 *
 * An on-device inspector can send an error as a response to a command (synchronous) or as an event
 * (asynchronous). When detected, synchronous errors are thrown as exceptions so that they become
 * part of the usual flow for errors: they cause the futures to fail and are shown in the views.
 *
 * Asynchronous errors are delivered to DatabaseInspectorProjectService that takes care of showing
 * them.
 */
internal fun handleError(
  project: Project,
  command: SqliteInspectorProtocol.Command,
  errorContent: SqliteInspectorProtocol.ErrorContent,
  logger: Logger
) {
  // Ignore race conditions for short-lived dbs.
  // Short lived dbs can be closed after the "db open" event is received and before the next command
  // is executed.
  if (
    errorContent.errorCode ==
      SqliteInspectorProtocol.ErrorContent.ErrorCode.ERROR_NO_OPEN_DATABASE_WITH_REQUESTED_ID ||
      errorContent.errorCode ==
        SqliteInspectorProtocol.ErrorContent.ErrorCode.ERROR_DB_CLOSED_DURING_OPERATION
  ) {
    return
  }

  when (command.oneOfCase) {
    SqliteInspectorProtocol.Command.OneOfCase.GET_SCHEMA,
    SqliteInspectorProtocol.Command.OneOfCase.KEEP_DATABASES_OPEN,
    SqliteInspectorProtocol.Command.OneOfCase.QUERY,
    SqliteInspectorProtocol.Command.OneOfCase.TRACK_DATABASES,
    SqliteInspectorProtocol.Command.OneOfCase.ONEOF_NOT_SET ->
      handleErrorContent(project, errorContent, logger)
    null -> {}
  }
}

private fun handleErrorContent(
  project: Project,
  errorContent: SqliteInspectorProtocol.ErrorContent,
  logger: Logger
) {
  val analyticsTracker = DatabaseInspectorAnalyticsTracker.getInstance(project)

  when (errorContent.recoverability.oneOfCase) {
    SqliteInspectorProtocol.ErrorRecoverability.OneOfCase.IS_RECOVERABLE -> {
      // log when isRecoverable is set and is false.
      if (!errorContent.recoverability.isRecoverable) {
        logger.warn(
          "Unrecoverable error from on-device inspector: ${errorContent.message}\n${errorContent.stackTrace}"
        )
        analyticsTracker.trackErrorOccurred(
          AppInspectionEvent.DatabaseInspectorEvent.ErrorKind.IS_RECOVERABLE_FALSE
        )
      } else {
        analyticsTracker.trackErrorOccurred(
          AppInspectionEvent.DatabaseInspectorEvent.ErrorKind.IS_RECOVERABLE_TRUE
        )
      }
    }
    SqliteInspectorProtocol.ErrorRecoverability.OneOfCase.ONEOF_NOT_SET -> {
      // log when isRecoverable is not set ("unknown if recoverable error").
      logger.warn(
        "Unknown if recoverable error from on-device inspector: ${errorContent.message}\n${errorContent.stackTrace}"
      )
      analyticsTracker.trackErrorOccurred(
        AppInspectionEvent.DatabaseInspectorEvent.ErrorKind.IS_RECOVERABLE_UNKNOWN
      )
    }
    null -> {}
  }
}

internal fun getErrorMessage(errorContent: SqliteInspectorProtocol.ErrorContent): String {
  /**
   * Errors can be "recoverable", "unrecoverable" or "unknown if recoverable".
   * 1. "Recoverable" errors are errors after which execution can continue as normal (eg. typo in
   *    query).
   * 2. "Unrecoverable" errors are errors after which the state of on-device inspector is corrupted
   *    and app needs restart.
   * 3. "Unknown if recoverable" means the on-device inspector doesn't have enough information to
   *    decide if the error is recoverable or not.
   *
   * `errorContent.recoverability.isRecoverable` is:
   * 1. true for "recoverable" errors
   * 2. false for "unrecoverable" errors
   * 3. not set for "unknown if recoverable" errors
   */
  return when (errorContent.recoverability.oneOfCase) {
    SqliteInspectorProtocol.ErrorRecoverability.OneOfCase.IS_RECOVERABLE -> {
      if (errorContent.recoverability.isRecoverable) {
        // recoverable
        errorContent.message
      } else {
        // unrecoverable
        "An error has occurred which requires you to restart your app: ${errorContent.message}"
      }
    }
    SqliteInspectorProtocol.ErrorRecoverability.OneOfCase.ONEOF_NOT_SET -> {
      // unknown if recoverable
      "An error has occurred which might require you to restart your app: ${errorContent.message}"
    }
    null -> error("value is null")
  }
}

private fun SqliteInspectorProtocol.Column.toSqliteColumn(): SqliteColumn {
  return SqliteColumn(name, SqliteAffinity.fromTypename(type), !isNotNull, primaryKey > 0)
}

/**
 * An exception from the on-device inspector.
 *
 * @param message The error message of the exception.
 * @param onDeviceStackTrace The stack trace of the exception, captured on the device.
 */
data class LiveInspectorException(override val message: String, val onDeviceStackTrace: String) :
  RuntimeException()

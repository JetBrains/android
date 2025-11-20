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
package com.android.tools.idea.sqlite

import androidx.sqlite.inspection.SqliteInspectorProtocol.AcquireDatabaseLockCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.AdditionalDriver
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.Event
import androidx.sqlite.inspection.SqliteInspectorProtocol.KeepDatabasesOpenCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.ReleaseDatabaseLockCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import androidx.sqlite.inspection.SqliteInspectorProtocol.TrackDatabasesCommand
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices.Severity.ERROR
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.getErrorMessage
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle.message
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.settings.DatabaseInspectorProjectSettings
import com.android.tools.idea.sqlite.settings.DatabaseInspectorSettings
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Class used to receive asynchronous events from the on-device inspector.
 *
 * @param messenger Communication channel with the on-device inspector.
 * @param onErrorEventListener Function called when a ErrorOccurred event is received.
 * @param onDatabaseAddedListener Function called when a DatabaseOpened event is received.
 * @param taskExecutor to parse responses from on-device inspector
 * @param errorsSideChannel side channel to error logging
 * @param scope the coroutine scoped used to send messages to inspector. The job in this scope must
 *   be created using SupervisorJob, to avoid the parent Job from failing when child Jobs fail.
 */
class DatabaseInspectorClient(
  private val project: Project,
  private val messenger: AppInspectorMessenger,
  private val parentDisposable: Disposable,
  private val onErrorEventListener: (errorMessage: String) -> Unit,
  private val onDatabaseAddedListener: (SqliteDatabaseId, LiveDatabaseConnection) -> Unit,
  private val onDatabasePossiblyChanged: () -> Unit,
  private val onDatabaseClosed: (databaseId: SqliteDatabaseId) -> Unit,
  private val taskExecutor: Executor,
  private val ideServices: AppInspectionIdeServices,
  scope: CoroutineScope,
  errorsSideChannel: ErrorsSideChannel = { _, _ -> },
) : DatabaseInspectorClientCommandsChannel {
  private val dbMessenger =
    DatabaseInspectorMessenger(messenger, scope, taskExecutor, errorsSideChannel)

  init {
    scope.launch { messenger.eventFlow.collect { eventData -> onRawEvent(eventData) } }
  }

  @VisibleForTesting
  fun onRawEvent(eventData: ByteArray) {
    val event = Event.parseFrom(eventData)
    when {
      event.hasDatabaseOpened() -> {
        val openedDatabase = event.databaseOpened
        invokeLater {
          val databaseId =
            SqliteDatabaseId.fromLiveDatabase(
              openedDatabase.path,
              openedDatabase.databaseId,
              openedDatabase.isForcedConnection,
              openedDatabase.isReadOnly,
              openedDatabase.apiClassName,
            )
          val databaseConnection =
            LiveDatabaseConnection(
              parentDisposable,
              dbMessenger,
              openedDatabase.databaseId,
              taskExecutor,
            )
          onDatabaseAddedListener(databaseId, databaseConnection)
        }
      }
      event.hasDatabasePossiblyChanged() -> {
        onDatabasePossiblyChanged()
      }
      event.hasDatabaseClosed() -> {
        invokeLater {
          onDatabaseClosed(
            SqliteDatabaseId.fromLiveDatabase(
              event.databaseClosed.path,
              event.databaseClosed.databaseId,
            )
          )
        }
      }
      event.hasErrorOccurred() -> {
        val errorContent = event.errorOccurred.content
        val errorMessage = getErrorMessage(errorContent)
        onErrorEventListener(errorMessage)
        val log = buildString {
          append(errorMessage)
          if (errorContent.stackTrace.isNotEmpty()) {
            append('\n')
            append(errorContent.stackTrace)
          }
        }
        thisLogger().warn(log)
      }
    }
  }

  /**
   * Sends a command to the on-device inspector to start looking for database connections. When the
   * on-device inspector discovers a connection, it sends back an asynchronous databaseOpen event.
   */
  suspend fun startTrackingDatabaseConnections() {
    val command = TrackDatabasesCommand.newBuilder()
    if (StudioFlags.APP_INSPECTION_USE_EXPERIMENTAL_DATABASE_INSPECTOR.get()) {
      command.forceOpen = DatabaseInspectorSettings.getInstance().isForceOpen
    }
    val settings = DatabaseInspectorProjectSettings.getInstance(project)
    if (StudioFlags.APP_INSPECTION_ENABLE_ADDITIONAL_SQL_DRIVER.get()) {
      if (settings.additionalDriverClass.isNotEmpty()) {
        command.addAdditionalDrivers(
          AdditionalDriver.newBuilder()
            .setDriverClass(settings.additionalDriverClass)
            .setConnectionClass(settings.additionalConnectionClass)
        )
      }
      command.ignoreFrameworkApi = settings.isIgnoreFrameworkApi
    }
    val response = dbMessenger.sendCommand(Command.newBuilder().setTrackDatabases(command.build()))

    if (response.trackDatabases.trackedAdditionalDriversCount != command.additionalDriversCount) {
      ideServices.showNotification(
        message("notification.additional.driver.error", settings.additionalDriverClass),
        message("database.inspector"),
        ERROR,
      )
    }
  }

  /**
   * If [keepOpen] is true, sends a command to the on-device inspector to force connections to
   * databases to stay open, even after the app closes them. Return a future boolean that is true if
   * `KeepDatabasesOpen` is enabled, false otherwise and null if the command failed.
   */
  override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> {
    val response =
      dbMessenger.sendCommandAsync(
        Command.newBuilder()
          .setKeepDatabasesOpen(KeepDatabasesOpenCommand.newBuilder().setSetEnabled(keepOpen))
      )

    return response.transform(taskExecutor) {
      return@transform when (it.oneOfCase) {
        Response.OneOfCase.KEEP_DATABASES_OPEN -> keepOpen
        else -> null
      }
    }
  }

  override fun acquireDatabaseLock(databaseId: Int): ListenableFuture<Int?> {
    return dbMessenger
      .sendCommandAsync(
        Command.newBuilder()
          .setAcquireDatabaseLock(AcquireDatabaseLockCommand.newBuilder().setDatabaseId(databaseId))
      )
      .transform(taskExecutor) { response ->
        when (response.oneOfCase) {
          Response.OneOfCase.ACQUIRE_DATABASE_LOCK -> response.acquireDatabaseLock.lockId
          Response.OneOfCase.ERROR_OCCURRED -> null // TODO(161081452): log the error
          else -> null // TODO(161081452): log an error
        }
      }
  }

  override fun releaseDatabaseLock(lockId: Int): ListenableFuture<Unit> {
    return dbMessenger
      .sendCommandAsync(
        Command.newBuilder()
          .setReleaseDatabaseLock(ReleaseDatabaseLockCommand.newBuilder().setLockId(lockId))
      )
      .transform(taskExecutor) { response ->
        when (response.oneOfCase) {
          Response.OneOfCase.ACQUIRE_DATABASE_LOCK -> Unit
          Response.OneOfCase.ERROR_OCCURRED -> Unit // TODO(161081452): log the error
          else -> Unit // TODO(161081452): log an error
        }
      }
  }
}

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

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.sqlite.databaseConnection.live.getErrorMessage
import com.intellij.openapi.application.ApplicationManager

/**
 * Class used to receive asynchronous events from the on-device inspector.
 * @param messenger Communication channel with the on-device inspector.
 * @param handleError Function called when a ErrorOccurred event is received.
 * @param openDatabase Function called when a DatabaseOpened event is received.
 * @param onDisposeListener Function called when an onDispose event is received.
 */
class DatabaseInspectorClient constructor(
  messenger: CommandMessenger,
  private val handleError: (errorMessage: String) -> Unit,
  private val openDatabase: (messenger: CommandMessenger, databaseConnectionId: Int, databaseName: String) -> Unit,
  private val onDisposeListener: () -> Unit
) : AppInspectorClient(messenger) {
  override val eventListener: EventListener = object : EventListener {
    override fun onRawEvent(eventData: ByteArray) {
      val event = SqliteInspectorProtocol.Event.parseFrom(eventData)
      when {
        event.hasDatabaseOpened() -> {
          val openedDatabase = event.databaseOpened
          ApplicationManager.getApplication().invokeLater {
            openDatabase(messenger, openedDatabase.databaseId, openedDatabase.name)
          }
        }
        event.hasErrorOccurred() -> {
          val errorContent = event.errorOccurred.content
          val errorMessage = getErrorMessage((errorContent))
          handleError(errorMessage)
        }
      }
    }

    override fun onCrashEvent(message: String) {
      // TODO databaseInspectorProjectService.showError(message)
    }

    override fun onDispose() {
      onDisposeListener()
    }
  }

  /**
   * Sends a command to the on-device inspector to start looking for database connections.
   * When the on-device inspector discovers a connection, it sends back an asynchronous databaseOpen event.
   */
  fun startTrackingDatabaseConnections() {
    messenger.sendRawCommand(
      SqliteInspectorProtocol.Command.newBuilder()
        .setTrackDatabases(SqliteInspectorProtocol.TrackDatabasesCommand.getDefaultInstance())
        .build()
        .toByteArray()
    )
  }
}
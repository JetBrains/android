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

import androidx.sqlite.inspection.SqliteInspectorProtocol
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.sqlite.databaseConnection.live.LiveInspectorException
import com.android.tools.idea.sqlite.databaseConnection.live.getErrorMessage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext

class DatabaseInspectorMessenger(
  private val messenger: AppInspectorClient.CommandMessenger,
  private val scope: CoroutineScope,
  private val errorsSideChannel: ErrorsSideChannel = { _, _ -> }
) {
  suspend fun sendCommand(command: Command): SqliteInspectorProtocol.Response {
    val rawResponse = messenger.sendRawCommand(command.toByteArray())
    val parsedResponse = SqliteInspectorProtocol.Response.parseFrom(rawResponse)
    if (parsedResponse.hasErrorOccurred()) {
      val errorResponse = parsedResponse.errorOccurred
      errorsSideChannel(command, errorResponse)
      val message = getErrorMessage(errorResponse.content)
      throw LiveInspectorException(message, errorResponse.content.stackTrace)
    }
    return parsedResponse
  }

  /**
   * The alternate version of sendCommand using futures. It delegates work to [sendCommand].
   *
   * This is intended to be used in the interim while database inspector is being migrated to kotlin coroutines, or for Java clients that
   * cannot be easily ported to Kotlin.
   */
  fun sendCommandAsync(command: Command): ListenableFuture<SqliteInspectorProtocol.Response> = scope.future { sendCommand(command) }
}

typealias ErrorsSideChannel = (Command, SqliteInspectorProtocol.ErrorOccurredResponse) -> Unit

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
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.databaseConnection.live.LiveInspectorException
import com.android.tools.idea.sqlite.databaseConnection.live.getErrorMessage
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

class DatabaseInspectorMessenger(
  private val messenger: AppInspectorClient.CommandMessenger,
  private val taskExecutor: Executor,
  private val errorsSideChannel: ErrorsSideChannel = { _, _ -> }
) {
  fun sendCommand(command: Command): ListenableFuture<SqliteInspectorProtocol.Response> {
    return messenger.sendRawCommand(command.toByteArray()).transform(taskExecutor) {
      val response = SqliteInspectorProtocol.Response.parseFrom(it)
      if (response.hasErrorOccurred()) {
        val errorResponse = response.errorOccurred
        errorsSideChannel(command, errorResponse)
        val message = getErrorMessage(errorResponse.content)
        throw LiveInspectorException(message, errorResponse.content.stackTrace)
      }
      response
    }
  }
}

typealias ErrorsSideChannel = (Command, SqliteInspectorProtocol.ErrorOccurredResponse) -> Unit

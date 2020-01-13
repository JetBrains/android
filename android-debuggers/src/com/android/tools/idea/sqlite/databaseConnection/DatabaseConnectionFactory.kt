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
package com.android.tools.idea.sqlite.databaseConnection

import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.databaseConnection.jdbc.JdbcDatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.ide.PooledThreadExecutor
import java.sql.DriverManager

interface DatabaseConnectionFactory {
  /**
   * Returns a [DatabaseConnection] ready to be used, associated with the sqliteFile passed as argument.
   * @param sqliteFile The file containing the Sqlite database.
   * @param executor An executor for long-running and/or IO-bound tasks, such as [PooledThreadExecutor].
   */
  fun getDatabaseConnection(sqliteFile: VirtualFile, executor: FutureCallbackExecutor): ListenableFuture<DatabaseConnection>

  /**
   * Returns a [DatabaseConnection] ready to be used, associated with a live connection to an on-device inspector.
   * @param id The id of the connection.
   * @param databaseInspectorClient The [AppInspectorClient] used to send messages between studio and an on-device inspector.
   * @param executor An executor for long-running and/or IO-bound tasks, such as [PooledThreadExecutor].
   */
  fun getLiveDatabaseConnection(
    messenger: AppInspectorClient.CommandMessenger,
    id: Int,
    executor: FutureCallbackExecutor
  ): ListenableFuture<DatabaseConnection>
}

class DatabaseConnectionFactoryImpl : DatabaseConnectionFactory {
  companion object {
    private val logger: Logger = Logger.getInstance(DatabaseConnectionFactoryImpl::class.java)
  }

  override fun getDatabaseConnection(sqliteFile: VirtualFile, executor: FutureCallbackExecutor): ListenableFuture<DatabaseConnection> {
    return executor.executeAsync {
      try {
        val url = "jdbc:sqlite:${sqliteFile.path}"
        val connection = DriverManager.getConnection(url)

        logger.info("Successfully opened database: ${sqliteFile.path}")

        return@executeAsync JdbcDatabaseConnection(connection, sqliteFile, executor)

      }
      catch (e: Exception) {
        throw Exception("Error opening Sqlite database file \"${sqliteFile.path}\"", e)
      }
    }
  }

  override fun getLiveDatabaseConnection(
    messenger: AppInspectorClient.CommandMessenger,
    id: Int,
    executor: FutureCallbackExecutor
  ): ListenableFuture<DatabaseConnection> {
    return Futures.immediateFuture(LiveDatabaseConnection(messenger, id, executor))
  }
}
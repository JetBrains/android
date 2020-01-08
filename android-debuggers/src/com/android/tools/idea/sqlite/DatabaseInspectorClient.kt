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

import com.android.tools.idea.appinspection.api.AppInspectionProcessListener
import com.android.tools.idea.appinspection.api.AppInspectionTarget
import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.appinspection.api.AppInspectorJar
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.AppInspectionClientsService
import com.android.tools.idea.appinspection.ide.createJarCopierForProcess
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.transform
import com.android.tools.sql.protocol.SqliteInspection
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.Executor

/**
 * Class used to send and receive messages from an on-device inspector instance.
 */
class DatabaseInspectorClient private constructor(
  private val databaseInspectorProjectService: DatabaseInspectorProjectService,
  messenger: CommandMessenger
) : AppInspectorClient(messenger) {

  companion object {
    private const val inspectorId = "appinspector.sqlite"
    // TODO(b/147635250): Set release directory
    private val inspectorJar =
      AppInspectorJar("live_sql_viewer_dex.jar", "", "../../bazel-bin/tools/base/experimental/live-sql-inspector")

    /**
     * Starts listening for the creation of new connections to a device.
     */
    fun startListeningForPipelineConnections(databaseInspectorProjectService: DatabaseInspectorProjectService, taskExecutor: Executor) {
      AppInspectionClientsService.discovery.addProcessListener(object : AppInspectionProcessListener {
        override fun onProcessConnect(processDescriptor: ProcessDescriptor) {
          val jarCopier = AppInspectionClientsService.discovery.createJarCopierForProcess(processDescriptor)
          AppInspectionClientsService.discovery.attachToProcess(processDescriptor, jarCopier)
            .transform(taskExecutor) { target ->
              launch(databaseInspectorProjectService, target, FutureCallbackExecutor.wrap(taskExecutor))
            }
        }

        override fun onProcessDisconnect(processDescriptor: ProcessDescriptor) {
          // TODO(b/147617372): end inspector session
        }
      }, taskExecutor)
    }

    /**
     * Starts the database inspector in the connected app and begins tracking open databases.
     */
    private fun launch(
      databaseInspectorProjectService: DatabaseInspectorProjectService,
      target: AppInspectionTarget,
      taskExecutor: FutureCallbackExecutor
    ): ListenableFuture<DatabaseInspectorClient> {
      val launchInspectorFuture = target.launchInspector(inspectorId, inspectorJar) { messenger ->
        DatabaseInspectorClient(databaseInspectorProjectService, messenger)
      }

      taskExecutor.catching(launchInspectorFuture, Throwable::class.java) {
        // TODO(b/147617372) databaseInspectorProjectService.showError(message)
        it
      }

      return taskExecutor.transform(launchInspectorFuture) { it.trackDatabases(); it }
    }

    @VisibleForTesting
    fun createDatabaseInspectorClient(
      databaseInspectorProjectService: DatabaseInspectorProjectService,
      messenger: CommandMessenger
    ): DatabaseInspectorClient {
      return DatabaseInspectorClient(databaseInspectorProjectService, messenger)
    }

    @VisibleForTesting
    fun launchInspector(
      databaseInspectorProjectService: DatabaseInspectorProjectService,
      target: AppInspectionTarget,
      taskExecutor: FutureCallbackExecutor
    ): ListenableFuture<DatabaseInspectorClient> {
      return launch(databaseInspectorProjectService, target, taskExecutor)
    }
  }

  override val eventListener: EventListener = object : EventListener {
    override fun onRawEvent(eventData: ByteArray) {
      val event = SqliteInspection.Events.parseFrom(eventData)
      when {
        event.hasDatabaseOpen() -> {
          val openedDatabase = event.databaseOpen
          ApplicationManager.getApplication().invokeLater {
            databaseInspectorProjectService.openSqliteDatabase(messenger, openedDatabase.id, openedDatabase.name)
          }
        }
        event.hasTableUpdate() -> {
          // TODO(b/147617372): what should we do when table has an update?
        }
      }
    }

    override fun onCrashEvent(message: String) {
      // TODO(b/147617372): databaseInspectorProjectService.showError(message)
    }

    override fun onDispose() {
      // TODO(b/147617372): databaseInspectorProjectService.closeAllLiveDatabase()
    }
  }

  /**
   * Tells on-device inspector to start looking for database connections. When a connection is discovered a databaseOpen event is sent.
   */
  private fun trackDatabases() {
    messenger.sendRawCommand(
      SqliteInspection.Commands.newBuilder()
        .setTrackDatabases(SqliteInspection.TrackDatabasesCommand.getDefaultInstance())
        .build()
        .toByteArray()
    )

    // TODO(b/147617372): blocked right now, error handling.
  }
}
/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.tools.idea.concurrent.FutureCallbackExecutor
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.sqlite.model.SqliteModel
import com.android.tools.idea.sqlite.model.SqliteModelListener
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.SqliteView
import com.android.tools.idea.sqlite.ui.SqliteViewListener
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executor

/**
 * Implementation of the application logic related to viewing/editing of a single sqlite database.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */

@UiThread
class SqliteController(
  parentDisposable: Disposable,
  private val model: SqliteModel,
  private val view: SqliteView,
  private val service: SqliteService,
  private val edtExecutor: EdtExecutor,
  taskExecutor: Executor
) : Disposable {
  private val wrappedEdtExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val taskExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(taskExecutor)
  private var currentResultSetController: ResultSetController? = null

  init {
    Disposer.register(parentDisposable, this)
    view.addListener(ViewListener())
    model.addListener(ModelListener())
  }

  fun start() {
    view.setup()
    view.startLoading("Opening Sqlite database")

    val futureSchema = taskExecutor.transformAsync(service.openDatabase()) { service.readSchema() }

    wrappedEdtExecutor.addListener(futureSchema) {
      if (!Disposer.isDisposed(this)) {
        view.stopLoading()
      }
    }

    wrappedEdtExecutor.addCallback(futureSchema, object : FutureCallback<SqliteSchema> {
      override fun onSuccess(result: SqliteSchema?) {
        if (!Disposer.isDisposed(this@SqliteController)) {
          result?.let { refreshDatabase(it) }
        }
      }

      override fun onFailure(t: Throwable) {
        if (!Disposer.isDisposed(this@SqliteController)) {
          view.reportErrorRelatedToService(service, "Error opening Sqlite database", t)
        }
      }
    })
  }

  override fun dispose() { }

  private fun refreshDatabase(schema: SqliteSchema) {
    model.schema = schema
  }

  private inner class ViewListener : SqliteViewListener {
    override fun tableNodeActionInvoked(table: SqliteTable) {
      wrappedEdtExecutor.addCallback(service.readTable(table), object : FutureCallback<SqliteResultSet> {
        override fun onSuccess(result: SqliteResultSet?) {
          result?.let { displayTableContents(table, it) }
        }

        override fun onFailure(t: Throwable) {
          view.reportErrorRelatedToTable(table, "Error opening Sqlite table", t)
        }
      })
    }
  }

  private fun displayTableContents(table: SqliteTable, resultSet: SqliteResultSet) {
    currentResultSetController?.let { Disposer.dispose(it) }
    currentResultSetController = ResultSetController(this, view, table, resultSet, edtExecutor)
    currentResultSetController!!.start()
  }

  private inner class ModelListener : SqliteModelListener {
    override fun schemaChanged(schema: SqliteSchema) {
      logger.info("Schema changed $schema")
      view.displaySchema(schema)
    }

    override fun deviceFileIdChanged(fileId: DeviceFileId?) {
      logger.info("Device file id changed: $fileId")
      //TODO(b/131588252)
    }
  }

  companion object {
    private val logger = Logger.getInstance(SqliteController::class.java)
  }
}
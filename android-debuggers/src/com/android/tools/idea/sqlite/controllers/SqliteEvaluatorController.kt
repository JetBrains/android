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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrent.FutureCallbackExecutor
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewListener
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Implementation of the application logic related to running queries and updates on a sqlite database.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class SqliteEvaluatorController(
  parentDisposable: Disposable,
  private val view: SqliteEvaluatorView,
  private val service: SqliteService,
  private val edtExecutor: FutureCallbackExecutor
) : Disposable {

  private var currentQueryResultSetController: ResultSetController? = null
  private val sqliteEvaluatorViewListener = SqliteEvaluatorViewListenerImpl()

  init {
    Disposer.register(parentDisposable, this)
  }

  fun setUp() {
    view.addListener(sqliteEvaluatorViewListener)
    view.show()
  }

  fun requestFocus() {
    view.requestFocus()
  }

  override fun dispose() {
    view.removeListener(sqliteEvaluatorViewListener)
  }

  private inner class SqliteEvaluatorViewListenerImpl : SqliteEvaluatorViewListener {
    override fun evaluateSqlActionInvoked(sqlInstruction: String) {
      // TODO after introducing the SQL parser this bit should become a bit nicer (?)
      when {
        sqlInstruction.startsWith("CREATE", ignoreCase = true) or
          sqlInstruction.startsWith("DROP", ignoreCase = true) or
          sqlInstruction.startsWith("ALTER", ignoreCase = true) or
          sqlInstruction.startsWith("INSERT", ignoreCase = true) or
          sqlInstruction.startsWith("UPDATE", ignoreCase = true) or
          sqlInstruction.startsWith("DELETE", ignoreCase = true) -> executeUpdate(sqlInstruction)
        else -> executeQuery(sqlInstruction) {
          view.reportErrorRelatedToTable(null, "Error executing sqlQueryCommand", it)
        }
      }
    }

    override fun sessionClosed() {
    }

    private fun executeUpdate(sqlUpdateCommand: String) {
      edtExecutor.addCallback(service.executeUpdate(sqlUpdateCommand), object : FutureCallback<Int> {
        override fun onSuccess(result: Int?) {
          // TODO do we want to update the UI of the dialog?
          view.resetView()
        }

        override fun onFailure(t: Throwable) {
          view.reportErrorRelatedToTable(null, "Error executing update", t)
        }
      })
    }

    private fun executeQuery(sqlQueryCommand: String, doOnFailure: (Throwable) -> Unit) {
      edtExecutor.addCallback(service.executeQuery(sqlQueryCommand), object : FutureCallback<SqliteResultSet> {
        override fun onSuccess(sqliteResultSet: SqliteResultSet?) {
          if(sqliteResultSet == null) return

          currentQueryResultSetController = ResultSetController(
            this@SqliteEvaluatorController,
            view, null, sqliteResultSet,
            edtExecutor
          ).also { it.setUp() }
          currentQueryResultSetController
        }

        override fun onFailure(t: Throwable) {
          doOnFailure(t)
        }
      })
    }
  }
}
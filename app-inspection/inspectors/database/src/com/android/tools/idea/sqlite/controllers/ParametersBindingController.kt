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
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.sqlLanguage.expandCollectionParameters
import com.android.tools.idea.sqlite.sqlLanguage.replaceNamedParametersWithPositionalParameters
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import java.util.LinkedList

/**
 * Implementation of the application logic to show a dialog through which the user can assign values
 * to parameters in a SQLite statement.
 */
@UiThread
class ParametersBindingController(
  private val view: ParametersBindingDialogView,
  private val sqliteStatementPsi: PsiElement,
  private val runStatement: (SqliteStatement) -> Unit
) : Disposable {

  private val listener = ParametersBindingViewListenerImpl()
  private val parameters: List<SqliteParameter>

  init {
    val (_, myParameters) = replaceNamedParametersWithPositionalParameters(sqliteStatementPsi)
    // rename parameters that start with '?' (eg: '?' and '?1') with 'param #'
    parameters =
      myParameters.mapIndexed { i, p ->
        SqliteParameter(if (p.name.startsWith("?")) "param ${i + 1}" else p.name, p.isCollection)
      }
  }

  fun setUp() {
    view.addListener(listener)
    view.showNamedParameters(parameters.toSet())
  }

  fun show() {
    view.show()
  }

  override fun dispose() {
    view.removeListener(listener)
  }

  private inner class ParametersBindingViewListenerImpl : ParametersBindingDialogView.Listener {
    override fun bindingCompletedInvoked(parameters: Map<SqliteParameter, SqliteParameterValue>) {
      val parametersValues =
        this@ParametersBindingController.parameters.map {
          parameters[it] ?: error("No value assigned to parameter $it.")
        }

      val newPsi = expandCollectionParameters(sqliteStatementPsi, LinkedList(parametersValues))
      val (sqliteStatement, _) = replaceNamedParametersWithPositionalParameters(newPsi)
      val sqliteValues =
        parametersValues.flatMap { sqliteParameterValue ->
          when (sqliteParameterValue) {
            is SqliteParameterValue.SingleValue -> listOf(sqliteParameterValue.value)
            is SqliteParameterValue.CollectionValue -> sqliteParameterValue.value
          }
        }

      runStatement(createSqliteStatement(sqliteStatementPsi.project, sqliteStatement, sqliteValues))
    }
  }
}

data class SqliteParameter(val name: String, val isCollection: Boolean = false)

sealed class SqliteParameterValue {
  companion object {
    fun fromAny(vararg values: Any?): SqliteParameterValue {
      return if (values.size == 1) {
        SingleValue(SqliteValue.fromAny(values[0]))
      } else {
        CollectionValue(values.map { SqliteValue.fromAny(it) })
      }
    }
  }
  data class SingleValue(val value: SqliteValue) : SqliteParameterValue()
  data class CollectionValue(val value: List<SqliteValue>) : SqliteParameterValue()
}

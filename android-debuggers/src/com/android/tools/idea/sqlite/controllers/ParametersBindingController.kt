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
import com.android.tools.idea.sqlite.sqlLanguage.inlineParameterValues
import com.android.tools.idea.sqlite.sqlLanguage.replaceNamedParametersWithPositionalParameters
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import java.util.LinkedList

/**
 * Implementation of the application logic to show a dialog through which the user can assign values to parameters in a SQLite statement.
 */
@UiThread
class ParametersBindingController(
  private val view: ParametersBindingDialogView,
  private val sqliteStatementPsi: PsiElement,
  private val runStatement: (SqliteStatement) -> Unit
): Disposable {

  private val listener = ParametersBindingViewListenerImpl()
  private val sqliteStatement: String
  private val parametersNames: List<String>

  init {
    val (mySqliteStatement, myParametersNames) = replaceNamedParametersWithPositionalParameters(sqliteStatementPsi)
    sqliteStatement = mySqliteStatement
    // rename parameters that start with '?' (eg: '?' and '?1') with 'param #'
    parametersNames = myParametersNames.mapIndexed { i, s -> if (s.startsWith("?")) "param ${i+1}" else s }
  }

  fun setUp() {
    view.addListener(listener)
    view.showNamedParameters(parametersNames.toSet())
  }

  fun show() {
    view.show()
  }

  override fun dispose() {
    view.removeListener(listener)
  }

  private inner class ParametersBindingViewListenerImpl : ParametersBindingDialogView.Listener {
    override fun bindingCompletedInvoked(parameters: Map<String, SqliteValue>) {
      val parametersValues = parametersNames.map { parameters[it] ?: error("No value assigned to parameter $it.") }
      val stringRepresentation = inlineParameterValues(sqliteStatementPsi, LinkedList(parametersValues))
      runStatement(SqliteStatement(sqliteStatement, parametersValues, stringRepresentation))
    }
  }
}
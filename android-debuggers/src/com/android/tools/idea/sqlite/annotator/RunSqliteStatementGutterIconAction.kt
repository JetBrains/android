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
package com.android.tools.idea.sqlite.annotator

import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.controllers.ParametersBindingController
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.sqlLanguage.replaceNamedParametersWithPositionalParameters
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.intellij.icons.AllIcons
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JList
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

/**
 * Action triggered when [RunSqliteStatementGutterIconRenderer] is clicked.
 *
 * The action runs the SQLite statement on the open database.
 * If multiple database are open a dialog is shown to allow the user to select the database of interest.
 *
 * To handle SQLite statements with named parameters a dialog is shown to assign a value to the parameters.
 * All named parameters are replaced with positional parameters.
 */
class RunSqliteStatementGutterIconAction(
  private val project: Project,
  private val element: PsiElement,
  private val viewFactory: DatabaseInspectorViewsFactory
) : AnAction() {
  override fun actionPerformed(actionEvent: AnActionEvent) {
    val openDatabases = DatabaseInspectorProjectService.getInstance(project).getOpenDatabases()

    if (openDatabases.isEmpty()) return

    val injectedPsiFile = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(element)
                            .orEmpty()
                            .firstOrNull { it.first.language == AndroidSqlLanguage.INSTANCE }?.first ?: return

    val (sqliteStatement, parametersNames) = replaceNamedParametersWithPositionalParameters(injectedPsiFile)

    if (openDatabases.size == 1) {
      runSqliteStatement(openDatabases.first(), sqliteStatement, parametersNames)
    }
    else if (openDatabases.size > 1) {
      val popupChooserBuilder = JBPopupFactory.getInstance().createPopupChooserBuilder(openDatabases.toList())
      val popup = popupChooserBuilder
        .setTitle("Choose database")
        .setMovable(true)
        .setRenderer(SqliteQueryListCellRenderer())
        .withHintUpdateSupply()
        .setResizable(true)
        .setItemChosenCallback {
          runSqliteStatement(it, sqliteStatement, parametersNames)
        }
        .createPopup()

      if (actionEvent.inputEvent is MouseEvent) {
        val point = RelativePoint(actionEvent.inputEvent as MouseEvent)
        popup.show(point)
      }
      else {
        popup.showInFocusCenter()
      }
    }
  }

  private fun runSqliteStatement(database: SqliteDatabase, sqliteStatement: String, parametersNames: List<String>) {
    if (parametersNames.isEmpty()) {
      DatabaseInspectorProjectService.getInstance(project).runSqliteStatement(database, SqliteStatement(sqliteStatement))
    }
    else {
      val view = viewFactory.createParametersBindingView(project)
      ParametersBindingController(view, sqliteStatement, parametersNames) {
        DatabaseInspectorProjectService.getInstance(project).runSqliteStatement(database, it)
      }.also {
        it.setUp()
        it.show()
        Disposer.register(project, it)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = DatabaseInspectorProjectService.getInstance(project).hasOpenDatabase()
  }

  private class SqliteQueryListCellRenderer : DefaultListCellRenderer() {
    companion object {
      private val cellInsets = JBUI.insets(2, 6)
    }

    private val border = EmptyBorder(
      cellInsets)

    override fun getBorder(): Border = border

    override fun getIconTextGap(): Int = cellInsets.left

    override fun getIcon(): Icon = AllIcons.RunConfigurations.TestState.Run

    override fun getListCellRendererComponent(list: JList<*>,
                                              value: Any?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      if (value is SqliteDatabase) {
        text = value.name
      }

      return component
    }
  }
}
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
package com.android.tools.idea

import com.android.tools.idea.editors.sqlite.SqliteViewer
import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBindParameter
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlVisitor
import com.android.tools.idea.sqlite.controllers.ParametersBindingController
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactory
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactoryImpl
import com.android.tools.idea.sqliteExplorer.SqliteExplorerProjectService
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTFactory
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JList
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

// TODO: rename file
/**
 * Annotator that adds a run action to instances of SQL language in the editor, so that they can be executed in the Sqlite Inspector.
 */
class SqliteQueryAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!SqliteViewer.isFeatureEnabled) return

    val injectedPsiFile = InjectedLanguageManager.getInstance(element.project).getInjectedPsiFiles(element)
                            .orEmpty()
                            .map { it.first }
                            .filter { it.language == AndroidSqlLanguage.INSTANCE }
                            .firstOrNull { it.getUserData(InjectedLanguageManager.FRANKENSTEIN_INJECTION) == null } ?: return

    val injectionHost = InjectedLanguageManager.getInstance(injectedPsiFile.project).getInjectionHost(injectedPsiFile)

    // If the sql statement is defined over multiple strings (eg: "select " + "*" + " from users")
    // different elements ("select ", "*", " from users") correspond to the same injection host ("select ").
    // We don't want to add multiple annotations for these sql statements.
    if (element != injectionHost) return

    holder.createInfoAnnotation(element, null).also { annotation ->
      annotation.gutterIconRenderer = SqliteStatementGutterIconRenderer(element)
    }
  }
}

/**
 * Shows an icon in the gutter when a SQLite statement is recognized. eg. Room @Query annotations.
 */
private data class SqliteStatementGutterIconRenderer(private val element: PsiElement) : GutterIconRenderer() {
  private val sqliteExplorerProjectService = SqliteExplorerProjectService.getInstance(element.project)

  override fun getIcon(): Icon {
    return if (sqliteExplorerProjectService.hasOpenDatabase()) {
      AllIcons.RunConfigurations.TestState.Run
    } else {
      EmptyIcon.ICON_0
    }
  }

  override fun getTooltipText(): String {
    return if (sqliteExplorerProjectService.hasOpenDatabase()) {
      "Run Sqlite statement in Sqlite Inspector"
    } else {
      ""
    }
  }

  override fun isNavigateAction(): Boolean {
    return sqliteExplorerProjectService.hasOpenDatabase()
  }

  override fun getClickAction(): AnAction? {
    if (!sqliteExplorerProjectService.hasOpenDatabase()) return null

    return DatabaseInspectorGutterIconAction(element.project, element, SqliteEditorViewFactoryImpl.getInstance())
  }
}

// TODO move to a separate file
/**
 * Action triggered when [SqliteStatementGutterIconRenderer] is clicked.
 *
 * The action runs the SQLite statement on the open database.
 * If multiple database are open a dialog is shown to allow the user to select the database of interest.
 *
 * To handle SQLite statements with named parameters a dialog is shown to assign a value to the parameters.
 * All named parameters are replaced with positional parameters.
 */
class DatabaseInspectorGutterIconAction(
  private val project: Project,
  private val element: PsiElement,
  private val viewFactory: SqliteEditorViewFactory
) : AnAction() {
  override fun actionPerformed(actionEvent: AnActionEvent) {
    val openDatabases = SqliteExplorerProjectService.getInstance(project).getOpenDatabases()

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
      SqliteExplorerProjectService.getInstance(project).runSqliteStatement(database, SqliteStatement(sqliteStatement))
    }
    else {
      val view = viewFactory.createParametersBindingView(project)
      ParametersBindingController(view, sqliteStatement, parametersNames) {
        SqliteExplorerProjectService.getInstance(project).runSqliteStatement(database, it)
      }.also {
        it.setUp()
        it.show()
        Disposer.register(project, it)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = SqliteExplorerProjectService.getInstance(project).hasOpenDatabase()
  }

  /**
   * Returns a SQLite statement where named parameters have been replaced with positional parameters (?)
   * and the list of named parameters in the original statement.
   * @param psiElement The [PsiElement] corresponding to a SQLite statement.
   * @return The text of the SQLite statement with positional parameters and the list of named parameters.
   */
  private fun replaceNamedParametersWithPositionalParameters(psiElement: PsiElement): Pair<String, List<String>> {
    val psiElementCopy = psiElement.copy()
    val parametersNames = mutableListOf<String>()

    invokeAndWaitIfNeeded {
      runUndoTransparentWriteAction {
        val visitor = object : AndroidSqlVisitor() {
          override fun visitBindParameter(parameter: AndroidSqlBindParameter) {
            parametersNames.add(parameter.text)
            parameter.node.replaceChild(parameter.node.firstChildNode, ASTFactory.leaf(AndroidSqlPsiTypes.NUMBERED_PARAMETER, "?"))
          }
        }

        PsiTreeUtil.processElements(psiElementCopy) { it.accept(visitor); true }
      }
    }
    return Pair(psiElementCopy.text, parametersNames)
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
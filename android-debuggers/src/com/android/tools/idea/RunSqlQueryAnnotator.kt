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
import com.android.tools.idea.sqliteExplorer.SqliteExplorerProjectService
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

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
      annotation.gutterIconRenderer = SqliteQueryGutterIconRenderer(element)
    }
  }
}

private data class SqliteQueryGutterIconRenderer(private val element: PsiElement) : GutterIconRenderer() {

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

    return object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        val injectedPsiFile = InjectedLanguageManager.getInstance(element.project).getInjectedPsiFiles(element)
                                .orEmpty()
                                .map { it.first }
                                .firstOrNull { it.language == AndroidSqlLanguage.INSTANCE } ?: return

        SqliteExplorerProjectService.getInstance(element.project).runQuery(injectedPsiFile.text)
      }

      override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = sqliteExplorerProjectService.hasOpenDatabase()
      }
    }
  }
}
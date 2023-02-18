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
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactoryImpl
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.ui.EmptyIcon
import icons.StudioIcons
import javax.swing.Icon

/**
 * Annotator that adds a run action to instances of SQL language in the editor, so that they can be
 * executed in the Sqlite Inspector.
 */
class RunSqliteStatementAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val injectedPsiFile =
      InjectedLanguageManager.getInstance(element.project)
        .getInjectedPsiFiles(element)
        .orEmpty()
        .map { it.first }
        .filter { it.language == AndroidSqlLanguage.INSTANCE }
        .firstOrNull { it.getUserData(InjectedLanguageManager.FRANKENSTEIN_INJECTION) == null }
        ?: return

    val injectionHost =
      InjectedLanguageManager.getInstance(injectedPsiFile.project).getInjectionHost(injectedPsiFile)

    // If the sql statement is defined over multiple strings (eg: "select " + "*" + " from users")
    // different elements ("select ", "*", " from users") correspond to the same injection host
    // ("select ").
    // We don't want to add multiple annotations for these sql statements.
    if (element != injectionHost) return

    holder
      .newSilentAnnotation(HighlightSeverity.INFORMATION)
      .gutterIconRenderer(RunSqliteStatementGutterIconRenderer(element))
      .create()
  }
}

/**
 * Shows an icon in the gutter when a SQLite statement is recognized. eg. Room @Query annotations.
 */
private data class RunSqliteStatementGutterIconRenderer(private val element: PsiElement) :
  GutterIconRenderer() {
  private val sqliteExplorerProjectService =
    DatabaseInspectorProjectService.getInstance(element.project)

  override fun getIcon(): Icon {
    return if (sqliteExplorerProjectService.hasOpenDatabase()) {
      StudioIcons.DatabaseInspector.NEW_QUERY
    } else {
      EmptyIcon.ICON_0
    }
  }

  override fun getTooltipText(): String {
    return "Run Sqlite statement in Database Inspector"
  }

  override fun isNavigateAction(): Boolean {
    return sqliteExplorerProjectService.hasOpenDatabase()
  }

  override fun getClickAction(): AnAction? {
    return RunSqliteStatementGutterIconAction(
      element.project,
      element,
      DatabaseInspectorViewsFactoryImpl.getInstance()
    )
  }
}

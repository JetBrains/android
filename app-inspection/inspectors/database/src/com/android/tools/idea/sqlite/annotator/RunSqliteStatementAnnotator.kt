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
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle.message
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactoryImpl
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons
import javax.swing.JLabel

private const val ROOM_ENTITY_ANDROIDX: String = "androidx.room.Entity"
private const val ROOM_ENTITY_ARCH: String = "android.arch.persistence.room.Entity"

/**
 * Shows an icon in the gutter when a SQLite statement is recognized. e.g. Room @Query annotations.
 */
internal class RunSqliteStatementAnnotator : LineMarkerProviderDescriptor() {
  override fun getId(): String = "RunSqliteStatement"

  override fun getName(): String = message("gutter.name.run.sqlite.statement")

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(
    elements: List<PsiElement>,
    result: MutableCollection<in LineMarkerInfo<*>>
  ) {
    val first = elements.firstOrNull() ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(first) ?: return

    if (
      !JavaLibraryUtil.hasLibraryClass(module, ROOM_ENTITY_ANDROIDX) &&
        !JavaLibraryUtil.hasLibraryClass(module, ROOM_ENTITY_ARCH)
    ) {
      return
    }

    val injectedLanguageManager = InjectedLanguageManager.getInstance(first.project)
    for (element in elements) {
      collectRunMarkers(injectedLanguageManager, element, result)
    }
  }

  private fun collectRunMarkers(
    injectedLanguageManager: InjectedLanguageManager,
    element: PsiElement,
    result: MutableCollection<in LineMarkerInfo<*>>
  ) {
    if (element.children.isNotEmpty()) return // not leaf element

    val targetElement =
      if (element is PsiLanguageInjectionHost) element
      else element.parent as? PsiLanguageInjectionHost
    if (targetElement == null) return

    val injectedPsiFile =
      injectedLanguageManager
        .getInjectedPsiFiles(targetElement)
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
    if (targetElement != injectionHost) return

    if (targetElement != element && targetElement.firstChild != element) return

    // it is much easier to always show icon and fallback to warning balloon if no database
    result.add(
      LineMarkerInfo(
        element,
        element.textRange,
        StudioIcons.DatabaseInspector.NEW_QUERY,
        { message("marker.run.sqlite.statement") },
        getNavHandler(SmartPointerManager.createPointer(injectionHost)),
        Alignment.CENTER,
        { message("marker.run.sqlite.statement") }
      )
    )
  }

  private fun getNavHandler(
    pointer: SmartPsiElementPointer<PsiLanguageInjectionHost>
  ): GutterIconNavigationHandler<PsiElement> {
    return GutterIconNavigationHandler { event, element ->
      val targetElement = pointer.element ?: return@GutterIconNavigationHandler

      val sqliteExplorerProjectService =
        DatabaseInspectorProjectService.getInstance(element.project)
      if (!sqliteExplorerProjectService.hasOpenDatabase()) {
        JBPopupFactory.getInstance()
          .createBalloonBuilder(JLabel(message("no.db.in.inspector")))
          .setFillColor(HintUtil.getWarningColor())
          .createBalloon()
          .show(RelativePoint(event), Balloon.Position.above)

        return@GutterIconNavigationHandler
      }

      val action =
        RunSqliteStatementGutterIconAction(
          element.project,
          targetElement,
          DatabaseInspectorViewsFactoryImpl.getInstance()
        )
      action.actionPerformed(
        AnActionEvent.createFromAnAction(action, event, "", DataContext.EMPTY_CONTEXT)
      )
    }
  }
}

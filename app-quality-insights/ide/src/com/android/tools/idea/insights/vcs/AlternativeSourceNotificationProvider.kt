/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.vcs

import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ui.vcs.ContextDataForDiff
import com.android.tools.idea.insights.ui.vcs.InsightsDiffVirtualFile
import com.android.tools.idea.insights.ui.vcs.goToDiff
import com.android.tools.idea.insights.vcs.AlternativeSourceNotificationProvider.AppScopeMatchResult
import com.android.tools.idea.model.AndroidModel
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JPanel
import org.jetbrains.kotlin.idea.util.projectStructure.getModule

/**
 * Provides alternative sources options in the top banner of the diff view, if there's ambiguity of
 * sources and the issue that's under investigation occurred in another build variant instead of the
 * currently selected one.
 *
 * Note we only show a banner when [AppScopeMatchResult.MISMATCH] to avoid false positives.
 */
class AlternativeSourceNotificationProvider : EditorNotificationProvider {
  val HIDDEN_KEY =
    Key.create<Boolean>(
      "com.android.tools.idea.insights.vcs.alternative.source.notification.panel.hidden"
    )

  val APP_SCOPE_MATCH_RESULT_KEY =
    Key.create<AppScopeMatchResult>(
      "com.android.tools.idea.insights.vcs.alternative.source.notification.panel.app.scope.match.result"
    )

  val TEXT_WARNING =
    "The historical source might not match if the issue occurred in another build variant."

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<FileEditor, EditorNotificationPanel?>? {
    if (file !is InsightsDiffVirtualFile) return null

    val diffRequestContext = file.provider.insightsContext
    val diffContextVFile = diffRequestContext.filePath.virtualFile ?: return null

    // Locate other files not in scope (e.g. files from inactive variant src set)
    val otherSources =
      diffContextVFile.locateOtherFilesWithSameName(project).takeUnless { it.isEmpty() }
        ?: return null

    return Function { fileEditor ->
      if (fileEditor.getUserData(HIDDEN_KEY) == true) return@Function null

      val scopeMatchResult =
        fileEditor.getUserData(APP_SCOPE_MATCH_RESULT_KEY)
          ?: diffRequestContext.origin.ifMatchesCurrent(diffContextVFile, project).also {
            fileEditor.putUserData(APP_SCOPE_MATCH_RESULT_KEY, it)
          }

      return@Function if (scopeMatchResult == AppScopeMatchResult.MISMATCH) {
        createEditorNotificationPanel(
          diffContextVFile,
          otherSources,
          diffRequestContext,
          fileEditor,
          file,
          project,
        )
      } else null
    }
  }

  private fun createEditorNotificationPanel(
    diffContextVFile: VirtualFile,
    otherSources: List<VirtualFile>,
    diffRequestContext: ContextDataForDiff,
    fileEditor: FileEditor,
    file: VirtualFile,
    project: Project,
  ): EditorNotificationPanel {
    return object : EditorNotificationPanel(fileEditor, Status.Warning) {
      init {
        val items =
          (listOf(diffContextVFile) + otherSources)
            .map { ComboBoxFileElement(it, project) }
            .toTypedArray()
        val onClick = addActionListener@{ selectedItem: Any? ->
          val selected =
            (selectedItem as? ComboBoxFileElement)?.virtualFile ?: return@addActionListener
          if (selected == diffContextVFile) return@addActionListener

          val newContext = diffRequestContext.copy(filePath = selected.toVcsFilePath())

          fileEditor.file?.let { FileEditorManager.getInstance(project).closeFile(it) }
          goToDiff(newContext, project)
        }

        myLinksPanel.add(
          JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JBLabel("Alternative sources: "), BorderLayout.WEST)
            add(ComboBox(items).apply { addActionListener { onClick(selectedItem) } })
          }
        )

        createActionLabel("Hide") {
          fileEditor.putUserData(HIDDEN_KEY, true)
          EditorNotifications.getInstance(project).updateNotifications(file)
        }

        text = TEXT_WARNING
      }
    }
  }

  private fun Connection?.ifMatchesCurrent(
    context: VirtualFile,
    project: Project,
  ): AppScopeMatchResult {
    this ?: return AppScopeMatchResult.UNKNOWN

    // We don't try hard to check dependent/dependency modules for getting the current app id.
    val currentAppId =
      context
        .getModule(project)
        ?.let { AndroidModel.get(it) }
        ?.applicationId
        ?.takeUnless { it == AndroidModel.UNINITIALIZED_APPLICATION_ID }
        ?: return AppScopeMatchResult.UNKNOWN

    return if (currentAppId == appId) AppScopeMatchResult.MATCH else AppScopeMatchResult.MISMATCH
  }

  /** Returns files that have the same file name and package qualifier with the given file. */
  private fun VirtualFile.locateOtherFilesWithSameName(project: Project): List<VirtualFile> {
    val psiManager = PsiManager.getInstance(project)
    val packageName =
      (psiManager.findFile(this) as? PsiClassOwner)?.packageName ?: return emptyList()

    return runReadAction {
      FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
        .filterNot { it == this }
        .filter { packageName == (psiManager.findFile(it) as? PsiClassOwner)?.packageName }
        .toList()
    }
  }

  enum class AppScopeMatchResult {
    MATCH,
    MISMATCH,
    UNKNOWN,
  }

  data class ComboBoxFileElement(val virtualFile: VirtualFile, val project: Project) {
    private val uniqueFilePath =
      UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, virtualFile)

    override fun toString(): String {
      return uniqueFilePath
    }
  }
}

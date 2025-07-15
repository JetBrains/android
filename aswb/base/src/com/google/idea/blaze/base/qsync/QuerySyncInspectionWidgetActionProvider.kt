/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup
import com.google.idea.blaze.base.qsync.action.PopupPositioner
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors
import com.google.idea.blaze.base.qsync.action.getVirtualFiles
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import javax.swing.JComponent
import kotlinx.coroutines.guava.asDeferred

/**
 * Provides the actions to be used with the inspection widget. The inspection widget is the
 * tri-color icon at the top-right of files showing analysis results. This class provides the action
 * that sits there and builds the file dependencies and enables analysis
 */
class QuerySyncInspectionWidgetActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project = editor.project ?: return null
    return when {
      Blaze.getProjectType(project) != BlazeImportSettings.ProjectType.QUERY_SYNC -> null
      editor.editorKind != EditorKind.MAIN_EDITOR -> null
      else -> BuildDependenciesAction(project, editor)
    }
  }

  private class BuildDependenciesAction(private val project: Project, private val editor: Editor) :
    AnAction(""), CustomComponentAction, DumbAware {

    private val buildDepsHelper: BuildDependenciesHelper = BuildDependenciesHelper(project)
    private val syncManager: QuerySyncManager = QuerySyncManager.getInstance(project)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      val vfs = e.getVirtualFiles() ?: return
      val querySyncActionStats = QuerySyncActionStatsScope.createForFiles(javaClass, e, ImmutableList.copyOf(vfs))
      buildDepsHelper.determineTargetsAndRun(
        workspaceRelativePaths = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(e.project, vfs),
        disambiguateTargetPrompt = BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt(
          PopupPositioner.showUnderneathClickedComponentOrCentered(e)),
        targetDisambiguationAnchors = TargetDisambiguationAnchors.NONE,
        querySyncActionStats = querySyncActionStats
      ) { labels ->
        syncManager.enableAnalysis(
          labels,
          querySyncActionStats,
          QuerySyncManager.TaskOrigin.USER_ACTION
        ).asDeferred()
      }
    }

    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.text = ""
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      val vf = psiFile?.virtualFile ?: let {
        presentation.setEnabled(false)
        return
      }

      val currentOperation = QuerySyncManager.getInstance(project).currentOperation()
      if (currentOperation.isPresent) {
        presentation.isEnabled =false
        presentation.text =
          when (QuerySyncManager.OperationType.SYNC) {
            currentOperation.get() -> "Syncing project..."
            else -> "Building dependencies..."
          }
        return
      }
      val toBuild = buildDepsHelper.getTargetsToEnableAnalysisForPaths(
        WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(
          e.project,
          ImmutableList.of(vf)
        )
      )

      if (toBuild.isEmpty()) {
        // The file is not recognised as potentially analyzable. If the file is not synced, it is represented by a
        // `TargetsToBuild.UnknownSourceFile`.
        presentation.isEnabled = false
        return
      }

      presentation.isEnabled = true
      val singleSourceFileGroup = toBuild.singleOrNull() as? TargetsToBuild.SourceFile
      if (singleSourceFileGroup != null && QuerySyncSettings.getInstance().showDetailedInformationInEditor()) {
        val missing = buildDepsHelper.getSourceFileMissingDepsCount(singleSourceFileGroup)
        if (missing > 0) {
          val dependency = StringUtil.pluralize("dependency", missing)
          presentation.text = "Analysis disabled - missing $missing $dependency"
        }
      }
    }

    override fun createCustomComponent(
      presentation: Presentation, place: String
    ): JComponent {
      presentation.icon = AllIcons.Actions.Compile
      presentation.text = ""
      return QuerySyncWidget(this, presentation, place, editor, buildDepsHelper).component()
    }
  }
}

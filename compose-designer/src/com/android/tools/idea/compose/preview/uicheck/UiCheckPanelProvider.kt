/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.uicheck

import com.android.tools.idea.common.error.DesignerCommonIssuePanel
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.error.NotSuppressedFilter
import com.android.tools.idea.common.error.UICheckNodeFactory
import com.android.tools.idea.compose.preview.ComposeStudioBotActionFactory
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils.addTab
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils.getTabById
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.SmartPsiElementPointer

val TAB_PREVIEW_DEFINITION: Key<SmartPsiElementPointer<*>> =
  Key.create(IssuePanelService::class.java.name + "_UiCheckPreviewDef")

val TAB_IS_WEAR_PREVIEW: Key<Boolean> =
  Key.create(IssuePanelService::class.java.name + "_UiCheckWearPreview")

class UiCheckPanelProvider(
  private val instance: ComposePreviewElementInstance<*>,
  private val isWearPreview: Boolean,
  private val project: Project,
) : ProblemsViewPanelProvider {
  override fun create(): ProblemsViewTab? {
    val problemsViewWindow = ProblemsView.getToolWindow(project) ?: return null

    return DesignerCommonIssuePanel(
      problemsViewWindow.disposable,
      project,
      !problemsViewWindow.anchor.isHorizontal,
      instance.displaySettings.name,
      instance.instanceId,
      { UICheckNodeFactory },
      NotSuppressedFilter,
      { "UI Check did not find any issues to report" },
      ::fixWithAiActionProvider,
    ) { content ->
      (instance.previewElementDefinition as? SmartPsiElementPointer<*>)?.let {
        content.putUserData(TAB_PREVIEW_DEFINITION, it)
      }
      content.putUserData(TAB_IS_WEAR_PREVIEW, isWearPreview)
      content.isPinnable = true
      content.isCloseable = true
    }
  }

  fun getPanel(): DesignerCommonIssuePanel {
    val id = instance.instanceId
    val existingTab = getTabById(project, id) as? DesignerCommonIssuePanel
    if (existingTab != null) {
      return existingTab
    }
    addTab(project, this)
    return getTabById(project, id) as DesignerCommonIssuePanel
  }

  /**
   * Fixes the given [issue] using StudioBot if it is a VisualLintRenderIssue. The [issue] is
   * expected to be an accessibility issue found in the UI Check Panel.
   *
   * @param issue The [Issue] to fix.
   */
  private fun fixWithAiActionProvider(issue: Issue): AnAction? {
    if (StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.get() && issue is VisualLintRenderIssue)
      return ComposeStudioBotActionFactory.EP_NAME.extensionList
        .firstOrNull()
        ?.fixVisualLintIssuesAction()
    return null
  }
}

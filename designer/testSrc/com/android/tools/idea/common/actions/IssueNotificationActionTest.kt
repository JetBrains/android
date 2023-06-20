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
package com.android.tools.idea.common.actions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.error.IssueSourceWithFile
import com.android.tools.idea.common.error.TestIssue
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class IssueNotificationActionTest {

  @Rule
  @JvmField
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testOnlyHaveVisualLintIssue() {
    val action = IssueNotificationAction()
    val surface = mock<NlDesignSurface>()

    val issueModel = IssueModel(projectRule.testRootDisposable, projectRule.project)
    whenever(surface.issueModel).thenReturn(issueModel)
    whenever(surface.project).thenReturn(projectRule.project)
    whenever(surface.supportedActions).thenReturn(setOf(NlSupportedActions.TOGGLE_ISSUE_PANEL))

    val mockedFile = mock<VirtualFile>()
    val model = mock<NlModel>()
    whenever(model.virtualFile).thenReturn(mockedFile)
    whenever(surface.models).thenReturn(ImmutableList.of(model))

    val context = DataContext { dataId ->
      when {
        PlatformDataKeys.PROJECT.`is`(dataId) -> projectRule.project
        DESIGN_SURFACE.`is`(dataId) -> surface
        else -> null
      }
    }
    val presentation = Presentation()
    val actionEvent = AnActionEvent(null, context, "", presentation, ActionManager.getInstance(), 0)
    val visualLintIssueModel = VisualLintService.getInstance(projectRule.project).issueModel


    action.update(actionEvent)
    assertEquals(IssueNotificationAction.DISABLED_ICON, presentation.icon)
    assertEquals(IssueNotificationAction.NO_ISSUE, presentation.description)

    run {
      val infoIssueProvider = createSingleIssueProviderWithSeverity(HighlightSeverity.INFORMATION, mockedFile)
      visualLintIssueModel.addIssueProvider(infoIssueProvider)
      action.update(actionEvent)
      assertEquals(StudioIcons.Common.INFO_INLINE, presentation.icon)
      assertEquals(IssueNotificationAction.SHOW_ISSUE, presentation.description)
    }

    run {
      val warningIssueProvider = createSingleIssueProviderWithSeverity(HighlightSeverity.WARNING, mockedFile)
      visualLintIssueModel.addIssueProvider(warningIssueProvider)
      action.update(actionEvent)
      assertEquals(StudioIcons.Common.WARNING_INLINE, presentation.icon)
      assertEquals(IssueNotificationAction.SHOW_ISSUE, presentation.description)
    }

    run {
      val errorIssueProvider = createSingleIssueProviderWithSeverity(HighlightSeverity.ERROR, mockedFile)
      visualLintIssueModel.addIssueProvider(errorIssueProvider)
      action.update(actionEvent)
      assertEquals(StudioIcons.Common.ERROR_INLINE, presentation.icon)
      assertEquals(IssueNotificationAction.SHOW_ISSUE, presentation.description)
    }
  }

  private fun createSingleIssueProviderWithSeverity(severity: HighlightSeverity, file: VirtualFile): IssueProvider {
    return object : IssueProvider() {
      override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
        val issue = TestIssue(source = IssueSourceWithFile(file), severity = severity)
        issueListBuilder.add(issue)
      }
    }
  }
}

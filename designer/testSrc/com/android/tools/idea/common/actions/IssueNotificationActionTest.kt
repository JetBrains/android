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
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IssueNotificationActionTest {

  @Rule @JvmField val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testOnlyHaveVisualLintIssue() {
    val action = IssueNotificationAction()
    val surface = mock<NlDesignSurface>()

    val issueModel = IssueModel(projectRule.testRootDisposable, projectRule.project)
    whenever(surface.issueModel).thenReturn(issueModel)
    whenever(surface.project).thenReturn(projectRule.project)
    whenever(surface.supportedActions)
      .thenReturn(ImmutableSet.of(NlSupportedActions.TOGGLE_ISSUE_PANEL))

    val issueProvider =
      object : VisualLintIssueProvider(projectRule.testRootDisposable) {
        override fun customizeIssue(issue: VisualLintRenderIssue) = Unit
      }
    whenever(surface.visualLintIssueProvider).thenReturn(issueProvider)

    val mockedFile = mock<VirtualFile>()
    val model = mock<NlModel>()
    whenever(model.virtualFile).thenReturn(mockedFile)
    whenever(surface.models).thenReturn(ImmutableList.of(model))

    val actionEvent =
      TestActionEvent.createTestEvent { dataId ->
        when {
          PlatformDataKeys.PROJECT.`is`(dataId) -> projectRule.project
          DESIGN_SURFACE.`is`(dataId) -> surface
          else -> null
        }
      }

    action.update(actionEvent)
    assertTrue(actionEvent.presentation.isEnabled)
    assertEquals(IssueNotificationAction.DISABLED_ICON, actionEvent.presentation.icon)
    assertEquals(IssueNotificationAction.NO_ISSUE, actionEvent.presentation.description)

    run {
      issueProvider.addAllIssues(
        listOf(createSingleIssueWithSeverity(HighlightSeverity.INFORMATION, model))
      )
      action.update(actionEvent)
      assertEquals(StudioIcons.Common.INFO_INLINE, actionEvent.presentation.icon)
      assertEquals(IssueNotificationAction.SHOW_ISSUE, actionEvent.presentation.description)
      issueProvider.clear()
    }

    run {
      issueProvider.addAllIssues(
        listOf(createSingleIssueWithSeverity(HighlightSeverity.WARNING, model))
      )
      action.update(actionEvent)
      assertTrue(actionEvent.presentation.isEnabled)
      assertEquals(StudioIcons.Common.WARNING_INLINE, actionEvent.presentation.icon)
      assertEquals(IssueNotificationAction.SHOW_ISSUE, actionEvent.presentation.description)
      issueProvider.clear()
    }

    run {
      issueProvider.addAllIssues(
        listOf(createSingleIssueWithSeverity(HighlightSeverity.ERROR, model))
      )
      action.update(actionEvent)
      assertTrue(actionEvent.presentation.isEnabled)
      assertEquals(StudioIcons.Common.ERROR_INLINE, actionEvent.presentation.icon)
      assertEquals(IssueNotificationAction.SHOW_ISSUE, actionEvent.presentation.description)
      issueProvider.clear()
    }
  }

  @Test
  fun testActionNotVisibleIfActionIsNotSupported() {
    val surface = mock<NlDesignSurface>()

    whenever(surface.project).thenReturn(projectRule.project)
    whenever(surface.supportedActions).thenReturn(ImmutableSet.of())

    val actionEvent =
      TestActionEvent.createTestEvent { dataId ->
        when {
          PlatformDataKeys.PROJECT.`is`(dataId) -> projectRule.project
          DESIGN_SURFACE.`is`(dataId) -> surface
          else -> null
        }
      }
    val action = IssueNotificationAction()
    action.update(actionEvent)
    assertFalse(actionEvent.presentation.isEnabled)
  }

  private fun createSingleIssueWithSeverity(severity: HighlightSeverity, nlModel: NlModel) =
    VisualLintRenderIssue.builder()
      .summary("")
      .severity(severity)
      .contentDescriptionProvider { HtmlBuilder() }
      .model(nlModel)
      .components(mutableListOf(NlComponent(nlModel, 570L)))
      .type(VisualLintErrorType.BOUNDS)
      .build()
}

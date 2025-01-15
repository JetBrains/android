/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.error.TestIssue
import com.android.tools.idea.common.error.createTestVisualLintRenderIssue
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.visual.visuallint.ViewVisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.visuallint.VisualLintErrorType
import com.google.common.collect.ImmutableCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class VisualizationFormVisualLintHandlerTest {

  @Rule @JvmField val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testAddIssue() {
    val issueModel = IssueModel(rule.projectRule.testRootDisposable, rule.project)
    val handler =
      VisualizationFormVisualLintHandler(rule.testRootDisposable, rule.project, issueModel)

    assertEquals(0, issueModel.issues.size)

    val handlerIssueProvider = handler.lintIssueProvider
    val fakeNlModel = mock<NlModel>()
    val fakeNlComponent = mock<NlComponent>()
    Mockito.`when`(fakeNlComponent.model).thenReturn(fakeNlModel)

    val issueProvider = ViewVisualLintIssueProvider(rule.testRootDisposable)
    val localIssues =
      listOf(
        createTestVisualLintRenderIssue(
          VisualLintErrorType.BOUNDS,
          listOf(fakeNlComponent),
          issueProvider,
          "bounds",
        ),
        createTestVisualLintRenderIssue(
          VisualLintErrorType.OVERLAP,
          listOf(fakeNlComponent),
          issueProvider,
          "overlap",
        ),
      )
    handlerIssueProvider.addAllIssues(localIssues)
    issueModel.updateErrorsList()

    // Compare as the sets, because we don't care about the order in issue model.
    assertEquals(localIssues.toSet(), issueModel.issues.toSet())

    handler.onDeactivate()
    assertTrue(issueModel.issues.isEmpty())
  }

  @Test
  fun testActivateClearBackgroundLintIssue() {
    val handler =
      VisualizationFormVisualLintHandler(
        rule.testRootDisposable,
        rule.project,
        IssueModel(rule.projectRule.testRootDisposable, rule.project),
      )

    val service = VisualLintService.getInstance(rule.project)
    val issueModel = service.issueModel
    val boundIssues = listOf(TestIssue("bound1"), TestIssue("bound2"))
    issueModel.addIssueProvider(
      object : IssueProvider() {
        override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
          issueListBuilder.addAll(boundIssues)
        }
      }
    )

    issueModel.updateErrorsList()

    // Compare as the sets, because we don't care about the order in issue model.
    assertEquals(boundIssues.toSet(), service.issueModel.issues.toSet())

    handler.onActivate()
    assertTrue(service.issueModel.issues.isEmpty())
  }
}

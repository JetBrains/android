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
package com.android.tools.idea.common.error

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.visual.visuallint.ViewVisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DesignerCommonIssueProviderTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testReceiveMessageFromTopic() {
    val project = projectRule.project
    val provider =
      DesignToolsIssueProvider(
        projectRule.testRootDisposable,
        project,
        EmptyFilter,
        SHARED_ISSUE_PANEL_TAB_ID,
      )

    var count = 0
    val listener: () -> Unit = { count++ }
    provider.registerUpdateListener(listener)

    val source = Any()
    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, emptyList())
    assertEquals(1, count)

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, emptyList())
    assertEquals(2, count)
  }

  @Test
  fun testUpdateIssuesFromSameSource() {
    val project = projectRule.project
    val provider =
      DesignToolsIssueProvider(
        projectRule.testRootDisposable,
        project,
        EmptyFilter,
        SHARED_ISSUE_PANEL_TAB_ID,
      )

    assertEmpty(provider.getFilteredIssues())

    val issueSource = Any()

    val issueA = TestIssue(summary = "IssueA")
    val issueB = TestIssue(summary = "IssueB")
    val issueC = TestIssue(summary = "IssueC")
    val issueD = TestIssue(summary = "IssueD")

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(issueSource, listOf(issueA, issueB))
    assertEquals(setOf(issueA, issueB), provider.getFilteredIssues().toSet())

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(issueSource, listOf(issueA, issueB, issueC))
    assertEquals(setOf(issueA, issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(issueSource, listOf(issueB, issueC))
    assertEquals(setOf(issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(issueSource, listOf(issueB, issueD))
    assertEquals(setOf(issueB, issueD), provider.getFilteredIssues().toSet())

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(issueSource, emptyList())
    assertEquals(setOf<Issue>(), provider.getFilteredIssues().toSet())
  }

  @Test
  fun testUpdateIssuesFromMultipleSource() {
    val project = projectRule.project
    val provider =
      DesignToolsIssueProvider(
        projectRule.testRootDisposable,
        project,
        EmptyFilter,
        SHARED_ISSUE_PANEL_TAB_ID,
      )

    assertEmpty(provider.getFilteredIssues())

    val source1 = Any()
    val source2 = Any()

    val issueA = TestIssue(summary = "IssueA")
    val issueB = TestIssue(summary = "IssueB")
    val issueC = TestIssue(summary = "IssueC")
    val issueD = TestIssue(summary = "IssueD")

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(source1, listOf(issueA, issueB))
    assertEquals(setOf(issueA, issueB), provider.getFilteredIssues().toSet())

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(source2, listOf(issueC))
    assertEquals(setOf(issueA, issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(source1, listOf(issueB))
    assertEquals(setOf(issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(source2, listOf(issueD))
    assertEquals(setOf(issueB, issueD), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source1, emptyList())
    assertEquals(setOf(issueD), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source2, emptyList())
    assertEquals(setOf<Issue>(), provider.getFilteredIssues().toSet())
  }

  @Test
  fun testNotSuppressFilter() {
    val model1 = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout1.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight(),
        )
        .build()
    }

    val model2 = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout2.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .withAttribute(
              SdkConstants.TOOLS_URI,
              SdkConstants.ATTR_IGNORE,
              VisualLintErrorType.BOUNDS.ignoredAttributeValue,
            ),
        )
        .build()
    }

    val filter = NotSuppressedFilter

    val issueProvider = ViewVisualLintIssueProvider(projectRule.testRootDisposable)
    val visualLintIssue1 =
      createTestVisualLintRenderIssue(
        VisualLintErrorType.BOUNDS,
        model1.treeReader.components,
        issueProvider,
        "",
      )
    val visualLintIssue2 =
      createTestVisualLintRenderIssue(
        VisualLintErrorType.BOUNDS,
        model2.treeReader.components,
        issueProvider,
        "",
      )
    assertTrue(filter.invoke(visualLintIssue1))
    assertFalse(filter.invoke(visualLintIssue2))
  }

  @RunsInEdt
  @Test
  fun testSelectedEditorFilter() {
    val model1 = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout1.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight(),
        )
        .build()
    }

    val model2 = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout2.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight(),
        )
        .build()
    }

    val filter = SelectedEditorFilter(projectRule.project)
    val issueProvider = ViewVisualLintIssueProvider(projectRule.testRootDisposable)
    val visualLintIssue1 =
      createTestVisualLintRenderIssue(
        VisualLintErrorType.BOUNDS,
        model1.treeReader.components,
        issueProvider,
        "",
      )
    val visualLintIssue2 =
      createTestVisualLintRenderIssue(
        VisualLintErrorType.BOUNDS,
        model2.treeReader.components,
        issueProvider,
        "",
      )

    runInEdtAndWait { projectRule.fixture.openFileInEditor(model1.virtualFile) }
    assertTrue(filter.invoke(visualLintIssue1))
    assertFalse(filter.invoke(visualLintIssue2))

    runInEdtAndWait { projectRule.fixture.openFileInEditor(model2.virtualFile) }
    assertFalse(filter.invoke(visualLintIssue1))
    assertTrue(filter.invoke(visualLintIssue2))
  }
}

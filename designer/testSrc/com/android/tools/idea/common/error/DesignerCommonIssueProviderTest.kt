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
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class DesignerCommonIssueProviderTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testReceiveMessageFromTopic() {
    val project = projectRule.project
    val provider = DesignToolsIssueProvider(projectRule.testRootDisposable, project, EmptyFilter)

    val listener = mock(Runnable::class.java)
    provider.registerUpdateListener(listener)

    val source = Any()
    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, emptyList())
    verify(listener).run()

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, emptyList())
    verify(listener, times(2)).run()
  }

  @Test
  fun testUpdateIssuesFromSameSource() {
    val project = projectRule.project
    val provider = DesignToolsIssueProvider(projectRule.testRootDisposable, project, EmptyFilter)

    assertEmpty(provider.getFilteredIssues())

    val issueSource = Any()

    val issueA = TestIssue(summary = "IssueA")
    val issueB = TestIssue(summary = "IssueB")
    val issueC = TestIssue(summary = "IssueC")
    val issueD = TestIssue(summary = "IssueD")

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(issueSource, listOf(issueA, issueB))
    assertEquals(setOf(issueA, issueB), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(issueSource, listOf(issueA, issueB, issueC))
    assertEquals(setOf(issueA, issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(issueSource, listOf(issueB, issueC))
    assertEquals(setOf(issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(issueSource, listOf(issueB, issueD))
    assertEquals(setOf(issueB, issueD), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(issueSource, emptyList())
    assertEquals(setOf<Issue>(), provider.getFilteredIssues().toSet())
  }

  @Test
  fun testUpdateIssuesFromMultipleSource() {
    val project = projectRule.project
    val provider = DesignToolsIssueProvider(projectRule.testRootDisposable, project, EmptyFilter)

    assertEmpty(provider.getFilteredIssues())

    val source1 = Any()
    val source2 = Any()

    val issueA = TestIssue(summary = "IssueA")
    val issueB = TestIssue(summary = "IssueB")
    val issueC = TestIssue(summary = "IssueC")
    val issueD = TestIssue(summary = "IssueD")

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source1, listOf(issueA, issueB))
    assertEquals(setOf(issueA, issueB), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source2, listOf(issueC))
    assertEquals(setOf(issueA, issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source1, listOf(issueB))
    assertEquals(setOf(issueB, issueC), provider.getFilteredIssues().toSet())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source2, listOf(issueD))
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
          .matchParentHeight()
      ).build()
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
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_IGNORE, VisualLintErrorType.BOUNDS.ignoredAttributeValue)
      ).build()
    }

    val filter = NotSuppressedFilter

    val visualLintIssue1 = createTestVisualLintRenderIssue(VisualLintErrorType.BOUNDS, model1.components, "")
    val visualLintIssue2 = createTestVisualLintRenderIssue(VisualLintErrorType.BOUNDS, model2.components, "")

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
          .matchParentHeight()
      ).build()
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
      ).build()
    }

    val filter = SelectedEditorFilter(projectRule.project)
    val visualLintIssue1 = createTestVisualLintRenderIssue(VisualLintErrorType.BOUNDS, model1.components, "")
    val visualLintIssue2 = createTestVisualLintRenderIssue(VisualLintErrorType.BOUNDS, model2.components, "")

    runInEdtAndWait { projectRule.fixture.openFileInEditor(model1.virtualFile) }
    assertTrue(filter.invoke(visualLintIssue1))
    assertFalse(filter.invoke(visualLintIssue2))

    runInEdtAndWait { projectRule.fixture.openFileInEditor(model2.virtualFile) }
    assertFalse(filter.invoke(visualLintIssue1))
    assertTrue(filter.invoke(visualLintIssue2))
  }
}

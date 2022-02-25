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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import junit.framework.Assert.assertEquals
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
    val provider = DesignToolsIssueProvider(project)

    val listener = mock(Runnable::class.java)
    provider.registerUpdateListener(listener)

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(emptyList(), emptyList())
    verify(listener).run()

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(emptyList(), emptyList())
    verify(listener, times(2)).run()
  }

  @Test
  fun testProviderIssue() {
    val project = projectRule.project
    val provider = DesignToolsIssueProvider(project)

    assertEmpty(provider.getFilteredIssues())

    val issueA = TestIssue(summary = "IssueA")
    val issueB = TestIssue(summary = "IssueB")
    val issueC = TestIssue(summary = "IssueC")
    val issueD = TestIssue(summary = "IssueD")

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(emptyList(), listOf(issueA, issueB))
    assertEquals(listOf(issueA, issueB), provider.getFilteredIssues())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(emptyList(), listOf(issueC))
    assertEquals(listOf(issueA, issueB, issueC), provider.getFilteredIssues())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(listOf(issueA), emptyList())
    assertEquals(listOf(issueB, issueC), provider.getFilteredIssues())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(listOf(issueC), listOf(issueD))
    assertEquals(listOf(issueB, issueD), provider.getFilteredIssues())

    project.messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(emptyList(), emptyList())
    assertEquals(listOf(issueB, issueD), provider.getFilteredIssues())
  }
}

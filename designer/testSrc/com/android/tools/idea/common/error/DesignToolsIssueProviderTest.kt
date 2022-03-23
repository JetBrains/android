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
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignToolsIssueProviderTest {
  @JvmField
  @Rule
  val rule = EdtAndroidProjectRule(AndroidProjectRule.inMemory())

  @Test
  fun testProvideIssues() {
    val messageBus = rule.project.messageBus

    val provider = DesignToolsIssueProvider(rule.project)
    assertTrue(provider.getFilteredIssues().isEmpty())

    val source1 = Any()
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source1, listOf(TestIssue()))
    assertEquals(1, provider.getFilteredIssues().size)

    val source2 = Any()
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source2, listOf(TestIssue(), TestIssue()))
    assertEquals(3, provider.getFilteredIssues().size)

    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source1, emptyList())
    assertEquals(2, provider.getFilteredIssues().size)

    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source2, emptyList())
    assertTrue(provider.getFilteredIssues().isEmpty())
  }

  @Test
  fun testIssueFilter() {
    val messageBus = rule.project.messageBus

    val provider = DesignToolsIssueProvider(rule.project)
    assertTrue(provider.getFilteredIssues().isEmpty())

    provider.filter = { issue -> issue.summary.contains("keyword") }

    val issue1 = TestIssue(summary = "I have keyword")
    val issue2 = TestIssue(summary = "I have something")
    val issue3 = TestIssue(summary = "I have some keywords")
    val issueList = listOf(issue1, issue2, issue3)
    val source = Any()
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, issueList)
    assertEquals(2, provider.getFilteredIssues().size)

    provider.filter = { issue -> issue.summary.contains("have") }
    assertEquals(3, provider.getFilteredIssues().size)

    provider.filter = { issue -> issue.summary.contains("something") }
    assertEquals(1, provider.getFilteredIssues().size)

    val anotherSource = Any()
    val anotherIssueList = listOf(TestIssue("a keyword"), TestIssue("something"))
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(anotherSource, anotherIssueList)
    assertEquals(2, provider.getFilteredIssues().size)

    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, emptyList())
    assertEquals(1, provider.getFilteredIssues().size)
  }

  @Test
  fun testFileClosed() {
    val messageBus = rule.project.messageBus
    val provider = DesignToolsIssueProvider(rule.project)

    runInEdt {
      val file = rule.fixture.addFileToProject("/res/layout/layout.xml", "")
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(Any(), listOf(TestIssue()))
      assertEquals(1, provider.getFilteredIssues().size)

      FileEditorManager.getInstance(rule.project).closeFile(file.virtualFile)

      assertEquals(0, provider.getFilteredIssues().size)
    }
  }
}

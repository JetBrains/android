/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.validator.ValidatorData
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import org.junit.Test
import org.mockito.Mockito


class AccessibilityLintIntegratorTest : LayoutTestCase() {

  @Test
  fun testCreateIssue() {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    assertTrue(integrator.issues.isEmpty())

    integrator.createIssue(createTestIssue(), null)

    assertEquals(1, integrator.issues.size)
  }

  @Test
  fun testIssueProvider() {
    // Precondition : create issues.
    val numberOfIssues = 3
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    assertTrue(integrator.issues.isEmpty())
    for (i in 0 until numberOfIssues) {
      val issue = ScannerTestHelper.createTestIssueBuilder().setMsg(i.toString()).build()
      integrator.createIssue(issue, null)
    }
    assertEquals(numberOfIssues, integrator.issues.size)

    // Test ensure issues are added correctly.
    val issueListBuilder: ImmutableCollection.Builder<Issue> = ImmutableList.builder()
    integrator.issueProvider.collectIssues(issueListBuilder)
    assertEquals(numberOfIssues, issueListBuilder.build().size)
  }

  @Test
  fun testDisableAccessibilityLint() {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    integrator.createIssue(createTestIssue(), null)
    assertEquals(1, integrator.issues.size)

    integrator.disableAccessibilityLint()
    assertTrue(integrator.issues.isEmpty())
  }

  @Test
  fun testPopulateLints() {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    integrator.createIssue(createTestIssue(), null)
    assertEquals(1, integrator.issues.size)

    integrator.populateLints()
    Mockito.verify(issueModel, Mockito.times(1))
      .addIssueProvider(Mockito.any(IssueProvider::class.java))
  }

  private fun createTestIssue(): ValidatorData.Issue {
    return ScannerTestHelper.createTestIssueBuilder().build()
  }
}
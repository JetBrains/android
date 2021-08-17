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
import com.android.tools.idea.validator.ValidatorData
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class AccessibilityLintIntegratorTest {

  @Mock
  lateinit var mockSurface: NlDesignSurface

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)
  }

  @Test
  fun createIssue() {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    assertTrue(integrator.issues.isEmpty())

    integrator.createIssue(createTestIssue(), ScannerTestHelper().buildNlComponent())

    assertEquals(1, integrator.issues.size)
  }

  @Test
  fun issueProvider() {
    // Precondition : create issues.
    val numberOfIssues = 3
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    assertTrue(integrator.issues.isEmpty())
    for (i in 0 until numberOfIssues) {
      val issue = ScannerTestHelper.createTestIssueBuilder().setMsg(i.toString()).build()
      integrator.createIssue(issue, ScannerTestHelper().buildNlComponent())
    }
    assertEquals(numberOfIssues, integrator.issues.size)

    // Test ensure issues are added correctly.
    val issueListBuilder: ImmutableCollection.Builder<Issue> = ImmutableList.builder()
    integrator.issueProvider.collectIssues(issueListBuilder)
    assertEquals(numberOfIssues, issueListBuilder.build().size)
  }

  @Test
  fun disableAccessibilityLint() {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    integrator.createIssue(createTestIssue(), ScannerTestHelper().buildNlComponent())
    assertEquals(1, integrator.issues.size)

    integrator.clear()
    assertTrue(integrator.issues.isEmpty())
  }

  @Test
  fun populateLints() {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)
    integrator.createIssue(createTestIssue(), ScannerTestHelper().buildNlComponent())
    assertEquals(1, integrator.issues.size)

    integrator.populateLints()
    Mockito.verify(issueModel, Mockito.times(1))
      .addIssueProvider(Mockito.any(IssueProvider::class.java))
  }

  @Test
  fun handleInclude() {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val integrator = AccessibilityLintIntegrator(issueModel)

    assertTrue(integrator.issues.isEmpty())

    val parser = NlScannerLayoutParser()
    val root = NlScannerLayoutParserTest.createComponentWithInclude()
    parser.buildViewToComponentMap(root)

    integrator.handleInclude(parser, mockSurface)

    assertEquals(1, integrator.issues.size)
  }

  private fun createTestIssue(): ValidatorData.Issue {
    return ScannerTestHelper.createTestIssueBuilder().build()
  }
}
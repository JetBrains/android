/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.google.common.collect.ImmutableList
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

import java.util.Comparator

import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.ERROR
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING
import com.android.tools.idea.gradle.structure.model.PsIssueType.LIBRARY_UPDATES_AVAILABLE
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations.initMocks

/**
 * Tests for [PsIssueCollection].
 */
class PsIssueCollectionTest {
  private lateinit var issueCollection: PsIssueCollection
  private lateinit var testPath: PsPath
  @Mock
  private val context: PsContext? = null

  @Before
  fun setUp() {
    initMocks(this)
    issueCollection = PsIssueCollection(context!!)
    testPath = TestPath("test path")
  }

  @Test
  fun getTooltipText_empty() {
    assertNull(PsIssueCollection.getTooltipText(ImmutableList.of(), true))
  }

  @Test
  fun getTooltipText_singleIssueWithPath() {
    issueCollection.add(PsGeneralIssue("Issue 01", testPath, PROJECT_ANALYSIS, WARNING))
    val issues = issueCollection.values
    val expected = "<html><body>" +
                   "test path: Issue 01<br>" +
                   "</body></html>"
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, true))
  }

  @Test
  fun getTooltipText_singleIssueWithoutPath() {
    issueCollection.add(PsGeneralIssue("Issue 01", testPath, PROJECT_ANALYSIS, WARNING))
    val issues = issueCollection.values
    val expected = "<html><body>" +
                   "Issue 01<br>" +
                   "</body></html>"
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, false))
  }

  @Test
  fun getTooltipText_multipleIssuesWithPath() {
    for (i in 1..3) {
      issueCollection.add(PsGeneralIssue(String.format("Empty Issue %02d", i), TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
      issueCollection.add(PsGeneralIssue(String.format("Test Issue %02d", i), testPath, PROJECT_ANALYSIS, WARNING))
    }
    val issues = issueCollection.values
    val expected = "<html><body><ul>" +
                   "<li>Empty Issue 01</li>" +
                   "<li>Empty Issue 02</li>" +
                   "<li>Empty Issue 03</li>" +
                   "<li>test path: Test Issue 01</li>" +
                   "<li>test path: Test Issue 02</li>" +
                   "<li>test path: Test Issue 03</li>" +
                   "</ul></body></html>"
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, true))
  }

  @Test
  fun getTooltipText_multipleIssuesWithoutPath() {
    for (i in 1..3) {
      issueCollection.add(PsGeneralIssue(String.format("Empty Issue %02d", i), TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
      issueCollection.add(PsGeneralIssue(String.format("Test Issue %02d", i), testPath, PROJECT_ANALYSIS, WARNING))
    }
    val issues = issueCollection.values
    val expected = "<html><body><ul>" +
                   "<li>Empty Issue 01</li>" +
                   "<li>Empty Issue 02</li>" +
                   "<li>Empty Issue 03</li>" +
                   "<li>Test Issue 01</li>" +
                   "<li>Test Issue 02</li>" +
                   "<li>Test Issue 03</li>" +
                   "</ul></body></html>"
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, false))
  }

  @Test
  fun getTooltipText_manyIssuesWithPath() {
    for (i in 1..8) {
      issueCollection.add(PsGeneralIssue(String.format("Empty Issue %02d", i), TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
      issueCollection.add(PsGeneralIssue(String.format("Test Issue %02d", i), testPath, PROJECT_ANALYSIS, WARNING))
    }
    val issues = issueCollection.values
    val expected = "<html><body><ul>" +
                   "<li>Empty Issue 01</li>" +
                   "<li>Empty Issue 02</li>" +
                   "<li>Empty Issue 03</li>" +
                   "<li>Empty Issue 04</li>" +
                   "<li>Empty Issue 05</li>" +
                   "<li>Empty Issue 06</li>" +
                   "<li>Empty Issue 07</li>" +
                   "<li>Empty Issue 08</li>" +
                   "<li>test path: Test Issue 01</li>" +
                   "<li>test path: Test Issue 02</li>" +
                   "<li>test path: Test Issue 03</li>" +
                   "</ul>5 more messages...<br></body></html>"
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, true))
  }

  @Test
  fun getTooltipText_manyIssuesWithoutPath() {
    for (i in 1..8) {
      issueCollection.add(PsGeneralIssue(String.format("Empty Issue %02d", i), TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
      issueCollection.add(PsGeneralIssue(String.format("Test Issue %02d", i), testPath, PROJECT_ANALYSIS, WARNING))
    }
    val issues = issueCollection.values
    val expected = "<html><body><ul>" +
                   "<li>Empty Issue 01</li>" +
                   "<li>Empty Issue 02</li>" +
                   "<li>Empty Issue 03</li>" +
                   "<li>Empty Issue 04</li>" +
                   "<li>Empty Issue 05</li>" +
                   "<li>Empty Issue 06</li>" +
                   "<li>Empty Issue 07</li>" +
                   "<li>Empty Issue 08</li>" +
                   "<li>Test Issue 01</li>" +
                   "<li>Test Issue 02</li>" +
                   "<li>Test Issue 03</li>" +
                   "</ul>5 more messages...<br></body></html>"
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, false))
  }

  @Test
  fun findIssues_withModuleModel() {
    val mockModule = mock(PsModule::class.java)
    val issueA = PsGeneralIssue("a", PsModulePath(mockModule), PROJECT_ANALYSIS, WARNING)
    issueCollection.add(issueA)
    issueCollection.add(PsGeneralIssue("b", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
    val issues = issueCollection.findIssues(mockModule, null)
    assertThat(issues).containsExactly(issueA)
  }

  @Test
  fun findIssues_withUnknownType() {
    issueCollection.add(PsGeneralIssue("a", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
    issueCollection.add(PsGeneralIssue("b", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
    val issues = issueCollection.findIssues(object : PsChildModel() {
      override val parent: PsModel?
        get() = null

      override val name: String
        get() = "test"

      override val isDeclared: Boolean
        get() = false
    }, Comparator.comparing<PsIssue, String> { it.text })
    assertThat(issues).isEmpty()
  }

  @Test
  fun findIssues_withComparator() {
    val issueA = PsGeneralIssue("a", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    val issueB = PsGeneralIssue("b", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    val issueC = PsGeneralIssue("c", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    val issueD = PsGeneralIssue("d", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)

    issueCollection.add(issueB)
    issueCollection.add(issueD)
    issueCollection.add(issueC)
    issueCollection.add(issueA)
    val issues = issueCollection.findIssues(TestPath.EMPTY_PATH,
                                              Comparator.comparing<PsIssue, String> { it.text })
    assertThat(issues).containsExactly(issueA, issueB, issueC, issueD).inOrder()
  }

  @Test
  fun findIssues_nomatch() {
    issueCollection.add(PsGeneralIssue("", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
    assertThat(issueCollection.findIssues(testPath, null)).isEmpty()
  }

  @Test
  fun add() {
    val testIssue = PsGeneralIssue("", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    issueCollection.add(testIssue)
    assertThat(issueCollection.values).containsExactly(testIssue)
  }

  @Test
  fun add_withParentPath() {
    val testIssue = PsGeneralIssue("", TestPath("a", TestPath.EMPTY_PATH), PROJECT_ANALYSIS, WARNING)
    issueCollection.add(testIssue)
    assertThat(issueCollection.values).containsExactly(testIssue, testIssue)
  }

  @Test
  fun remove() {
    val issueA = PsGeneralIssue("a", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    val issueB = PsGeneralIssue("b", TestPath.EMPTY_PATH, LIBRARY_UPDATES_AVAILABLE, WARNING)
    issueCollection.add(issueA)
    issueCollection.add(issueB)
    assertThat(issueCollection.values).containsExactly(issueA, issueB)
    issueCollection.remove(PROJECT_ANALYSIS)
    assertThat(issueCollection.values).containsExactly(issueB)
  }

  @Test
  fun isEmpty() {
    assertThat(issueCollection.values).isEmpty()
    assertTrue(issueCollection.isEmpty)
    issueCollection.add(PsGeneralIssue("", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING))
    assertThat(issueCollection.values).isNotEmpty()
    assertFalse(issueCollection.isEmpty)
  }

  @Test
  fun getValues() {
    val issueA = PsGeneralIssue("a", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    val issueB = PsGeneralIssue("b", testPath, LIBRARY_UPDATES_AVAILABLE, WARNING)
    issueCollection.add(issueA)
    issueCollection.add(issueB)
    assertThat(issueCollection.values).containsExactly(issueA, issueB)
  }

  @Test
  fun getValues_byType() {
    val issueA = PsGeneralIssue("a", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    val issueB = PsGeneralIssue("b", testPath, LIBRARY_UPDATES_AVAILABLE, ERROR)
    val issueC = PsGeneralIssue("c", testPath, LIBRARY_UPDATES_AVAILABLE, ERROR)
    issueCollection.add(issueA)
    issueCollection.add(issueB)
    issueCollection.add(issueC)
    assertThat(issueCollection.getValues(TestPath.EMPTY_PATH.javaClass)).containsExactly(issueA)
    assertThat(issueCollection.getValues(testPath.javaClass)).containsExactly(issueB, issueC)
  }

  @Test
  fun getValues_byParentType() {
    val issueA = PsGeneralIssue("a", TestPath.EMPTY_PATH, PROJECT_ANALYSIS, WARNING)
    val issueB = PsGeneralIssue("b", testPath, LIBRARY_UPDATES_AVAILABLE, ERROR)
    val issueC = PsGeneralIssue("c", TestPath("something", testPath), LIBRARY_UPDATES_AVAILABLE, ERROR)
    issueCollection.add(issueA)
    issueCollection.add(issueB)
    issueCollection.add(issueC)
    assertThat(issueCollection.getValues(TestPath.EMPTY_PATH.javaClass)).containsExactly(issueA)
    assertThat(issueCollection.getValues(testPath.javaClass)).containsExactly(issueB, issueC)
  }
}
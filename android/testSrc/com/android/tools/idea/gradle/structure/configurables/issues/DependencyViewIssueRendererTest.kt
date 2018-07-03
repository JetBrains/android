/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.issues

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.*
import org.hamcrest.CoreMatchers
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks

class DependencyViewIssueRendererTest {
  @Mock
  private lateinit var context: PsContext
  private lateinit var testIssuePath: PsPath
  private lateinit var testIssue: PsGeneralIssue
  private lateinit var quickFix: PsQuickFix

  @Before
  fun setUp() {
    initMocks(this)
    testIssuePath = createPath("/PATH")
    quickFix = createFix("/QUICK_FIX")
    testIssue = PsGeneralIssue("TEXT", "DESCRIPTION", testIssuePath, PsIssueType.PROJECT_ANALYSIS, PsIssue.Severity.ERROR, null)
  }

  private fun createPath(text: String): PsPath = object : PsPath {
    override fun toText(type: PsPath.TexType): String = when (type) {
      PsPath.TexType.FOR_COMPARE_TO -> "FOR_COMPARE_$text"
      PsPath.TexType.PLAIN_TEXT -> "PLAIN_TEXT_$text"
      else -> throw AssertionError()
    }

    override fun getHyperlinkDestination(context: PsContext): String? = "@$text"

    override fun getHtml(context: PsContext): String = "<$text>"

    override fun getParents(): List<PsPath> = listOf()
  }

  private fun createFix(text: String): PsQuickFix = object : PsQuickFix {
    override fun getHyperlinkDestination(context: PsContext): String? = throw UnsupportedOperationException()
    override fun getHtml(context: PsContext): String = "<$text>"
  }

  @Test
  fun testRenderIssue() {
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, testIssuePath), CoreMatchers.`is`("TEXT"))
  }

  @Test
  fun testRenderIssue_quickFix() {
    testIssue = testIssue.copy(quickFix = quickFix)
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, testIssuePath), CoreMatchers.`is`("TEXT </QUICK_FIX>"))
  }

  @Test
  fun testRenderIssue_renderPath() {
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, null), CoreMatchers.`is`("<a href=\"@/PATH\">PLAIN_TEXT_/PATH</a>: TEXT"))
  }

  @Test
  fun testRenderIssue_renderPathAndQuickFix() {
    testIssue = testIssue.copy(quickFix = quickFix)
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, null), CoreMatchers.`is`("<a href=\"@/PATH\">PLAIN_TEXT_/PATH</a>: TEXT </QUICK_FIX>"))
  }

  private fun renderIssue(renderer: IssueRenderer, scope: PsPath?): String {
    val sb = StringBuilder()
    renderer.renderIssue(sb, testIssue, scope)
    return sb.toString()
  }

  @Test
  fun testRenderIssue_renderDescription() {
    val renderer = DependencyViewIssueRenderer(context, true)
    assertThat(renderIssue(renderer, testIssuePath), CoreMatchers.`is`("TEXT<br/><br/>DESCRIPTION"))
  }

  @Test
  fun testRenderIssue_renderDescriptionAndQuickFix() {
    testIssue = testIssue.copy(quickFix = quickFix)
    val renderer = DependencyViewIssueRenderer(context, true)
    assertThat(renderIssue(renderer, testIssuePath), CoreMatchers.`is`("TEXT </QUICK_FIX><br/><br/>DESCRIPTION"))
  }
}
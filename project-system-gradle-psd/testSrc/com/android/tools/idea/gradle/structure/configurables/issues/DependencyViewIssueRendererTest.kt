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
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssueType
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import org.hamcrest.CoreMatchers.equalTo
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
  private lateinit var quickFix2: PsQuickFix

  @Before
  fun setUp() {
    initMocks(this)
    testIssuePath = createPath("/PATH")
    quickFix = createFix("QUICK_FIX")
    quickFix2 = createFix("QUICK_FIX2")
    testIssue = PsGeneralIssue("TEXT", "DESCRIPTION", testIssuePath, PsIssueType.PROJECT_ANALYSIS, PsIssue.Severity.ERROR, emptyList())
  }

  private fun createPath(text: String): PsPath = object : PsPath {
    override fun getHyperlinkDestination(context: PsContext): String? = "@$text"
    override fun toString(): String = text
  }

  private fun createFix(text: String): PsQuickFix = object : PsQuickFix {
    override val text = text
    override fun execute(context: PsContext): Unit = TODO("not implemented")
  }

  @Test
  fun testRenderIssue() {
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, testIssuePath), equalTo("TEXT"))
  }

  @Test
  fun testRenderIssue_quickFix() {
    testIssue = testIssue.copy(quickFixes = listOf(quickFix))
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, testIssuePath), equalTo("TEXT <a href='go:QUICK_FIX'>[QUICK_FIX]</a>"))
  }

  @Test
  fun testRenderIssue_renderPath() {
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, null), equalTo("""<a href="@/PATH">/PATH</a>: TEXT"""))
  }

  @Test
  fun testRenderIssue_renderPathAndQuickFix() {
    testIssue = testIssue.copy(quickFixes = listOfNotNull(quickFix))
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, null),
               equalTo("""<a href="@/PATH">/PATH</a>: TEXT <a href='go:QUICK_FIX'>[QUICK_FIX]</a>"""))
  }

  @Test
  fun testRenderIssue_renderPathAndMultipleQuickFixes() {
    testIssue = testIssue.copy(quickFixes = listOfNotNull(quickFix, quickFix2))
    val renderer = DependencyViewIssueRenderer(context, false)
    assertThat(renderIssue(renderer, null), equalTo(
      """<a href="@/PATH">/PATH</a>: TEXT <a href='go:QUICK_FIX'>[QUICK_FIX]</a> <a href='go:QUICK_FIX2'>[QUICK_FIX2]</a>"""))
  }

  private fun renderIssue(renderer: IssueRenderer, scope: PsPath?): String {
    val sb = StringBuilder()
    renderer.renderIssue(sb, testIssue, scope)
    val text = sb.toString()
    val regex = Regex("psdFix://[0123456789abcdef]+")
    return regex.replace(text) { match ->
      match.value.substring("psdFix://".length).let { "go:${PsQuickFix.deserialize(it).text}" }
    }
  }

  @Test
  fun testRenderIssue_renderDescription() {
    val renderer = DependencyViewIssueRenderer(context, true)
    assertThat(renderIssue(renderer, testIssuePath), equalTo("TEXT<br/><br/>DESCRIPTION"))
  }

  @Test
  fun testRenderIssue_renderDescriptionAndQuickFix() {
    testIssue = testIssue.copy(quickFixes = listOfNotNull(quickFix))
    val renderer = DependencyViewIssueRenderer(context, true)
    assertThat(renderIssue(renderer, testIssuePath), equalTo("""TEXT <a href='go:QUICK_FIX'>[QUICK_FIX]</a><br/><br/>DESCRIPTION"""))
  }
}
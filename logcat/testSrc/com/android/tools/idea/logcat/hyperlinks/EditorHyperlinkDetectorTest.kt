/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.UrlFilter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/** Tests for [editorHyperlinkDetector] */
@RunsInEdt
class EditorHyperlinkDetectorTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      logcatEditorRule,
      ApplicationServiceRule(StudioBot::class.java, TestStudioBot),
      disposableRule,
      EdtRule(),
    )

  private val project
    get() = projectRule.project

  private val editor
    get() = logcatEditorRule.editor

  /**
   * Tests that we are using the correct filter as provided by
   * ConsoleViewUtil.computeConsoleFilters(). This is a CompositeFilter that wraps a set of filters
   * provided by the IDEA.
   */
  @Test
  fun usesCorrectFilters_withoutStudioBot() {
    TestStudioBot.available = false
    val expectedFilters =
      ConsoleViewUtil.computeConsoleFilters(
        project,
        /* consoleView= */ null,
        GlobalSearchScope.allScope(project),
      )

    val hyperlinkDetector = editorHyperlinkDetector(editor)

    val expected = expectedFilters.map { it::class } + SimpleFileLinkFilter::class
    waitForCondition {
      hyperlinkDetector.filter.compositeFilter.filters.map { it::class }.containsAll(expected)
    }
  }

  /**
   * Tests that we are using the correct filter as provided by
   * ConsoleViewUtil.computeConsoleFilters(). This is a CompositeFilter that wraps a set of filters
   * provided by the IDEA.
   */
  @Test
  fun usesCorrectFilters_withStudioBot() {
    TestStudioBot.available = true
    val expectedFilters =
      ConsoleViewUtil.computeConsoleFilters(
        project,
        /* consoleView= */ null,
        GlobalSearchScope.allScope(project),
      )

    val hyperlinkDetector = editorHyperlinkDetector(editor)

    val expected =
      expectedFilters.map { it::class } + SimpleFileLinkFilter::class + StudioBotFilter::class
    waitForCondition {
      hyperlinkDetector.filter.compositeFilter.filters.map { it::class }.containsAll(expected)
    }
  }

  /**
   * Tests that we actually detect a hyperlink and add to the editor.
   *
   * The easiest hyperlink type to test is a URL which is one of the filters injected by the IDEA.
   */
  @Test
  fun detectHyperlinks() {
    editor.document.setText("http://www.google.com")
    val hyperlinkSupport = EditorHyperlinkSupport.get(editor)

    val detector = editorHyperlinkDetector(editor)
    waitForCondition { detector.filter.compositeFilter.filters.any { it is UrlFilter } }
    detector.detectHyperlinks(0, editor.document.lineCount - 1, sdk = null)

    hyperlinkSupport.waitForPendingFilters(/* timeoutMs= */ 5000)
    assertThat(
        hyperlinkSupport.findAllHyperlinksOnLine(0).map {
          editor.document.text.substring(it.startOffset, it.endOffset)
        }
      )
      .containsExactly("http://www.google.com")
  }

  @Test
  fun detectHyperlinks_usesAllFilters() {
    editor.document.setText("Foo Bar")
    val hyperlinkSupport = EditorHyperlinkSupport.get(editor)
    val editorHyperlinkDetector = editorHyperlinkDetector(editor)
    val filters = (editorHyperlinkDetector.filter.compositeFilter)
    filters.addFilter(TestFilter("Foo"))
    filters.addFilter(TestFilter("Bar"))

    editorHyperlinkDetector.detectHyperlinks(0, editor.document.lineCount - 1, sdk = null)

    hyperlinkSupport.waitForPendingFilters(/* timeoutMs= */ 5000)
    assertThat(
        hyperlinkSupport.findAllHyperlinksOnLine(0).map {
          editor.document.text.substring(it.startOffset, it.endOffset)
        }
      )
      .containsExactly("Foo", "Bar")
  }

  @Test
  fun detectHyperlinks_passesSdk() {
    val editorHyperlinkDetector = editorHyperlinkDetector(editor)

    editorHyperlinkDetector.detectHyperlinks(0, editor.document.lineCount - 1, sdk = 23)

    assertThat(editorHyperlinkDetector.filter.apiLevel).isEqualTo(23)
  }

  private class TestFilter(private val text: String) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Result? {
      val start = line.indexOf(text)
      if (start < 0) {
        return null
      }

      return Result(start, start + text.length, Info())
    }

    private class Info : HyperlinkInfo {
      override fun navigate(project: Project) {}
    }
  }

  private object TestStudioBot : StudioBot.StubStudioBot() {
    var available = true

    override fun isAvailable(): Boolean = available

    override fun isContextAllowed(project: Project): Boolean = true
  }

  private fun editorHyperlinkDetector(editor: EditorEx) =
    EditorHyperlinkDetector(
      project,
      editor,
      disposableRule.disposable,
      ModalityState.any(),
      MoreExecutors.newDirectExecutorService(),
    )
}

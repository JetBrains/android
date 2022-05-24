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

import com.android.tools.idea.logcat.util.createLogcatEditor
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.editor.EditorFactory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [EditorHyperlinkDetector]
 */
@RunsInEdt
class EditorHyperlinkDetectorTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val editor by lazy { createLogcatEditor(projectRule.project) }

  @After
  fun tearDown() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  /**
   * Tests that we are using the correct filter as provided by ConsoleViewUtil.computeConsoleFilters(). This is a CompositeFilter that
   * wraps a set of filters provided by the IDEA.
   */
  @Test
  fun usesCorrectFilters() {
    val expectedFilters =
      ConsoleViewUtil.computeConsoleFilters(projectRule.project, /* consoleView= */ null, GlobalSearchScope.allScope(projectRule.project))

    val hyperlinkDetector = EditorHyperlinkDetector(projectRule.project, editor)

    assertThat(hyperlinkDetector.hyperlinkFilters.filters.map { it::class }).containsExactlyElementsIn(expectedFilters.map { it::class })
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

    EditorHyperlinkDetector(projectRule.project, editor).detectHyperlinks(0, editor.document.lineCount - 1)

    hyperlinkSupport.waitForPendingFilters(/* timeoutMs= */ 5000)
    assertThat(hyperlinkSupport.findAllHyperlinksOnLine(0).map {
      editor.document.text.substring(it.startOffset, it.endOffset)
    }).containsExactly("http://www.google.com")
  }
}

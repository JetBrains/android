/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.adtui.validation.ErrorDetailDialog
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.testing.WaitForIndexRule
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import javax.swing.JTextArea
import kotlin.test.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DeobfuscatedFilterTest {
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val editor by lazy {
    runInEdtAndGet { createLogcatEditor(project, projectRule.disposable) }
  }

  @get:Rule
  val rule = RuleChain(projectRule, WaitForIndexRule(projectRule), EdtRule(), HeadlessDialogRule())

  @Before fun setUp() {}

  @Test
  fun applyFilter_noMatch() {
    val filter = DeobfuscatedFilter(editor)
    val line = "no link added"

    val result = filter.applyFilter(line, line.length)

    assertThat(result).isNull()
  }

  @Test
  fun applyFilter_hasMatch() {
    val filter = DeobfuscatedFilter(editor)
    val line = "Exception (Show original)"

    val result = filter.applyFilter(line, line.length)

    assertThat(result?.resultItems).hasSize(1)
  }

  @Test
  fun applyFilter_opensPopup() {
    val filter = DeobfuscatedFilter(editor)
    val line = "Exception (Show original)"
    val document = editor.document
    document.setText(line)
    document.createRangeMarker(0, document.textLength).apply {
      putUserData(LOGCAT_MESSAGE_KEY, logcatMessage(message = "Original"))
    }

    val result = filter.applyFilter(line, line.length) ?: fail("Expected to me not null")
    val hyperlinkInfo = result.resultItems.first().hyperlinkInfo ?: fail("Expected to me not null")
    hyperlinkInfo.navigate(project)

    val dialog = waitForDialog()
    assertThat(dialog.title).isEqualTo("Original Stack Trace")
    val textArea = TreeWalker(dialog.rootPane).descendants().filterIsInstance<JTextArea>().first()
    assertThat(textArea.text).isEqualTo("Original")
  }
}

private fun waitForDialog(): DialogWrapper {
  waitForCondition { findDialog() != null }
  return findDialog()!!
}

private fun findDialog() = findModelessDialog<ErrorDetailDialog> { it.isShowing }

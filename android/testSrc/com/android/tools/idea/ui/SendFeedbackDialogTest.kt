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
package com.android.tools.idea.ui

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.google.common.truth.Truth
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.nio.file.Paths

private val PATH = Paths.get("DiagnosticsFile20220616-040000.zip")

@RunsInEdt
class SendFeedbackDialogTest {
  private val projectRule = ProjectRule()
  private val dialog by lazy { SendFeedbackDialog(projectRule.project, PATH) }

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
  }

  @Test
  fun testFields() {
    createModalDialogAndInteractWithIt(dialog::show) {
      val titleText = findByName<JBTextField>("titleText")
      Truth.assertThat(titleText).isNotNull()
      titleText?.text = "This is the bug title."

      val reproText = findByName<JBTextArea>("reproText")
      Truth.assertThat(reproText).isNotNull()
      reproText?.text = "These are the repro steps."

      val expectedText = findByName<JBTextArea>("expectedText")
      Truth.assertThat(expectedText).isNotNull()
      expectedText?.text = "This is the expected behavior."

      val actualText = findByName<JBTextArea>("actualText")
      Truth.assertThat(actualText).isNotNull()
      actualText?.text = "This is the actual behavior."

      dialog.apply {
        Truth.assertThat(issueTitle).isEqualTo("This is the bug title.")
        Truth.assertThat(reproSteps).isEqualTo("These are the repro steps.")
        Truth.assertThat(expected).isEqualTo("This is the expected behavior.")
        Truth.assertThat(actual).isEqualTo("This is the actual behavior.")
        Truth.assertThat(paths.size).isEqualTo(1)
        Truth.assertThat(paths[0]).isEqualTo(Paths.get("DiagnosticsFile20220616-040000.zip"))
      }
    }
  }

  private inline fun <reified R> findByName(name: String): R? {
    return TreeWalker(dialog.rootPane).descendants().filterIsInstance<R>()
      .firstOrNull { (it as? Component)?.name == name }
  }
}
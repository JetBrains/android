/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals.ui

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.insights.IssueAnnotation
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JPanel
import javax.swing.JTextPane
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test

private val TEST_ANNOTATION =
  IssueAnnotation(
    "Insight",
    "Native lock contention",
    """
            The main thread is blocked, waiting on a native synchronization routine, such as a mutex.

            Native synchronization routines don't provide details on the exact lock, or where it is being held. Find the locked mutex in your source, and then locate other code locations where it is being acquired. You can use Android Studio's profiler to detect potential lock contentions if multiple threads frequently compete for the same lock. (https://support.google.com/googleplay/android-developer/answer/9859174)
          """
      .trimIndent(),
  )

@RunsInEdt
class SdkInsightsPanelTest {

  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val edtRule = EdtRule()

  @Test
  fun `show correct information when expanded and shrunk`() {
    val panel =
      JPanel().apply {
        add(SdkInsightsPanel(TEST_ANNOTATION.category, TEST_ANNOTATION.title, TEST_ANNOTATION.body))
      }

    val fakeUi = FakeUi(panel)
    val titleLabel =
      fakeUi.findComponent<JBLabel> { it.icon == AllIcons.Actions.IntentionBulb }
        ?: fail("Title label not found")

    assertThat(titleLabel.isVisible).isTrue()
    assertThat(titleLabel.text).contains(TEST_ANNOTATION.category)
    assertThat(titleLabel.text).contains(TEST_ANNOTATION.title)

    val showLabel =
      fakeUi.findComponent<JBLabel> { it.text == "Show more" } ?: fail("Show more label not found")
    val textPane = fakeUi.findComponent<JTextPane>() ?: fail("Text pane not found")
    assertThat(textPane.isVisible).isTrue()
    var expectedShrunkText = truncateText(textPane, TEST_ANNOTATION.body, showLabel)
    assertThat(textPane.textWithoutHtmlTags).isEqualTo(expectedShrunkText)

    showLabel.mouseListeners.forEach { it.mouseClicked(null) }
    assertThat(showLabel.text).isEqualTo("Show less")
    assertThat(textPane.textWithoutHtmlTags).isEqualTo(TEST_ANNOTATION.body.replace("\n\n", " "))

    expectedShrunkText = truncateText(textPane, TEST_ANNOTATION.body, showLabel)
    showLabel.mouseListeners.forEach { it.mouseClicked(null) }
    assertThat(showLabel.text).isEqualTo("Show more")
    assertThat(textPane.textWithoutHtmlTags).isEqualTo(expectedShrunkText)
  }

  @Test
  fun `see more not visible if content fits in label without truncating`() {
    val panel =
      SdkInsightsPanel(
        TEST_ANNOTATION.category,
        TEST_ANNOTATION.title,
        TEST_ANNOTATION.body.take(20),
      )
    val fakeUi =
      FakeUi(
        BorderLayoutPanel().apply {
          setBounds(0, 0, 300, 300)
          addToCenter(panel)
        }
      )

    val textPane = fakeUi.findComponent<JTextPane>() ?: fail("Text pane not found")

    assertThat(textPane.isVisible).isTrue()
    assertThat(textPane.preferredSize.width).isLessThan(textPane.width)

    val showLabel = fakeUi.findComponent<JBLabel> { it.text == "Show more" }
    assertThat(showLabel).isNull()
  }

  private val JTextPane.textWithoutHtmlTags: String
    get() = document.getText(0, document.length).replace("\u200B", "").trim()
}

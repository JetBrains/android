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

import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.insights.IssueAnnotation
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
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

class SdkInsightsPanelTest {

  @Test
  fun `show correct information when expanded`() {
    val panel =
      SdkInsightsPanel(TEST_ANNOTATION.category, TEST_ANNOTATION.title, TEST_ANNOTATION.body)

    val fakeUi = FakeUi(panel)
    assertThat(fakeUi.findComponent<JBLabel> { it.text == "Insight" }).isNotNull()
    assertThat(fakeUi.findComponent<JBLabel> { it.text == "Native lock contention" }).isNotNull()
    assertThat(fakeUi.findComponent<JBLabel> { it.icon == AllIcons.Actions.IntentionBulb })
      .isNotNull()
    val truncatedLabel =
      fakeUi.findComponent<JBLabel> {
        it.text ==
          """
            The main thread is blocked, waiting on a native synchronization routine, such as a mutex.

            Native synchronization routines don't provide details on the exact lock, or where it is being held. Find the locked mutex in your source, and then locate other code locations where it is being acquired. You can use Android Studio's profiler to detect potential lock contentions if multiple threads frequently compete for the same lock. (https://support.google.com/googleplay/android-developer/answer/9859174)
            """
            .trimIndent()
      }
    assertThat(truncatedLabel?.isVisible).isTrue()

    val seeMoreLink = fakeUi.findComponent<HyperlinkLabel>()!!
    seeMoreLink.doClick()

    assertThat(
        fakeUi
          .findComponent<HtmlLabel> {
            it.text.contains(
              "<a href=\"https://support.google.com/googleplay/android-developer/answer/9859174\">" +
                "https://support.google.com/googleplay/android-developer/answer/9859174</a>"
            )
          }
          ?.isVisible
      )
      .isTrue()
    assertThat(truncatedLabel?.parent?.isVisible).isFalse()
  }
}

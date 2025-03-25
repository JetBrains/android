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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.application.writeAction
import com.intellij.util.textCompletion.TextFieldWithCompletion
import java.awt.Dimension
import javax.swing.JButton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MorphPanelTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testTagValidation() = runBlocking {
    @Suppress("UnstableApiUsage")
    val morphPanel = writeAction {
      MorphPanel(
        projectRule.module.androidFacet!!,
        projectRule.project,
        "InitialTag",
        listOf("SuggestionA", "SuggestionB"),
      )
    }
    withContext(uiThread) {
      val fakeUi =
        FakeUi(
            morphPanel,
            createFakeWindow = true,
            parentDisposable = projectRule.testRootDisposable,
          )
          .apply {
            root.size = Dimension(400, 400)
            layout()
            render()
          }

      val inputText = fakeUi.findComponent<TextFieldWithCompletion>()!!
      val button = fakeUi.findComponent<JButton>()!!
      assertEquals("First suggestion expected to be pre-populated", "SuggestionA", inputText.text)
      assertTrue(
        "Accept expected to be enabled for a valid suggestion 'SuggestionA'",
        button.isEnabled,
      )

      inputText.text = "Invalid suggestion_"
      assertFalse(
        "Accept expected to be disabled for an invalid suggestion 'Invalid suggestion_'",
        button.isEnabled,
      )

      inputText.text = "a.valid.Tag"
      assertTrue(
        "Accept expected to be enabled for a valid suggestion 'a.valid.Tag'",
        button.isEnabled,
      )
    }
  }
}

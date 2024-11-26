/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.jewel.ui.component.TextField
import org.junit.Rule
import org.junit.Test

class ComposeWizardTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun enter() {
    val wizard = TestComposeWizard {
      val focusRequester = remember { FocusRequester() }
      TextField(TextFieldState("abcd"), Modifier.focusRequester(focusRequester))
      LaunchedEffect(Unit) { focusRequester.requestFocus() }
      finishAction = WizardAction { close() }
    }

    composeTestRule.setContent { wizard.Content() }

    @OptIn(ExperimentalTestApi::class)
    composeTestRule.onNodeWithText("abcd").performKeyInput { keyPress(Key.Enter) }

    wizard.awaitClose(5.seconds)
  }
}

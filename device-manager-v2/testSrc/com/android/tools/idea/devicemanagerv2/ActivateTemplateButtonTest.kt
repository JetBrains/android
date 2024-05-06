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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.testing.TestMessagesDialog
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.ApplicationRule
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import javax.swing.SwingUtilities

class ActivateTemplateButtonTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun activationError() = runTest {
    val buttonScope = createChildScope()
    val template = FakeDeviceTemplate("A")
    val button = ActivateTemplateButton(buttonScope, template)
    val dialog = TestMessagesDialog(Messages.OK)
    TestDialogManager.setTestDialog(dialog)

    SwingUtilities.invokeAndWait { button.doClick() }
    advanceUntilIdle()

    SwingUtilities.invokeAndWait {
      assertThat(dialog.displayedMessage).isEqualTo("Device is unavailable")
    }
    buttonScope.cancel()
  }
}

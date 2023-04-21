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
package com.android.tools.idea.layoutinspector.runningdevices.actions

import com.android.tools.adtui.actions.createTestActionEvent
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class ToggleDeepInspectActionTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testActionClick() {
    var isSelected = false
    val toggleDeepInspectAction = ToggleDeepInspectAction({ isSelected }, { isSelected = !isSelected })

    toggleDeepInspectAction.actionPerformed(createTestActionEvent(toggleDeepInspectAction))
    assertThat(isSelected).isTrue()

    toggleDeepInspectAction.actionPerformed(createTestActionEvent(toggleDeepInspectAction))
    assertThat(isSelected).isFalse()
  }

  @Test
  fun testActionIsNotVisible() {
    val toggleDeepInspectAction = ToggleDeepInspectAction({ false }, { })

    val event = createTestActionEvent(toggleDeepInspectAction)
    toggleDeepInspectAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testTitleAndDescription() {
    val toggleDeepInspectAction = ToggleDeepInspectAction({ false }, { })

    val event = createTestActionEvent(toggleDeepInspectAction)
    toggleDeepInspectAction.update(event)
    assertThat(event.presentation.text).isEqualTo("Toggle Deep Inspect")
    assertThat(event.presentation.description).isEqualTo("Enter Deep Inspect to be able to select components by clicking on the device")
  }
}
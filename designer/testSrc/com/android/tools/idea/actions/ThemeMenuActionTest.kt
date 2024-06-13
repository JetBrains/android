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
package com.android.tools.idea.actions

import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeMenuActionTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @Test
  fun presentationStateIsCorrect() {
    val psiFile = projectRule.fixture.addFileToProject("res/layout/foo", "".trimIndent())
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, psiFile.virtualFile)
    val action = ThemeMenuAction()
    ConfigurationManager.getOrCreateInstance(projectRule.module)
      .getConfiguration(psiFile.virtualFile)

    val event =
      TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder()
          .add(CONFIGURATIONS, listOf(configuration))
          .add(PlatformDataKeys.PROJECT, projectRule.project)
          .build(),
      )
    action.update(event)
    assertTrue(event.presentation.isEnabled)
    assertTrue(event.presentation.isVisible)
    assertEquals("Theme", event.presentation.text)

    DumbModeTestUtils.runInDumbModeSynchronously(projectRule.project) {
      action.update(event)
      assertFalse("action should be disabled if not in smart mode", event.presentation.isEnabled)
      assertEquals("Theme", event.presentation.text)
      assertTrue(event.presentation.isVisible)
    }
  }
}

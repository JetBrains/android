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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShowInspectionTooltipsActionTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    StudioFlags.COMPOSE_VIEW_INSPECTOR.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_VIEW_INSPECTOR.clearOverride()
  }

  @Test
  fun testEnableAndDisableInspectionTooltips() {
    val manager = TestComposePreviewManager()
    val context = SimpleDataContext.getSimpleContext(COMPOSE_PREVIEW_MANAGER, manager)

    val action = ShowInspectionTooltipsAction()
    manager.isInspectionTooltipEnabled = false

    action.setSelected(TestActionEvent.createTestEvent(context), true)
    assertTrue(manager.isInspectionTooltipEnabled)

    action.setSelected(TestActionEvent.createTestEvent(context), false)
    assertFalse(manager.isInspectionTooltipEnabled)
  }
}

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
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.TestActionEvent
import junit.framework.Assert.assertTrue
import kotlin.test.assertFalse
import org.junit.After
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
    val context = DataContext { if (COMPOSE_PREVIEW_MANAGER.`is`(it)) manager else null }

    val action = ShowInspectionTooltipsAction(context)
    manager.isInspectionTooltipEnabled = false

    action.setSelected(TestActionEvent(Presentation()), true)
    assertTrue(manager.isInspectionTooltipEnabled)

    action.setSelected(TestActionEvent(Presentation()), false)
    assertFalse(manager.isInspectionTooltipEnabled)
  }
}

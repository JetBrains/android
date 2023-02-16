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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.layout.EmptySurfaceLayoutManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.actions.SwitchSurfaceLayoutManagerAction
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SwitchSurfaceLayoutManagerActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory()

  @Suppress("UnstableApiUsage")
  @Test
  fun testCanChangeMultiChoiceMode() {

    val switcher =
      object : LayoutManagerSwitcher {
        override fun isLayoutManagerSelected(layoutManager: SurfaceLayoutManager): Boolean = true

        override fun setLayoutManager(
          layoutManager: SurfaceLayoutManager,
          sceneViewAlignment: DesignSurface.SceneViewAlignment
        ) = Unit
      }
    val option = listOf(SurfaceLayoutManagerOption("Layout A", EmptySurfaceLayoutManager()))

    var enabled = true
    val nonMultiChoiceAction = SwitchSurfaceLayoutManagerAction(switcher, option) { enabled }
    val presentation = Presentation()

    // It should always not be multi-choice no matter it is enabled or not.
    nonMultiChoiceAction.update(TestActionEvent(presentation))
    assertFalse(Utils.isMultiChoiceGroup(nonMultiChoiceAction))
    enabled = false
    nonMultiChoiceAction.update(TestActionEvent(presentation))
    assertFalse(Utils.isMultiChoiceGroup(nonMultiChoiceAction))

    enabled = true
    val multiChoiceAction =
      SwitchSurfaceLayoutManagerAction(switcher, option) { enabled }
        .apply { templatePresentation.isMultiChoice = true }
    // It should always be multi-choice no matter it is enabled or not.
    multiChoiceAction.update(TestActionEvent(presentation))
    assertTrue(Utils.isMultiChoiceGroup(multiChoiceAction))
    enabled = false
    multiChoiceAction.update(TestActionEvent(presentation))
    assertTrue(Utils.isMultiChoiceGroup(multiChoiceAction))
  }
}

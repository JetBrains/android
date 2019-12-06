/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentModificationDelegate
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import com.android.tools.idea.uibuilder.structure.NlVisibilityModelTest.Companion.generateModel
import com.intellij.openapi.actionSystem.AnActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.util.function.Function

class VisibilityPopupButtonsTest {

  @Test
  fun testConstructor() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val model = generateModel(Visibility.VISIBLE, Visibility.VISIBLE)
    val buttons = VisibilityPopupButtons(SdkConstants.ANDROID_URI, model)
    assertEquals(4, buttons.buttons.size)

    var matches = 0
    buttons.buttons.forEach {
      val action = it.action as VisibilityToggleAction
      if (action.visibility == model.androidVisibility) {
        matches++
        assertTrue(action.isSelected(event))
      }
    }
    assertEquals(1, matches)
  }

  @Test
  fun testClicked() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val model = generateModel(Visibility.NONE, Visibility.NONE)
    val buttons = VisibilityPopupButtons(SdkConstants.ANDROID_URI, model)
    val noneAction = buttons.buttons[0].action as VisibilityToggleAction
    val visibleAction = buttons.buttons[1].action as VisibilityToggleAction
    assertEquals(Visibility.VISIBLE, visibleAction.visibility)
    assertFalse(visibleAction.isSelected(event))
    assertTrue(noneAction.isSelected(event))

    visibleAction.setSelected(event, true)

    assertTrue(visibleAction.isSelected(event))
    assertFalse(noneAction.isSelected(event))
  }
}
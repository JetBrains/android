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
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import com.android.tools.idea.uibuilder.structure.NlVisibilityModelTest.Companion.generateModel
import com.intellij.openapi.actionSystem.AnActionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.util.function.BiFunction

class VisibilityToggleActionTest {

  @Test
  fun testDefaultNotSelected() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val model = generateModel(Visibility.NONE, Visibility.NONE)

    val action = VisibilityToggleAction(
        model,
        SdkConstants.TOOLS_URI,
        Visibility.VISIBLE,
        BiFunction<NlVisibilityModel, VisibilityToggleAction, Unit> { _, _ -> })
    assertFalse(action.isSelected(event))
  }

  @Test
  fun testDefaultSelected() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val model = generateModel(Visibility.VISIBLE, Visibility.INVISIBLE)

    val action = VisibilityToggleAction(
      model,
      SdkConstants.ANDROID_URI,
      Visibility.VISIBLE,
      BiFunction<NlVisibilityModel, VisibilityToggleAction, Unit> { _, _ -> })
    assertTrue(action.isSelected(event))
  }

  @Test
  fun testSetSelected() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val model = generateModel(Visibility.VISIBLE, Visibility.INVISIBLE)

    val action = VisibilityToggleAction(
      model,
      SdkConstants.ANDROID_URI,
      Visibility.VISIBLE,
      BiFunction<NlVisibilityModel, VisibilityToggleAction, Unit> { _, _ -> })

    action.setSelected(event, true)
    assertTrue(action.isSelected(event))
  }

  @Test
  fun testUnselect() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val model = generateModel(Visibility.VISIBLE, Visibility.INVISIBLE)

    val action = VisibilityToggleAction(
      model,
      SdkConstants.ANDROID_URI,
      Visibility.VISIBLE,
      BiFunction<NlVisibilityModel, VisibilityToggleAction, Unit> { _, _ -> })

    action.isSelected = false
    assertFalse(action.isSelected(event))
  }
}
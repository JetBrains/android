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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.preview.PreviewViewFilter
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import javax.swing.JTextField
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.mock

class PreviewFilterTextActionTest {

  @Rule @JvmField val rule = AndroidProjectRule.Companion.inMemory()

  @Test
  fun testTriggerFilterWhenTextChanged() {
    val filter = mock<PreviewViewFilter>()

    val action = PreviewFilterTextAction(filter)
    val textField = action.createCustomComponent(Presentation(), ActionPlaces.UNKNOWN) as JTextField

    textField.text = "Hello"
    Mockito.verify(filter, Mockito.times(1))
      .filter(ArgumentMatchers.eq("Hello"), ArgumentMatchers.any(DataContext::class.java))

    textField.text = "World"
    Mockito.verify(filter, Mockito.times(1))
      .filter(ArgumentMatchers.eq("World"), ArgumentMatchers.any(DataContext::class.java))
  }
}

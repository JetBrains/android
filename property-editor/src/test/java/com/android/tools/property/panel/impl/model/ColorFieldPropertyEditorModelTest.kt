/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.panel.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.tools.property.panel.impl.model.util.FakeActionIconButton
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import icons.StudioIcons
import org.junit.Test
import org.mockito.Mockito

class ColorFieldPropertyEditorModelTest {

  private fun createModel(): Pair<ColorFieldPropertyEditorModel, AnAction> {
    val action = Mockito.mock(AnAction::class.java)
    val actionButton = FakeActionIconButton(true, StudioIcons.LayoutEditor.Properties.FAVORITES, action)
    val property = FakePropertyItem(ANDROID_URI, ATTR_TEXT_COLOR, "#330066", null, actionButton)
    val model = ColorFieldPropertyEditorModel(property)
    return Pair(model, action)
  }

  @Test
  fun testDelegates() {
    val (model, action) = createModel()
    assertThat(model.editable).isTrue()
    assertThat(model.leftButtonIcon).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES)
    assertThat(model.buttonAction).isSameAs(action)
  }

  @Test
  fun testColorIconUpdatesWithPropertyChanges() {
    val model = createModel().first
    val property = model.property as FakePropertyItem
    val actionButton = property.colorButton as FakeActionIconButton
    assertThat(model.leftButtonIcon).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES)
    actionButton.actionIcon = StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LEFT
    assertThat(model.leftButtonIcon).isEqualTo(StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LEFT)
  }
}

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
package com.android.tools.idea.common.property2.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.tools.idea.common.property2.api.ActionIconButton
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import icons.StudioIcons
import org.junit.Test
import org.mockito.Mockito

class ColorFieldPropertyEditorModelTest {

  private fun createModel(): Pair<ColorFieldPropertyEditorModel, AnAction> {
    val action = Mockito.mock(AnAction::class.java)
    val actionButton = object: ActionIconButton {
      override val actionButtonFocusable = true
      override fun getActionIcon(focused: Boolean) = StudioIcons.LayoutEditor.Properties.FAVORITES
      override val action = action
    }
    val property = PropertyModelTestUtil.makeProperty(ANDROID_URI, ATTR_TEXT_COLOR, "#330066", null, actionButton)
    val model = ColorFieldPropertyEditorModel(property)
    return Pair(model, action)
  }

  @Test
  fun testDelegates() {
    val (model, action) = createModel()
    assertThat(model.editable).isTrue()
    assertThat(model.getDrawableIcon(true)).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES)
    assertThat(model.colorAction).isSameAs(action)
  }
}

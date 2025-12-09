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
package com.android.tools.idea.layoutinspector.model

import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.FQCN_BUTTON
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.compose
import com.android.tools.idea.layoutinspector.view
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons
import org.junit.Test

class SelectedViewModelTest {

  @Test
  fun testButtonWithId() {
    val view =
      view(
        drawId = 10,
        qualifiedName = FQCN_BUTTON,
        viewId = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "button1"),
      )
    val model = SelectedViewModel(view)
    assertThat(model.id).isEqualTo("@id/button1")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.BUTTON)
    assertThat(model.description).isEqualTo(BUTTON)
  }

  @Test
  fun testTextViewWithoutId() {
    val view = view(drawId = 10, qualifiedName = FQCN_TEXT_VIEW)
    val model = SelectedViewModel(view)
    assertThat(model.id).isEqualTo("<unnamed>")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(model.description).isEqualTo(TEXT_VIEW)
  }

  @Test
  fun testDecorView() {
    val view = view(drawId = 10, qualifiedName = "com.android.internal.policy.DecorView")
    val model = SelectedViewModel(view)
    assertThat(model.id).isEqualTo("<unnamed>")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW)
    assertThat(model.description).isEqualTo("DecorView")
  }

  @Test
  fun testCoreText() {
    val view = compose(drawId = -1, name = "CoreText").build()
    val model = SelectedViewModel(view)
    assertThat(model.id).isEqualTo("")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(model.description).isEqualTo("CoreText")
  }
}

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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentModificationDelegate
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import com.intellij.util.ui.EmptyIcon
import icons.StudioIcons.LayoutEditor.Properties
import javax.swing.Icon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class NlVisibilityButtonTest {

  @Test
  fun testUpdatePresentation() {
    val button = NlVisibilityButton()

    assertEquals(EmptyIcon.ICON_16, button.normalIcon)

    val model = generateModel(Visibility.VISIBLE, Visibility.NONE)
    val presentation = ButtonPresentation(model)

    button.update(presentation)
    assertEquals(Properties.VISIBLE, button.normalIcon)
  }

  @Test
  fun testButtonsInGutterAndroidAttr() {
    assertPresentationCreation(
      Visibility.NONE,
      false,
      EmptyIcon.ICON_16,
      null, // Hover icon alpha.
      Properties.VISIBLE
    )
    assertPresentationCreation(
      Visibility.VISIBLE,
      false,
      Properties.VISIBLE,
      Properties.VISIBLE,
      Properties.VISIBLE
    )
    assertPresentationCreation(
      Visibility.INVISIBLE,
      false,
      Properties.INVISIBLE,
      Properties.INVISIBLE,
      Properties.INVISIBLE
    )
    assertPresentationCreation(
      Visibility.GONE,
      false,
      Properties.GONE,
      Properties.GONE,
      Properties.GONE
    )
  }

  private fun assertPresentationCreation(
    visibility: Visibility,
    isToolsAttr: Boolean,
    expectedIcon: Icon,
    expectedHoverIcon: Icon?,
    expectedClickIcon: Icon
  ) {
    val model =
      if (isToolsAttr) generateModel(Visibility.NONE, visibility)
      else generateModel(visibility, Visibility.NONE)
    val presenter = ButtonPresentation(model)

    assertEquals(expectedIcon, presenter.icon)
    if (expectedHoverIcon != null) {
      assertEquals(expectedHoverIcon, presenter.hoverIcon)
    }
    assertEquals(expectedClickIcon, presenter.clickIcon)
  }

  private fun generateModel(android: Visibility, tools: Visibility): NlVisibilityModel {
    val component: NlComponent = Mockito.mock(NlComponent::class.java)
    whenever(component.getAttribute(SdkConstants.ANDROID_URI, "visibility"))
      .thenReturn(Visibility.convert(android))
    whenever(component.getAttribute(SdkConstants.TOOLS_URI, "visibility"))
      .thenReturn(Visibility.convert(tools))
    val delegate = Mockito.mock(NlComponentModificationDelegate::class.java)
    whenever(component.componentModificationDelegate).thenReturn(delegate)
    return NlVisibilityModel(component)
  }
}
